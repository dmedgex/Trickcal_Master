package com.trickcal.crayon.master

import com.trickcal.crayon.master.data.Attr
import com.trickcal.crayon.master.data.BpCharacter
import com.trickcal.crayon.master.data.Character
import com.trickcal.crayon.master.data.GameData
import com.trickcal.crayon.master.data.Layer
import com.trickcal.crayon.master.data.PdTask
import com.trickcal.crayon.master.data.Personality
import com.trickcal.crayon.master.data.Pet
import com.trickcal.crayon.master.data.PetSkill
import com.trickcal.crayon.master.data.Region
import com.trickcal.crayon.master.domain.BattlePowerEngine
import com.trickcal.crayon.master.domain.BoardEngine
import com.trickcal.crayon.master.domain.BpForm
import com.trickcal.crayon.master.domain.CharacterFilters
import com.trickcal.crayon.master.domain.PetDispatchSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePowerEngineTest {

    private val form = BpForm()

    /** 基准值由 demo/core.js 在 V8 下按同一份 battlepower.json 计算得出。 */
    @Test
    fun matchesDemoBaseline_uid10001() {
        val ch = BpCharacter(
            uid = 10001,
            name = "达雅",
            resourceName = "Daya",
            attackType = "魔法",
            nameKey = "",
            activeSkillValueA = 0.0199999995529652,
            ultimateSkillValueA = 0.0199999995529652,
            passiveValueA = 0.0199999995529652,
            weightValueA = 0.349999994039536,
            asideValueA = 0.0,
        )
        val r = BattlePowerEngine.calc(ch, form)
        assertEquals(4262.0, r.mainAttack, 0.0)
        assertEquals(114283.0, r.defenseSum, 0.0)
        assertEquals(0.0, r.asideBonus, 0.0)
        assertEquals(195230.09999999998, r.baseScore, 1e-6)
        assertEquals(1.8699999824166311, r.growth, 1e-9)
        assertEquals(365080.0, r.power, 0.0)
    }

    @Test
    fun matchesDemoBaseline_uid10014() {
        val ch = BpCharacter(
            uid = 10014,
            name = "测试",
            resourceName = "",
            attackType = "物理",
            nameKey = "",
            activeSkillValueA = 0.0199999995529652,
            ultimateSkillValueA = 0.0199999995529652,
            passiveValueA = 0.0199999995529652,
            weightValueA = 0.524999976158142,
            asideValueA = 0.699999988079071,
        )
        val r = BattlePowerEngine.calc(ch, form)
        assertEquals(53080.0, r.mainAttack, 0.0)
        assertEquals(0.699999988079071, r.asideBonus, 1e-12)
        assertEquals(297747.89999999997, r.baseScore, 1e-6)
        assertEquals(2.7449999526143083, r.growth, 1e-9)
        assertEquals(817317.0, r.power, 0.0)
    }

    @Test
    fun asideGradeBelowTwoDisablesAsideBonus() {
        val ch = BpCharacter(
            uid = 1, name = "", resourceName = "", attackType = "物理", nameKey = "",
            activeSkillValueA = 0.02, ultimateSkillValueA = 0.02, passiveValueA = 0.02,
            weightValueA = 0.35, asideValueA = 0.7,
        )
        val low = BattlePowerEngine.calc(ch, form.copy(asideGrade = 1.0))
        val high = BattlePowerEngine.calc(ch, form.copy(asideGrade = 2.0))
        assertEquals(0.0, low.asideBonus, 0.0)
        assertEquals(0.7, high.asideBonus, 1e-9)
        assertTrue(high.power > low.power)
    }
}

class BoardEngineTest {

    private fun character(
        id: String,
        tier1: List<String>,
        tier2: List<String> = listOf("AAA", "A.."),
    ): Character = Character(
        id = id,
        name = id,
        avatarKey = "avatar_$id",
        personality = Personality.ICE,
        sortOrder = 0,
        layers = listOf(
            Layer(charId = id, tier = 1, rows = tier1, width = tier1.maxOf { it.length }),
            Layer(charId = id, tier = 2, rows = tier2, width = tier2.maxOf { it.length }),
        ),
    )

