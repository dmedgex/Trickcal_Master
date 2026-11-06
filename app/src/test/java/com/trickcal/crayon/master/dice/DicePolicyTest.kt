package com.trickcal.crayon.master.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 决策层离线验收。
 *
 * 基准数据由 tools/gen_dice_tables.py 从 Python 侧 solver_policy 导出：
 * 300 个随机状态（已登记集合、上级总分、剩余重投、4 橙 + 1 绿），
 * 要求 Kotlin 侧逐条给出完全相同的动作。这是「移植是否等价」的唯一判据。
 */
class DicePolicyTest {

    private fun loadPolicy(): DicePolicy {
        val f = File("src/main/assets/dice/tables.bin")
        assertTrue("缺少策略表: ${f.absolutePath}", f.exists())
        return DicePolicy.fromBytes(f.readBytes())
    }

    private fun loadCases(): List<String> {
        val stream = javaClass.classLoader!!.getResourceAsStream("dice/policy_cases.tsv")
        assertNotNull("缺少对拍基准 dice/policy_cases.tsv", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
    }

    @Test
    fun `表可加载且开局期望值正确`() {
        val policy = loadPolicy()
        // 理论最优期望总分 148.88（8 格全空、上级 0 分）
        assertEquals(148.88, policy.openingExpectation, 1.0)
        assertTrue(policy.packedBytes > 1_000_000)
    }

    @Test
    fun `与 Python 侧 300 个状态逐动作零分歧`() {
        val policy = loadPolicy()
        val cases = loadCases()
        assertEquals("对拍样本数不符", 300, cases.size)

        var mismatch = 0
        val detail = StringBuilder()

        for ((lineNo, line) in cases.withIndex()) {
            val f = line.split("\t")
            require(f.size >= 10) { "第 $lineNo 行字段数不对: ${f.size}" }

            val used = if (f[0].isEmpty()) emptySet()
            else f[0].split(",").mapNotNull { DiceCategory.ofSolver(it.toInt()) }.toSet()
            val upper = f[1].toInt()
            val rerolls = f[2].toInt()
            val orange = f[3].split(",").map { it.toInt() }.toIntArray()
            val green = f[4].toInt()
            val expectAction = f[5]
            val expectCat = f[6].toInt()
            val expectPoints = f[7].toInt()
            val expectKeep = if (f[8].isEmpty()) emptyList() else f[8].split(",").map { it.toInt() }
            val expectKeepGreen = f[9].toInt()

            val got = policy.advise(used, upper, rerolls, orange, green)
            val ok = when {
                got == null -> false
                expectAction == "score" -> got is Advice.Score &&
                    got.category.solver == expectCat && got.points == expectPoints
                else -> got is Advice.Keep &&
                    got.keep == expectKeep && got.keepGreen == expectKeepGreen
            }
            if (!ok) {
                mismatch++
                if (mismatch <= 10) {
                    detail.append("\n#$lineNo used=${f[0]} upper=$upper r=$rerolls 橙=${f[3]} 绿=$green")
                        .append(" 期望=$expectAction/$expectCat/$expectPoints/keep=${f[8]}/g=$expectKeepGreen")
                        .append(" 实际=$got")
                }
            }
        }
        assertEquals("与 Python 侧存在分歧: $mismatch 条$detail", 0, mismatch)
    }

    @Test
    fun `骰子构成不符时不给建议`() {
        val policy = loadPolicy()
        val used = emptySet<DiceCategory>()
        assertNull(policy.advise(used, 0, 2, intArrayOf(1, 2, 3), 4))
        assertNull(policy.advise(used, 0, 2, intArrayOf(1, 2, 3, 7), 4))
        assertNull(policy.advise(used, 0, 2, intArrayOf(1, 2, 3, 4), 0))
    }

    @Test
    fun `decide 的保留下标指向正确的骰子`() {
        val policy = loadPolicy()
        val dice = listOf(
            Die(isOrange = true, value = 2),
            Die(isOrange = true, value = 2),
            Die(isOrange = true, value = 2),
            Die(isOrange = true, value = 6),
            Die(isOrange = false, value = null),
        )
        val d = policy.decide(dice, rollsLeft = 2, used = emptySet(), upper = 0)
        assertNotNull(d)
        when (d) {
            is Decision.Reroll -> {
                // 下标必须落在 0..4 且不重复
                assertEquals(d.keepIndexes.size, d.keepIndexes.toSet().size)
                assertTrue(d.keepIndexes.all { it in dice.indices })
                // 保留下来的骰子点数，必须与查表给的方案一致
                val kept = d.keepIndexes.map { dice[it] }
                val table = policy.advise(
                    emptySet(), 0, 2,
                    intArrayOf(2, 2, 2, 6), 1,
                ) as Advice.Keep
                val keptOrange = kept.filter { it.isOrange }.mapNotNull { it.value }.sorted()
                assertEquals(table.keep.sorted(), keptOrange)
                assertEquals(table.keepGreen != 0, kept.any { !it.isOrange })
            }

            is Decision.Register -> assertTrue(d.points >= 0)
            null -> throw AssertionError("意外返回 null")
        }
    }
}
