package com.trickcal.crayon.master.domain

import com.trickcal.crayon.master.data.PdTask
import com.trickcal.crayon.master.data.Pet
import com.trickcal.crayon.master.data.Region
import com.trickcal.crayon.master.data.RewardTier

/** 队伍中的一只宠物，borrowed=true 表示借用（每日借用上限内）。 */
data class TeamPet(val pet: Pet, val borrowed: Boolean)

/** 结果档位计数，顺序：特/一/二/三/四阶。 */
data class Summary(
    val special: Int = 0,
    val first: Int = 0,
    val second: Int = 0,
    val third: Int = 0,
    val fourth: Int = 0,
) {
    operator fun plus(o: Summary) = Summary(
        special + o.special,
        first + o.first,
        second + o.second,
        third + o.third,
        fourth + o.fourth,
    )

    companion object {
        val ZERO = Summary()

        fun of(tier: RewardTier): Summary = when (tier) {
            RewardTier.SPECIAL -> Summary(special = 1)
            RewardTier.FIRST -> Summary(first = 1)
            RewardTier.SECOND -> Summary(second = 1)
            RewardTier.THIRD -> Summary(third = 1)
            RewardTier.FOURTH -> Summary(fourth = 1)
            RewardTier.NONE -> ZERO
        }
    }
}

/** 分层目标：先比档位分布，再比借用更少、用宠更少，最后比总分更高。 */
data class Objective(
    val summary: Summary,
    val borrowed: Int,
    val pets: Int,
    val score: Int,
)

data class Candidate(
    val team: List<TeamPet>,
    val ownedIds: List<Int>,
    val borrowed: Int,
    val count: Int,
    val score: Int,
    val tier: RewardTier,
)

class TaskEntry(
    val task: PdTask,
    val originalIndex: Int,
    val candidates: List<Candidate>,
    val specialCandidates: List<Candidate>,
    val bestRank: Int,
    val bestTier: Summary,
    val maxScore: Int,
    val minPets: Int,
    val minBorrowed: Int,
) {
    val upper: Objective
        get() = Objective(bestTier, minBorrowed, minPets, maxScore)
}

data class Assignment(
    val task: PdTask,
    val team: List<TeamPet>,
    val score: Int,
    val tier: RewardTier,
)

data class Solution(
    val assignments: List<Assignment>,
    val totalScore: Int,
    val borrowed: Int,
    val totalPets: Int,
    val summary: Summary,
    val allSpecial: Boolean,
    val objective: Objective,
)

data class PdOutcome(
    val ok: Boolean,
    val message: String = "",
    val solution: Solution? = null,
    val elapsedMs: Long = 0,
)

/** 对齐 PetDispatchSolver.kt / core.js 的 solvePetDispatch。 */
object PetDispatchSolver {

    private const val MAX_BORROWED_PER_TASK = 1
    private const val MAX_TOTAL_BORROWED = 3
    private const val CANDIDATE_LIMIT = 160

    private fun rankOf(t: RewardTier): Int = when (t) {
        RewardTier.NONE -> 0
        RewardTier.FOURTH -> 1
        RewardTier.THIRD -> 2
        RewardTier.SECOND -> 3
        RewardTier.FIRST -> 4
        RewardTier.SPECIAL -> 5
    }

    private fun tierFromScore(score: Int): RewardTier = when {
        score > 37 -> RewardTier.SPECIAL
        score >= 25 -> RewardTier.FIRST
        score > 13 -> RewardTier.SECOND
        score > 5 -> RewardTier.THIRD
        score > 1 -> RewardTier.FOURTH
        else -> RewardTier.NONE
    }

    fun petScoreForTask(pet: Pet, task: PdTask): Int {
        val total = pet.skills
            .filter { task.bonusSkills.contains(it.name) }
            .sumOf { it.score }
        return if (total != 0) total else pet.baseScore
    }

