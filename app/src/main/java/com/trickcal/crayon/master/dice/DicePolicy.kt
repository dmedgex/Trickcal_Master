package com.trickcal.crayon.master.dice

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * 计分板上的八个槽位。
 *
 * [solver] 是该槽位在精确策略表里的列号，顺序与游戏计分板自上而下完全一致：
 * 二点、三点、四点、五点、六点、葫芦、大顺、快艇。
 *
 * 不用「Int 与 String 混用」的旧写法（脚本侧是 {2:0, 3:1, ..., "fh":5}），
 * 枚举才能保证查表、位掩码、界面显示三处不会各写一份映射。
 */
enum class DiceCategory(val solver: Int, val label: String) {
    TWOS(0, "二点"),
    THREES(1, "三点"),
    FOURS(2, "四点"),
    FIVES(3, "五点"),
    SIXES(4, "六点"),
    FULL_HOUSE(5, "葫芦"),
    LARGE_STRAIGHT(6, "大顺"),
    YACHT(7, "快艇"),
    ;

    /** 二点至六点这五格计入「上级总和」的 55 分奖励 */
    val isUpper: Boolean get() = solver < 5

    companion object {
        fun ofSolver(index: Int): DiceCategory? = entries.firstOrNull { it.solver == index }
    }
}

/** 一颗骰子。[value] 为 null 表示绿骰掷出 1 点，按万能处理。 */
data class Die(val isOrange: Boolean, val value: Int?)

/** 策略给出的建议。 */
sealed interface Advice {
    /** 立即登记该槽位，得分 [points] */
    data class Score(val category: DiceCategory, val points: Int) : Advice

    /**
     * 只保留部分骰子后重投。
     * [keep] 是应保留的橙骰点数（升序展开，可出现重复），[keepGreen] 为 0 表示不保留绿骰，
     * 否则是要保留的绿骰点数（1 即万能面）。
     */
    data class Keep(val keep: List<Int>, val keepGreen: Int, val rerollCount: Int) : Advice
}

/** 面向本项目的决策结果，骰子下标对应调用方传入的 dice 列表顺序。 */
sealed interface Decision {
    data class Register(val category: DiceCategory, val points: Int, val reason: String) : Decision
    data class Reroll(val keepIndexes: List<Int>, val reason: String) : Decision
}

/**
 * 精确最优策略（查表）。
 *
 * 数据来自 dice_solver 的 DP 求解结果，模型为「4 颗橙骰 + 1 颗绿骰（掷出 1 为万能）」、
 * 8 个槽位各登记一次、上级总和超 55 加 40 分，开局理论最优期望总分 148.88。
 *
 * 原始张量未压缩合计 77MB，无法整表常驻。表按 mask（已登记槽位对应的位集合）切成 256 块
 * 分别 zlib 压缩，一次只解压当前 mask 的块：ACTK 252KB + ACTC 42KB，其余常驻的是
 * 1.5MB 压缩块本身。单次决策是一次数组下标 + 一次查表，耗时在微秒级。
 *
 * 加载后不可变，可跨线程共享；解压块的换入换出加了同步。
 */
