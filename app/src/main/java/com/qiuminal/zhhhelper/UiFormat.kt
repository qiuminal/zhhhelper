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

/**
 * 拆分配对解析：字根码与拆分部件按数量逐组配对。
 *
 * 码表约定（chai.txt）：字根码为若干组「大写+小写」两字母编码（空白分隔）；
 * 拆分部件为等量单码点字符，空格分隔或直接连写（如「⺊一」），
 * 部件可能位于增补平面（如 𠂇 U+20087）或 PUA 区（如 U+E430）。
 * 数量一致时返回 (编码, 部件) 配对列表，否则返回 null（调用方退回两行展示）。
 */
internal fun parseRootPairs(rootCodes: String?, components: String?): List<Pair<String, String>>? {
    val codes = rootCodes?.split(Regex("\\s+")).orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    if (codes.isEmpty()) {
        return null
    }
    val comps = ArrayList<String>(codes.size)
    val compsRaw = components.orEmpty()
    var i = 0
    while (i < compsRaw.length) {
        val cp = compsRaw.codePointAt(i)
        if (!Character.isWhitespace(cp)) {
            comps.add(String(Character.toChars(cp)))
        }
        i += Character.charCount(cp)
    }
    if (comps.size != codes.size) {
        return null
    }
    return codes.mapIndexed { idx, code -> code to comps[idx] }
}