    private fun compareObjective(a: Objective, b: Objective): Int {
        val av = intArrayOf(
            a.summary.special, a.summary.first, a.summary.second, a.summary.third, a.summary.fourth,
            -a.borrowed, -a.pets, a.score,
        )
        val bv = intArrayOf(
            b.summary.special, b.summary.first, b.summary.second, b.summary.third, b.summary.fourth,
            -b.borrowed, -b.pets, b.score,
        )
        for (i in av.indices) {
            if (av[i] != bv[i]) return av[i] - bv[i]
        }
        return 0
    }

    private fun combinations(list: List<PdTask>, k: Int): List<List<PdTask>> {
        if (k <= 0 || k > list.size) return emptyList()
        val out = ArrayList<List<PdTask>>()
        val cur = ArrayList<PdTask>(k)
        fun dfs(start: Int) {
            if (cur.size == k) {
                out.add(ArrayList(cur))
                return
            }
            val remaining = k - cur.size
            var i = start
            while (i <= list.size - remaining) {
                cur.add(list[i])
                dfs(i + 1)
                cur.removeAt(cur.size - 1)
                i++
            }
        }
        dfs(0)
        return out
    }

    fun solve(
        region: Region,
        taskCount: Int,
        ownedPets: List<Pet>,
        farmPets: List<Pet> = emptyList(),
    ): PdOutcome {
        val started = System.nanoTime()
        if (ownedPets.isEmpty()) return PdOutcome(false, "请选择至少一只拥有的宠物。")

        val available = buildList {
            ownedPets.forEach { add(TeamPet(it, false)) }
            farmPets.forEach { add(TeamPet(it, true)) }
        }
        val validTasks = region.tasks.filter { it.task.isNotBlank() }
        val combos = combinations(validTasks, taskCount)
        if (combos.isEmpty()) return PdOutcome(false, "该区域任务数不足，请减少任务数量。")

        val scores: Map<Int, Map<Int, Int>> = buildMap {
            for (c in available) {
                if (containsKey(c.pet.id)) continue
                put(c.pet.id, region.tasks.associate { it.id to petScoreForTask(c.pet, it) })
            }
        }

        val prepared = combos.map { combo ->
            val entries = combo.mapIndexed { idx, task -> buildTaskEntry(task, idx, available, scores) }
            var summary = Summary.ZERO
            var minBorrowed = 0
            var minPets = 0
            var maxScore = 0
            var allSpecial = true
            for (e in entries) {
                summary += e.bestTier
                minBorrowed += e.minBorrowed
                minPets += e.minPets
                maxScore += e.maxScore
                if (e.bestRank != rankOf(RewardTier.SPECIAL)) allSpecial = false
            }
            Prepared(entries, allSpecial, Objective(summary, minBorrowed, minPets, maxScore))
        }.sortedWith { a, b ->
            if (a.allSpecial != b.allSpecial) {
                if (b.allSpecial) 1 else -1
            } else {
                compareObjective(b.upper, a.upper)
            }
        }

        var best: Solution? = null
        for (prep in prepared) {
            val current = best
            if (current != null && compareObjective(prep.upper, current.objective) <= 0) continue
            val allSp = searchAllSpecial(prep.entries)
            if (allSp != null) {
                best = allSp
                break
            }
            val cand = searchBest(prep.entries)
            if (cand != null && (best == null || compareObjective(cand.objective, best!!.objective) > 0)) {
                best = cand
            }
        }

        val done = best ?: return PdOutcome(
            false,
            "没有找到有效的派遣方案，请增加宠物或减少任务数。",
            elapsedMs = (System.nanoTime() - started) / 1_000_000,
        )
        return PdOutcome(
            ok = true,
            solution = done,
            elapsedMs = (System.nanoTime() - started) / 1_000_000,
        )
    }

    private data class Prepared(
        val entries: List<TaskEntry>,
        val allSpecial: Boolean,
        val upper: Objective,
    )