    @Test
    fun slotIdIsStableAndUniquePerCharacter() {
        val a = character("a", listOf("A.K"))
        val b = character("b", listOf("A.K"))
        val idsA = a.layers.first().slots.mapNotNull { c -> c.slotId }
        val idsB = b.layers.first().slots.mapNotNull { c -> c.slotId }
        assertEquals(listOf("a_1_attack_0-0", "a_1_crit_0-2"), idsA)
        assertTrue(idsA.intersect(idsB.toSet()).isEmpty())
    }

    @Test
    fun quickPaintTargetRequiresSingleTierAndSingleAttr() {
        val ch = character("a", listOf("A.A", "K.."))
        assertNull(BoardEngine.quickPaintTarget(ch, emptySet(), setOf(Attr.ATTACK), emptySet()))
        assertNull(BoardEngine.quickPaintTarget(ch, setOf(1, 2), setOf(Attr.ATTACK), emptySet()))
        assertNull(BoardEngine.quickPaintTarget(ch, setOf(1), setOf(Attr.ATTACK, Attr.CRIT), emptySet()))
        // 该层没有该属性 → 无目标
        assertNull(BoardEngine.quickPaintTarget(ch, setOf(1), setOf(Attr.DEFENSE), emptySet()))

        val target = BoardEngine.quickPaintTarget(ch, setOf(1), setOf(Attr.ATTACK), emptySet())
        assertEquals(listOf("a_1_attack_0-0", "a_1_attack_0-2"), target?.slotIds)
        assertTrue(target!!.shouldUnlock)
    }

    @Test
    fun dimDisappearsAfterAnySlotPainted() {
        val ch = character("a", listOf("A.A", "K.."))
        assertTrue(BoardEngine.isDimmed(ch, setOf(1), setOf(Attr.ATTACK), emptySet()))
        assertFalse(BoardEngine.isDimmed(ch, setOf(1), setOf(Attr.ATTACK), setOf("a_1_attack_0-0")))
    }

    @Test
    fun applyQuickPaintTogglesWholeGroup() {
        val ch = character("a", listOf("A.A", "K.."))
        val lit = mutableSetOf<String>()
        val target = BoardEngine.quickPaintTarget(ch, setOf(1), setOf(Attr.ATTACK), lit)!!
        assertEquals(2, BoardEngine.applyQuickPaint(lit, target))
        assertEquals(2, lit.size)

        val undo = BoardEngine.quickPaintTarget(ch, setOf(1), setOf(Attr.ATTACK), lit)!!
        assertFalse(undo.shouldUnlock)
        assertEquals(2, BoardEngine.applyQuickPaint(lit, undo))
        assertTrue(lit.isEmpty())
    }

    @Test
    fun filterUsesSameLayerIntersection() {
        // ch1 第 1 层有攻击；ch2 只有第 2 层有攻击
        val ch1 = character("c1", tier1 = listOf("A.."))
        val ch2 = character("c2", tier1 = listOf("K.."))
        val data = GameData(
            characters = listOf(ch1, ch2),
            pets = emptyList(),
            regions = emptyList(),
            bpCharacters = emptyList(),
            defaultBpUid = 0,
        )

        val strict = BoardEngine.filterCharacters(
            data,
            CharacterFilters(tiers = setOf(1), attrs = setOf(Attr.ATTACK)),
        )
        assertEquals(listOf("c1"), strict.map { it.id })

        val anyTier = BoardEngine.filterCharacters(
            data,
            CharacterFilters(attrs = setOf(Attr.ATTACK)),
        )
        assertEquals(listOf("c1", "c2"), anyTier.map { it.id })

        val bothAttrs = BoardEngine.filterCharacters(
            data,
            CharacterFilters(tiers = setOf(1), attrs = setOf(Attr.ATTACK, Attr.CRIT)),
        )
        assertTrue(bothAttrs.isEmpty())
    }

