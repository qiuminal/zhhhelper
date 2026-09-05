package com.qiuminal.zhhhelper

/**
 * 练单统计的纯逻辑（无 Android 依赖，便于 JVM 单元测试）。
 *
 * 结算口径说明：
 *  - 击键（键/秒）只统计第一个字之后的剩余字，首字仅作计时起点；
 *  - 速度（字/分）首字计入正确字数，其用时按剩余字平均击键速度推算，
 *    即 速度 = 总正确字数 × 剩余键数 / (剩余用时 × (剩余键数 + 首字键数)) × 60，
 *    避免「首字打对、其余全错」时速度被算成 0；
 *  - 键准（键级）全组（含首字）：(应键数 - 错键数) / 应键数；
 *  - 无法测算（整组单批上屏、剩余用时≈0 或剩余键数为 0）且允许 ∞ 时返回 ∞。
 */

/** 本组键准（0~100）。应键数 ≤0 视为无错字返回 100。 */
internal fun keyAccuracyPct(expectedKeys: Int, errorKeys: Int): Double {
    if (expectedKeys <= 0) return 100.0
    val correct = (expectedKeys - errorKeys).coerceAtLeast(0)
    return correct * 100.0 / expectedKeys
}

/** 结算/实时展示用 (击键键/秒, 速度字/分, 是否∞)。详见文件头注释。 */
internal fun computeKpsAndSpeed(
    correctCount: Int,
    restKeys: Int,
    restElapsedMs: Long,
    firstCharKeys: Int,
    allowInfinite: Boolean,
    singleBatch: Boolean = false,
): Triple<Double, Double, Boolean> {
    val keys = restKeys
    val elapsed = restElapsedMs
    if (allowInfinite && (singleBatch || elapsed <= 0 || keys <= 0)) {
        return Triple(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true)
    }
    val sec = elapsed.coerceAtLeast(1L) / 1000.0
    val kps = keys / sec
    val speed = if (keys > 0) {
        correctCount * keys.toDouble() / (sec * (keys + firstCharKeys)) * 60.0
    } else {
        0.0
    }
    return Triple(kps, speed, false)
}

/** Levenshtein 编辑距离：两个编码串逐字母比对的差异数。 */
internal fun levenshteinDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        val tmp = prev
        prev = curr
        curr = tmp
    }
    return prev[b.length]
}