    private fun buildTaskEntry(
        task: PdTask,
        originalIndex: Int,
        available: List<TeamPet>,
        scores: Map<Int, Map<Int, Int>>,
    ): TaskEntry {
        val bestBySig = LinkedHashMap<String, Candidate>()

        fun register(team: List<TeamPet>) {
            val borrowed = team.count { it.borrowed }
            if (borrowed > MAX_BORROWED_PER_TASK) return
            if (team.map { it.pet.name }.toSet().size != team.size) return
            val ownedIds = team.filter { !it.borrowed }.map { it.pet.id }
            val score = team.sumOf { scores[it.pet.id]?.get(task.id) ?: 0 }
            val tier = tierFromScore(score)
            val cand = Candidate(team, ownedIds, borrowed, team.size, score, tier)
            val sig = ownedIds.sorted().joinToString(",") + "|" + borrowed + "|" + team.size
            val old = bestBySig[sig]
            if (old == null ||
                rankOf(cand.tier) > rankOf(old.tier) ||
                (rankOf(cand.tier) == rankOf(old.tier) && cand.score > old.score)
            ) {
                bestBySig[sig] = cand
            }
        }

        val n = available.size
        for (i in 0 until n) {
            register(listOf(available[i]))
            for (j in i + 1 until n) {
                register(listOf(available[i], available[j]))
                for (k in j + 1 until n) {
                    register(listOf(available[i], available[j], available[k]))
                }
            }
        }

        val sorted = bestBySig.values.sortedWith(compareByDescending<Candidate> { rankOf(it.tier) }
            .thenBy { it.count }
            .thenBy { it.borrowed }
            .thenByDescending { it.score })
            .take(CANDIDATE_LIMIT)

        val specialCandidates = sorted
            .filter { it.tier == RewardTier.SPECIAL }
            .sortedWith(compareBy<Candidate> { it.count }.thenBy { it.borrowed }.thenByDescending { it.score })

        val bestRank = sorted.maxOfOrNull { rankOf(it.tier) } ?: 0
        val bestTier = when (bestRank) {
            rankOf(RewardTier.SPECIAL) -> RewardTier.SPECIAL
            rankOf(RewardTier.FIRST) -> RewardTier.FIRST
            rankOf(RewardTier.SECOND) -> RewardTier.SECOND
            rankOf(RewardTier.THIRD) -> RewardTier.THIRD
            rankOf(RewardTier.FOURTH) -> RewardTier.FOURTH
            else -> RewardTier.NONE
        }

        return TaskEntry(
            task = task,
            originalIndex = originalIndex,
            candidates = sorted,
            specialCandidates = specialCandidates,
            bestRank = bestRank,
            bestTier = Summary.of(bestTier),
            maxScore = sorted.maxOfOrNull { it.score } ?: 0,
            minPets = sorted.minOfOrNull { it.count } ?: 0,
            minBorrowed = sorted.minOfOrNull { it.borrowed } ?: 0,
        )
    }

    private val searchOrder: Comparator<TaskEntry> =
        compareBy<TaskEntry> { it.specialCandidates.size }
            .thenBy { it.candidates.size }
            .thenByDescending { it.bestRank }
            .thenByDescending { it.maxScore }

    private class Choice(val entry: TaskEntry, val cand: Candidate)

    private fun searchAllSpecial(entries: List<TaskEntry>): Solution? {
        if (entries.any { it.specialCandidates.isEmpty() }) return null
        val ordered = entries.sortedWith(searchOrder)
        val chosen = arrayOfNulls<Choice>(ordered.size)
        val failed = HashSet<String>()

        fun dfs(i: Int, usedOwned: Set<Int>, borrowedTotal: Int): Boolean {
            if (i == ordered.size) return true
            val key = i.toString() + "|" + usedOwned.sorted().joinToString(",") + "|" + borrowedTotal
            if (failed.contains(key)) return false
            val e = ordered[i]
            for (c in e.specialCandidates) {
                if (borrowedTotal + c.borrowed > MAX_TOTAL_BORROWED) continue
                if (c.ownedIds.any { usedOwned.contains(it) }) continue
                chosen[i] = Choice(e, c)
                val next = HashSet(usedOwned)
                next.addAll(c.ownedIds)
                if (dfs(i + 1, next, borrowedTotal + c.borrowed)) return true
            }
            failed.add(key)
            return false
        }

        if (!dfs(0, emptySet(), 0)) return null
        return buildSolution(chosen.filterNotNull(), ordered.size)
    }

