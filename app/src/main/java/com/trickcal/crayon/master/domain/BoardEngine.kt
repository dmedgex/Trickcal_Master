package com.trickcal.crayon.master.domain

import com.trickcal.crayon.master.data.Attr
import com.trickcal.crayon.master.data.Character
import com.trickcal.crayon.master.data.GameData
import com.trickcal.crayon.master.data.Personality
import com.trickcal.crayon.master.data.Tier

/** 列表筛选条件。层与属性为「同层交集」语义（对齐旧版 CharacterFilterMatcher）。 */
data class CharacterFilters(
    val query: String = "",
    val tiers: Set<Int> = emptySet(),
    val attrs: Set<Attr> = emptySet(),
    val personality: Personality? = null,
) {
    val activeCount: Int
        get() = tiers.size + attrs.size + if (personality != null) 1 else 0
}

data class CharProgress(val total: Int, val lit: Int) {
    val pct: Float get() = if (total == 0) 0f else lit.toFloat() / total
}

data class TierStat(val tier: Int, val total: Int, val lit: Int)

data class AttrStat(val lit: Int = 0, val total: Int = 0, val bonus: Int = 0, val gold: Int = 0)

data class GlobalStats(
    val total: Int,
    val lit: Int,
    val bonus: Int,
    val gold: Int,
    val fullBonus: Int,
    val byAttr: Map<Attr, AttrStat>,
    val byTier: Map<Int, AttrStat>,
    val byTierAttr: Map<Int, Map<Attr, AttrStat>>,
) {
    val pct: Float get() = if (total == 0) 0f else lit.toFloat() / total
}

/** 快捷涂色目标：只在一层 + 一个属性被选中时成立。 */
data class QuickPaintTarget(
    val slotIds: List<String>,
    val shouldUnlock: Boolean,
)

object BoardEngine {

    fun charProgress(ch: Character, lit: Set<String>): CharProgress {
        var total = 0
        var n = 0
        for (layer in ch.layers) {
            for (cell in layer.slots) {
                total++
                val id = cell.slotId
                if (id != null && lit.contains(id)) n++
            }
        }
        return CharProgress(total, n)
    }

    fun tierStats(ch: Character, lit: Set<String>): List<TierStat> = ch.layers.map { layer ->
        var n = 0
        for (cell in layer.slots) {
            val id = cell.slotId
            if (id != null && lit.contains(id)) n++
        }
        TierStat(layer.tier, layer.slots.size, n)
    }

    fun globalStats(data: GameData, lit: Set<String>): GlobalStats {
        val attrTotal = IntArray(Attr.ORDER.size)
        val attrLit = IntArray(Attr.ORDER.size)
        val attrBonus = IntArray(Attr.ORDER.size)
        val attrGold = IntArray(Attr.ORDER.size)

        val tierTotal = IntArray(Tier.ALL.size)
        val tierLit = IntArray(Tier.ALL.size)
        val tierBonus = IntArray(Tier.ALL.size)
        val tierGold = IntArray(Tier.ALL.size)

        val taLit = Array(Tier.ALL.size) { IntArray(Attr.ORDER.size) }
        val taTotal = Array(Tier.ALL.size) { IntArray(Attr.ORDER.size) }

        var total = 0
        var litCount = 0
        var bonus = 0
        var gold = 0
        var fullBonus = 0

        for (cell in data.allSlots) {
            val attr = cell.attr ?: continue
            val tier = Tier.byN(cell.tier)
            val ai = attr.ordinal
            val ti = (cell.tier - 1).coerceIn(0, Tier.ALL.size - 1)

            total++
            fullBonus += tier.bonus
            attrTotal[ai]++
            tierTotal[ti]++
            taTotal[ti][ai]++

            val id = cell.slotId ?: continue
            if (!lit.contains(id)) continue
            litCount++
            bonus += tier.bonus
            gold += tier.cost
            attrLit[ai]++
            attrBonus[ai] += tier.bonus
            attrGold[ai] += tier.cost
            tierLit[ti]++
            tierBonus[ti] += tier.bonus
            tierGold[ti] += tier.cost
            taLit[ti][ai]++
        }

        val byAttr = Attr.ORDER.mapIndexed { i, a ->
            a to AttrStat(attrLit[i], attrTotal[i], attrBonus[i], attrGold[i])
        }.toMap()

        val byTier = Tier.ALL.mapIndexed { i, t ->
            t.n to AttrStat(tierLit[i], tierTotal[i], tierBonus[i], tierGold[i])
        }.toMap()

        val byTierAttr = Tier.ALL.mapIndexed { ti, t ->
            t.n to Attr.ORDER.mapIndexed { ai, a ->
                a to AttrStat(taLit[ti][ai], taTotal[ti][ai])
            }.toMap()
        }.toMap()

        return GlobalStats(
            total = total,
            lit = litCount,
            bonus = bonus,
            gold = gold,
            fullBonus = fullBonus,
            byAttr = byAttr,
            byTier = byTier,
            byTierAttr = byTierAttr,
        )
    }