    @Test
    fun compactSortPutsDimmedFirstAndKeepsOrder() {
        // a 待涂（全未涂）、b 已涂一格、c 待涂
        val a = character("a", listOf("A.."))
        val b = character("b", listOf("A.."))
        val c = character("c", listOf("A.."))
        val lit = setOf("b_1_attack_0-0")
        val tiers = setOf(1)
        val attrs = setOf(Attr.ATTACK)

        assertTrue(BoardEngine.isDimmed(a, tiers, attrs, lit))
        assertFalse(BoardEngine.isDimmed(b, tiers, attrs, lit))

        val sorted = BoardEngine.sortForCompactDisplay(listOf(b, a, c), tiers, attrs, lit)
        // 灰的在前且保持相对顺序：[a, c] + [b]
        assertEquals(listOf("a", "c", "b"), sorted.map { it.id })
    }

    @Test
    fun globalStatsAggregatesBonusAndGold() {        val ch = character("a", listOf("A.A", "K.."))
        val data = GameData(listOf(ch), emptyList(), emptyList(), emptyList(), 0)
        val all = data.allSlots.mapNotNull { it.slotId }
        val tier1Count = ch.layers[0].slots.size // 3
        val tier2Count = ch.layers[1].slots.size // 4
        val stats = BoardEngine.globalStats(data, all.toSet())
        assertEquals(all.size, stats.total)
        assertEquals(all.size, stats.lit)
        // 第一层每格 +3%/2 根，第二层每格 +4%/4 根
        assertEquals(tier1Count * 3 + tier2Count * 4, stats.bonus)
        assertEquals(tier1Count * 2 + tier2Count * 4, stats.gold)
    }
}

class PetDispatchSolverTest {

    private fun pet(id: Int, name: String, base: Int, skills: List<Pair<String, Int>>) = Pet(
        id = id,
        name = name,
        rarity = "COMMON",
        baseScore = base,
        skills = skills.map { PetSkill(it.first, "C", it.second) },
        imageAssetName = "$id.png",
    )

    @Test
    fun picksTeamThatReachesSpecialTier() {
        val pets = listOf(
            pet(1, "甲", 2, listOf("亲密" to 14, "体贴" to 14)),
            pet(2, "乙", 2, listOf("亲密" to 14)),
            pet(3, "丙", 2, listOf("亲密" to 14)),
        )
        val region = Region(
            name = "测试区",
            tasks = listOf(
                PdTask(0, "A", "任务一", listOf("亲密")),
                PdTask(1, "B", "任务二", listOf("亲密")),
            ),
        )
        val outcome = PetDispatchSolver.solve(region, 2, pets)
        assertTrue(outcome.message, outcome.ok)
        val sol = outcome.solution!!
        assertEquals(2, sol.assignments.size)
        // 三只宠各 14 分，任取两只组队即 28 分 → 一阶及以上
        assertTrue(sol.totalScore >= 28)
        assertTrue(sol.summary.special + sol.summary.first > 0)
    }

    @Test
    fun failsWhenNoPetsSelected() {
        val region = Region("r", listOf(PdTask(0, "a", "t", emptyList())))
        val outcome = PetDispatchSolver.solve(region, 1, emptyList())
        assertFalse(outcome.ok)
    }

    @Test
    fun failsWhenTaskCountExceedsTasks() {
        val region = Region("r", listOf(PdTask(0, "a", "t", emptyList())))
        val outcome = PetDispatchSolver.solve(region, 2, listOf(pet(1, "x", 2, emptyList())))
        assertFalse(outcome.ok)
    }

    @Test
    fun fallsBackToBaseScoreWhenNoSkillMatches() {
        val p = pet(9, "丁", 7, listOf("亲密" to 30))
        val task = PdTask(0, "a", "t", listOf("活泼"))
        assertEquals(7, PetDispatchSolver.petScoreForTask(p, task))
    }
}
