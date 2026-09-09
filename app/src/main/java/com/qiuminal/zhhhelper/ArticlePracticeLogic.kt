package com.qiuminal.zhhhelper

/** 文章跟打的纯逻辑，独立于 Android，便于回归测试。 */
internal object ArticlePracticeLogic {
    const val MAX_UTF16_LENGTH = 8000

    /**
     * 与原 Demo 一致先统一换行和长度；白名单过滤由 ArticleWhitelist 在所有加载路径统一执行。
     * 换行最终不在白名单中，因此不会进入跟打正文。
     */
    fun normalizeText(raw: String): Pair<String, Boolean> {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
            .replace(Regex("\n+$"), "\n")
        val truncated = text.length > MAX_UTF16_LENGTH
        if (truncated) text = text.substring(0, MAX_UTF16_LENGTH)
        return text to truncated
    }

    fun codePoints(text: String): IntArray = text.codePoints().toArray()

    fun stats(reference: IntArray, input: IntArray, elapsedSeconds: Int): ArticleStats {
        val correct = reference.indices.count { it < input.size && reference[it] == input[it] }
        val total = reference.size
        val errors = total - correct
        val accuracy = if (total == 0) 0 else kotlin.math.round(correct * 100.0 / total).toInt()
        // 与练单一致只按正确字符计速；文章白名单内每个 Unicode code point 均算一个字，含标点/字母/数字。
        val speed = correctCharactersPerMinute(correct, elapsedSeconds)
        return ArticleStats(total, elapsedSeconds, speed, accuracy, errors)
    }

    fun correctCount(reference: IntArray, input: IntArray): Int =
        reference.indices.count { it < input.size && reference[it] == input[it] }

    fun correctCharactersPerMinute(correct: Int, elapsedSeconds: Int): Int =
        if (elapsedSeconds > 0) kotlin.math.round(correct * 60.0 / elapsedSeconds).toInt() else 0
}

internal data class ArticleStats(
    val total: Int,
    val elapsedSeconds: Int,
    val speed: Int,
    val accuracy: Int,
    val errors: Int,
)
