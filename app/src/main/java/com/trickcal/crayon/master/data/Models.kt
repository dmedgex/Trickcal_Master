package com.trickcal.crayon.master.data

/** 属性类型，对应旧版 AttributeType 与 demo 的 ATTR。 */
enum class Attr(
    val label: String,
    val short: String,
    val argb: Long,
) {
    ATTACK("攻击力", "攻", 0xFFE8735A),
    CRIT("暴击", "暴", 0xFFF0A64E),
    HEALTH("生命值", "生", 0xFF5EAE72),
    DEFENSE("防御力", "防", 0xFF5B85D6),
    CRIT_RESIST("暴击抗性", "抗", 0xFF9B72D4);

    val imgName: String get() = "cell_" + name.lowercase()

    companion object {
        val ORDER = listOf(ATTACK, CRIT, HEALTH, DEFENSE, CRIT_RESIST)

        /** 大写字母 = 可涂属性格 */
        fun fromUpper(c: Char): Attr? = when (c) {
            'A' -> ATTACK
            'K' -> CRIT
            'H' -> HEALTH
            'D' -> DEFENSE
            'R' -> CRIT_RESIST
            else -> null
        }

        /** 小写字母 = 紫色格（不可涂） */
        fun fromLower(c: Char): Attr? = when (c) {
            'a' -> ATTACK
            'k' -> CRIT
            'h' -> HEALTH
            'd' -> DEFENSE
            'r' -> CRIT_RESIST
            else -> null
        }

        fun fromKey(key: String): Attr? = ORDER.firstOrNull { it.name == key }
    }
}

/** 性格（光暗冰火草） */
enum class Personality(
    val label: String,
    val argb: Long,
    val bgArgb: Long,
    val imgName: String,
) {
    LIGHT("光", 0xFFE9B44C, 0xFFFDF4DE, "light"),
    DARK("暗", 0xFF7C6BA8, 0xFFEEEAF7, "dark"),
    ICE("冰", 0xFF5B9BD5, 0xFFE6F0FA, "ice"),
    FIRE("火", 0xFFE0673F, 0xFFFBEAE3, "fire"),
    GRASS("草", 0xFF5FA87F, 0xFFE5F3EA, "grass");

    companion object {
        val ORDER = listOf(LIGHT, DARK, ICE, FIRE, GRASS)
        fun fromKey(key: String?): Personality = entries.firstOrNull { it.name == key } ?: LIGHT
    }
}

/** 棋盘层：每层 3 个，bonus/cost 为单格加成与金蜡笔消耗。 */
data class Tier(val n: Int, val key: String, val label: String, val bonus: Int, val cost: Int) {
    companion object {
        val ALL = listOf(
            Tier(1, "FIRST", "第 1 层", 3, 2),
            Tier(2, "SECOND", "第 2 层", 4, 4),
            Tier(3, "THIRD", "第 3 层", 5, 6),
        )

        fun byN(n: Int): Tier = ALL.firstOrNull { it.n == n } ?: ALL.first()
    }
}

/** 单个棋盘格。slotId 仅在可涂属性格上非空。 */
data class Cell(
    val row: Int,
    val col: Int,
    val tier: Int,
    val sym: Char,
    val attr: Attr?,
    val purple: Attr?,
    val slotId: String?,
) {
    val paintable: Boolean get() = attr != null && slotId != null
}

/** 一层棋盘。 */
class Layer(
    val charId: String,
    val tier: Int,
    val rows: List<String>,
    val width: Int,
) {
    val cells: List<Cell> = buildList {
        rows.forEachIndexed { ri, row ->
            for (ci in 0 until width) {
                val sym = row.getOrNull(ci) ?: '.'
                add(
                    Cell(
                        row = ri,
                        col = ci,
                        tier = tier,
                        sym = sym,
                        attr = Attr.fromUpper(sym),
                        purple = Attr.fromLower(sym),
                        slotId = null,
                    ),
                )
            }
        }
    }.map { c ->
        val attr = c.attr
        if (attr == null) c else c.copy(slotId = buildSlotId(c, attr))
    }

    /** 与 demo / 旧版一致的稳定 ID：角色_层_属性_行-列 */
    private fun buildSlotId(c: Cell, attr: Attr): String =
        "${charId}_${tier}_${attr.name.lowercase()}_${c.row}-${c.col}"

    private val index: Map<Int, Cell> = cells.associateBy { it.row * 1000 + it.col }

    val slots: List<Cell> get() = cells.filter { it.paintable }

    fun cellAt(row: Int, col: Int): Cell? = index[row * 1000 + col]
}

/** 使徒。slotId 需要全局唯一，构造时由 Character 注入角色 id。 */
data class Character(
    val id: String,
    val name: String,
    val avatarKey: String,
    val personality: Personality,
    val sortOrder: Int,
    val layers: List<Layer>,
) {
    fun layerOf(tier: Int): Layer? = layers.firstOrNull { it.tier == tier }
}

data class PetSkill(val name: String, val level: String, val score: Int)

data class Pet(
    val id: Int,
    val name: String,
    val rarity: String,
    val baseScore: Int,
    val skills: List<PetSkill>,
    val imageAssetName: String,
) {
    val rarityArgb: Long
        get() = when (rarity) {
            "COMMON" -> 0xFF9A9A9A
            "ADVANCED" -> 0xFF5B9BD5
            "RARE" -> 0xFF9B72D4
            "LEGENDARY" -> 0xFFE0B15C
            else -> 0xFF9A9A9A
        }

    val rarityLabel: String
        get() = when (rarity) {
            "COMMON" -> "普通"
            "ADVANCED" -> "高级"
            "RARE" -> "稀有"
            "LEGENDARY" -> "传说"
            else -> "普通"
        }
}

data class PdTask(
    val id: Int,
    val area: String,
    val task: String,
    val bonusSkills: List<String>,
)

data class Region(val name: String, val tasks: List<PdTask>)

data class BpCharacter(
    val uid: Int,
    val name: String,
    val resourceName: String,
    val attackType: String,
    val nameKey: String,
    val activeSkillValueA: Double,
    val ultimateSkillValueA: Double,
    val passiveValueA: Double,
    val weightValueA: Double,
    val asideValueA: Double,
)

/** 计算类结果档位 */
enum class RewardTier(val label: String, val colorArgb: Long, val bgArgb: Long) {
    SPECIAL("特阶", 0xFFC89A3F, 0xFFFCF3DF),
    FIRST("一阶", 0xFF9B72D4, 0xFFF1EAFB),
    SECOND("二阶", 0xFF5B85D6, 0xFFE9EFFB),
    THIRD("三阶", 0xFF5FA87F, 0xFFE7F4EC),
    FOURTH("四阶", 0xFF9A9A9A, 0xFFF0F0F0),
    NONE("无", 0xFFB0A69E, 0xFFF5F1ED),
}