    /** 旧版 resolveQuickPaintTarget：单层 + 单属性才成立；整组未涂才 shouldUnlock。 */
    fun quickPaintTarget(
        ch: Character,
        tiers: Set<Int>,
        attrs: Set<Attr>,
        lit: Set<String>,
    ): QuickPaintTarget? {
        if (tiers.size != 1 || attrs.size != 1) return null
        val tier = tiers.first()
        val attr = attrs.first()
        val layer = ch.layerOf(tier) ?: return null
        val ids = layer.slots.mapNotNull { cell -> cell.slotId?.takeIf { cell.attr == attr } }
        if (ids.isEmpty()) return null
        return QuickPaintTarget(ids, ids.none { lit.contains(it) })
    }

    /** 旧版 shouldDimCompactCard：有可涂目标且整组未涂 → 灰。 */
    fun isDimmed(
        ch: Character,
        tiers: Set<Int>,
        attrs: Set<Attr>,
        lit: Set<String>,
    ): Boolean = quickPaintTarget(ch, tiers, attrs, lit)?.shouldUnlock == true

    /** 旧版 matchesAnyTierAnyAttribute：候选层内必须存在一层同时包含全部选中属性。 */
    fun filterCharacters(data: GameData, f: CharacterFilters): List<Character> {        val q = f.query.trim()
        return data.characters.filter { ch ->
            if (q.isNotEmpty() && !ch.name.contains(q, true) && !ch.id.contains(q, true)) {
                return@filter false
            }
            val pers = f.personality
            if (pers != null && ch.personality != pers) return@filter false

            val candidates = if (f.tiers.isEmpty()) {
                ch.layers
            } else {
                ch.layers.filter { f.tiers.contains(it.tier) }
            }
            if (candidates.isEmpty()) return@filter false
            if (f.attrs.isEmpty()) return@filter candidates.any { it.slots.isNotEmpty() }

            candidates.any { layer ->
                f.attrs.all { a -> layer.slots.any { it.attr == a } }
            }
        }
    }

    /** 旧版 sortedForDisplay：仅极简模式生效，把待涂（灰）的稳定前置。 */
    fun sortForCompactDisplay(
        list: List<Character>,
        tiers: Set<Int>,
        attrs: Set<Attr>,
        lit: Set<String>,
    ): List<Character> {
        val (dimmed, normal) = list.partition { isDimmed(it, tiers, attrs, lit) }
        return dimmed + normal
    }

    /** 应用一组快捷涂色（整组涂亮 / 整组取消）。 */
    fun applyQuickPaint(lit: MutableSet<String>, target: QuickPaintTarget): Int {
        return if (target.shouldUnlock) {
            var added = 0
            target.slotIds.forEach { if (lit.add(it)) added++ }
            added
        } else {
            var removed = 0
            target.slotIds.forEach { if (lit.remove(it)) removed++ }
            removed
        }
    }
}
