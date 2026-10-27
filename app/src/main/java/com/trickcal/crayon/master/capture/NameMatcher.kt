package com.trickcal.crayon.master.capture

/**
 * 角色名匹配：OCR 文本 → 名单里的标准名。
 * 静态识图与动态识图必须共用同一份实现，否则两边行为会不一致
 * （曾经各写一份，导致「锡安」在静态能认、动态认不出）。
 */
object NameMatcher {

    fun match(raw: String, knownNames: List<String>): String? {
        val cleaned = raw.replace(Regex("[^\u4e00-\u9fa5A-Za-z0-9]"), "")
        if (cleaned.isEmpty()) return null

        // 单字角色（绫 / 琳）必须完全匹配，避免把某个字的误识别当成角色名
        if (cleaned.length == 1) return knownNames.firstOrNull { it == cleaned }

        knownNames.firstOrNull { it == cleaned }?.let { return it }

        // 只认中文部分再匹配：有的角色名带装饰符（如 ××锡安×× / x乂锡安乂x），
        // 去掉之后按「中文精确 → 中文唯一包含」再试一次。
        val cn = cleaned.filter { it in '\u4e00'..'\u9fa5' }
        if (cn.length >= 2) {
            knownNames.firstOrNull { it == cn }?.let { return it }
            val contained = knownNames.filter { it.contains(cn) }
            if (contained.size == 1) return contained[0]
        }

        var best: String? = null
        var bestD = Int.MAX_VALUE
        knownNames.forEach { name ->
            val d = editDistance(cleaned, name)
            if (d < bestD) {
                bestD = d
                best = name
            }
        }
        val limit = if (cleaned.length <= 2) 1 else 2
        return if (bestD <= limit) best else null
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