class DicePolicy private constructor(
    private val fTable: DoubleArray,
    private val scoreTable: ShortArray,
    private val keepVec: ByteArray,
    private val keepGreen: ByteArray,
    private val vectorIndex: HashMap<Int, Int>,
    private val actkBlobs: Array<ByteArray>,
    private val actcBlobs: Array<ByteArray>,
) {

    companion object {
        const val N_MASK = 256
        const val UPPER_MAX = 57
        const val REROLL_MAX = 3
        const val N_STATE = 756
        const val N_CAT = 8
        const val N_KEEP = 1470
        const val N_VECTOR = 126

        private const val MAGIC = "DICETBL1"
        private const val VERSION = 1
        private const val ACTK_VALUES = UPPER_MAX * REROLL_MAX * N_STATE
        private const val ACTK_BYTES = ACTK_VALUES * 2
        private const val ACTC_BYTES = UPPER_MAX * N_STATE

        fun fromStream(input: InputStream): DicePolicy = fromBytes(input.readBytes())

        /**
         * 解析 tables.bin。布局见 tools/gen_dice_tables.py 顶部注释。
         * 任何一处尺寸不符都直接抛异常：宁可启动失败，也不要带着错位的表去算决策。
         */
        fun fromBytes(bytes: ByteArray): DicePolicy {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(8)
            buf.get(magic)
            require(String(magic, Charsets.US_ASCII) == MAGIC) {
                "策略表格式不匹配: ${String(magic, Charsets.US_ASCII)}"
            }
            val version = buf.int
            require(version == VERSION) { "策略表版本不支持: $version" }
            require(buf.int == N_MASK) { "mask 数量不符" }
            require(buf.int == N_STATE) { "状态数量不符" }
            require(buf.int == N_CAT) { "类别数量不符" }
            require(buf.int == N_KEEP) { "保留方案数量不符" }
            require(buf.int == N_VECTOR) { "骰子向量数量不符" }

            val f = DoubleArray(N_MASK * UPPER_MAX) { buf.double }
            val sc = ShortArray(N_STATE * N_CAT) { buf.short }
            val kV = ByteArray(N_KEEP * 6)
            buf.get(kV)
            val kG = ByteArray(N_KEEP)
            buf.get(kG)
            val vRaw = ByteArray(N_VECTOR * 6)
            buf.get(vRaw)
            val vIdx = ShortArray(N_VECTOR) { buf.short }

            val map = HashMap<Int, Int>(N_VECTOR * 2)
            for (i in 0 until N_VECTOR) {
                var key = 0
                for (d in 0 until 6) key = key or (vRaw[i * 6 + d].toInt() shl (d * 3))
                map[key] = vIdx[i].toInt()
            }

            val actkLens = IntArray(N_MASK) { buf.int }
            val actcLens = IntArray(N_MASK) { buf.int }

            var p = buf.position()
            val actk = Array(N_MASK) { i ->
                val n = actkLens[i]
                require(n > 0 && p + n <= bytes.size) { "ACTK 块 $i 越界" }
                bytes.copyOfRange(p, p + n).also { p += n }
            }
            val actc = Array(N_MASK) { i ->
                val n = actcLens[i]
                require(n > 0 && p + n <= bytes.size) { "ACTC 块 $i 越界" }
                bytes.copyOfRange(p, p + n).also { p += n }
            }
            return DicePolicy(f, sc, kV, kG, map, actk, actc)
        }
    }

    /** 压缩块占用，便于日志里核对是否与 assets 一致 */
    val packedBytes: Int =
        actkBlobs.sumOf { it.size } + actcBlobs.sumOf { it.size } + fTable.size * 8 + scoreTable.size * 2

    private var openMask = -1
    private var openActk: ShortArray? = null
    private var openActc: ByteArray? = null

    /** 开局（8 格全空）的理论最优期望总分，用于自检与界面展示 */
    val openingExpectation: Double get() = fTable[(N_MASK - 1) * UPPER_MAX]

    fun expectation(used: Set<DiceCategory>, upper: Int): Double {
        val mask = maskOf(used)
        return fTable[mask * UPPER_MAX + upper.coerceIn(0, UPPER_MAX - 1)]
    }

    /**
     * 查表给出建议。骰子构成不是 4 橙 + 1 绿时返回 null —— 那是识别出错，
     * 调用方应当暂停而不是拿启发式硬打（打错一个格子比停一秒代价大得多）。
     */
    fun advise(
        used: Set<DiceCategory>,
        upper: Int,
        rerolls: Int,
        orange: IntArray,
        green: Int,
    ): Advice? {
        if (orange.size != 4) return null
        if (orange.any { it !in 1..6 }) return null
        if (green !in 1..6) return null
        val di = indexOf(orange, green)
        if (di < 0) return null

        val mask = maskOf(used)
        val s = upper.coerceIn(0, UPPER_MAX - 1)
        val r = rerolls.coerceIn(0, REROLL_MAX - 1)
        val block = actkOf(mask) ?: return null

        val action = block[(s * REROLL_MAX + r) * N_STATE + di].toInt()
        if (action < 0) {
            val cat = actcOf(mask)?.get(s * N_STATE + di)?.toInt() ?: return null
            val category = DiceCategory.ofSolver(cat) ?: return null
            return Advice.Score(category, scoreOf(di, cat))
        }
        if (action >= N_KEEP) return null

        val kept = ArrayList<Int>(4)
        for (face in 0 until 6) {
            val n = keepVec[action * 6 + face].toInt()
            repeat(n) { kept.add(face + 1) }
        }
        val gn = keepGreen[action].toInt()
        val rerollCount = (4 - kept.size) + (if (gn == 0) 1 else 0)
        return Advice.Keep(kept, gn, rerollCount)
    }

    /** 适配本项目：把「保留哪些点数」映射回 dice 列表的下标 */
    fun decide(dice: List<Die>, rollsLeft: Int, used: Set<DiceCategory>, upper: Int): Decision? {
        val oranges = dice.filter { it.isOrange }
        val greens = dice.filter { !it.isOrange }
        if (oranges.size != 4 || greens.size != 1) return null
        if (oranges.any { it.value == null }) return null

        val orangeValues = IntArray(4) { oranges[it].value ?: return null }
        val greenDie = greens[0]
        val greenValue = greenDie.value ?: 1

        return when (val a = advise(used, upper, rollsLeft, orangeValues, greenValue)) {
            null -> null
            is Advice.Score -> Decision.Register(
                a.category, a.points,
                "最优策略: 登记 ${a.category.label}=${a.points}（精确查表）",
            )

            is Advice.Keep -> {
                val need = a.keep.toMutableList()
                val idx = ArrayList<Int>(5)
                dice.forEachIndexed { i, d ->
                    if (d.isOrange && need.isNotEmpty()) {
                        val k = need.indexOf(d.value ?: -1)
                        if (k >= 0) {
                            need.removeAt(k)
                            idx.add(i)
                        }
                    }
                }
                if (a.keepGreen != 0) {
                    val gi = dice.indexOfFirst { !it.isOrange }
                    if (gi >= 0) idx.add(gi)
                }
                val desc = if (idx.isEmpty()) "无" else idx.joinToString("、") { fmt(dice[it]) }
                Decision.Reroll(
                    idx,
                    "最优策略: 保留 [$desc] 重投 ${5 - idx.size} 颗（精确查表）",
                )
            }
        }
    }

    private fun fmt(d: Die): String {
        if (!d.isOrange) return if (d.value == null) "绿万能" else "绿${d.value}"
        return "橙${d.value}"
    }

    private fun maskOf(used: Set<DiceCategory>): Int {
        var mask = 0
        for (c in DiceCategory.entries) if (c !in used) mask = mask or (1 shl c.solver)
        return mask
    }

    private fun scoreOf(di: Int, cat: Int): Int {
        val v = scoreTable[di * N_CAT + cat].toInt()
        return if (v < 0) 0 else v
    }

    /** 橙骰多重集 + 绿骰点数 -> 状态下标；索引由表提供，Kotlin 侧不重实现枚举顺序 */
    private fun indexOf(orange: IntArray, green: Int): Int {
        val cnt = IntArray(6)
        for (v in orange) cnt[v - 1]++
        var key = 0
        for (i in 0 until 6) key = key or (cnt[i] shl (i * 3))
        val vi = vectorIndex[key] ?: return -1
        return vi * 6 + (green - 1)
    }

    @Synchronized
    private fun actkOf(mask: Int): ShortArray? {
        if (openMask == mask) return openActk
        val raw = inflate(actkBlobs[mask], ACTK_BYTES) ?: return null
        val shorts = ShortArray(ACTK_VALUES)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        openMask = mask
        openActk = shorts
        openActc = null
        return shorts
    }

    @Synchronized
    private fun actcOf(mask: Int): ByteArray? {
        if (openMask == mask && openActc != null) return openActc
        // 只换了 ACTC 而 ACTK 仍是同 mask 的缓存：两者必须同时有效
        if (openMask != mask) {
            if (actkOf(mask) == null) return null
        }
        val raw = inflate(actcBlobs[mask], ACTC_BYTES) ?: return null
        openActc = raw
        return raw
    }

    private fun inflate(src: ByteArray, expected: Int): ByteArray? {
        val inf = Inflater()
        try {
            inf.setInput(src)
            val out = ByteArray(expected)
            var off = 0
            while (off < expected && !inf.finished()) {
                val n = inf.inflate(out, off, expected - off)
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) return null
                }
                off += n
            }
            return if (off == expected) out else null
        } catch (_: Throwable) {
            return null
        } finally {
            inf.end()
        }
    }
}
