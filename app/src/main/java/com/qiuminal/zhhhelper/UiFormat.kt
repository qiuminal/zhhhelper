package com.qiuminal.zhhhelper

import android.content.Context

/** 拼音展示：无/空白拼音时显示「无」，否则去首尾空白后括起展示。 */
internal fun formatPinyin(pinyin: String?): String {
    val trimmed = pinyin?.trim().orEmpty()
    return if (trimmed.isEmpty()) "无" else "($trimmed)"
}

/** U 码展示：〔〕只括码点字段再与区块拼接，如「基本 〔U+7684〕」。 */
internal fun formatUnicode(block: String?, code: String?): String {
    val b = block.orEmpty()
    val c = code.orEmpty()
    return when {
        b.isEmpty() && c.isEmpty() -> "无"
        b.isEmpty() -> "〔$c〕"
        c.isEmpty() -> b
        else -> "$b 〔$c〕"
    }
}

/** dp → px 快捷换算。 */
internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
