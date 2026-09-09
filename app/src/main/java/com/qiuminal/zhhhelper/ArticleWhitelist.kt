package com.qiuminal.zhhhelper

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 文章字符白名单：white.txt 全部字符 ∪ zi.txt 各行主键字符。
 * 构建资产按 Unicode code point 升序存储为大端 Int32，运行时二分判断，避免构造大型 Set。
 */
internal object ArticleWhitelist {
    private const val ASSET_NAME = "article_whitelist.bin"

    @Volatile
    private var codePoints = IntArray(0)

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val bytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
            require(bytes.size % 4 == 0) { "Invalid article whitelist" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            codePoints = IntArray(bytes.size / 4) { buffer.int }
            loaded = true
        }
    }

    fun contains(codePoint: Int): Boolean = loaded && codePoints.binarySearch(codePoint) >= 0

    /** 删除所有不在最终白名单中的 code point。 */
    fun filter(text: String): String {
        check(loaded) { "Article whitelist not loaded" }
        return filterArticleText(text, codePoints)
    }
}

/** 纯逻辑入口：allowedCodePoints 必须升序且不重复。 */
internal fun filterArticleText(text: String, allowedCodePoints: IntArray): String {
    val output = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        if (allowedCodePoints.binarySearch(codePoint) >= 0) output.appendCodePoint(codePoint)
        index += Character.charCount(codePoint)
    }
    return output.toString()
}