    private fun searchBest(entries: List<TaskEntry>): Solution? {
        if (entries.any { it.candidates.isEmpty() }) return null
        val ordered = entries.sortedWith(searchOrder)
        val n = ordered.size

        val suffixSummary = Array(n + 1) { Summary.ZERO }
        val suffixBorrowed = IntArray(n + 1)
        val suffixPets = IntArray(n + 1)
        val suffixScore = IntArray(n + 1)
        for (i in n - 1 downTo 0) {
            suffixSummary[i] = ordered[i].bestTier + suffixSummary[i + 1]
            suffixBorrowed[i] = ordered[i].minBorrowed + suffixBorrowed[i + 1]
            suffixPets[i] = ordered[i].minPets + suffixPets[i + 1]
            suffixScore[i] = ordered[i].maxScore + suffixScore[i + 1]
        }

        val chosen = arrayOfNulls<Choice>(n)
        val cache = HashMap<String, Objective>()
        var best: Solution? = null

        fun dfs(i: Int, usedOwned: Set<Int>, borrowedTotal: Int, summary: Summary, pets: Int, score: Int) {
            val optimistic = Objective(
                summary = summary + suffixSummary[i],
                borrowed = borrowedTotal + suffixBorrowed[i],
                pets = pets + suffixPets[i],
                score = score + suffixScore[i],
            )
            val cur = best
            if (cur != null && compareObjective(optimistic, cur.objective) <= 0) return

            val key = i.toString() + "|" + usedOwned.sorted().joinToString(",") + "|" + borrowedTotal
            val prefix = Objective(summary, 0, pets, score)
            val cached = cache[key]
            if (cached != null && compareObjective(prefix, cached) <= 0) return
            cache[key] = prefix

            if (i == n) {
                val sol = buildSolution(chosen.filterNotNull(), n)
                if (best == null || compareObjective(sol.objective, best!!.objective) > 0) best = sol
                return
            }

            val e = ordered[i]
            for (c in e.candidates) {
                val nb = borrowedTotal + c.borrowed
                if (nb > MAX_TOTAL_BORROWED) continue
                if (c.ownedIds.any { usedOwned.contains(it) }) continue
                chosen[i] = Choice(e, c)
                val next = HashSet(usedOwned)
                next.addAll(c.ownedIds)
                dfs(i + 1, next, nb, summary + Summary.of(c.tier), pets + c.count, score + c.score)
            }
        }

        dfs(0, emptySet(), 0, Summary.ZERO, 0, 0)
        return best
    }

    private fun buildSolution(pairs: List<Choice>, taskCount: Int): Solution {
        val assignments = arrayOfNulls<Assignment>(taskCount)
        var totalScore = 0
        var borrowed = 0
        var totalPets = 0
        var summary = Summary.ZERO
        for (pair in pairs) {
            val entry = pair.entry
            val cand = pair.cand
            assignments[entry.originalIndex] = Assignment(entry.task, cand.team, cand.score, cand.tier)
            totalScore += cand.score
            borrowed += cand.borrowed
            totalPets += cand.count
            summary += Summary.of(cand.tier)
        }
        val list = assignments.filterNotNull()
        return Solution(
            assignments = list,
            totalScore = totalScore,
            borrowed = borrowed,
            totalPets = totalPets,
            summary = summary,
            allSpecial = summary.special == taskCount,
            objective = Objective(summary, borrowed, totalPets, totalScore),
        )
    }
}
