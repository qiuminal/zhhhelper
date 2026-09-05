package com.qiuminal.zhhhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class UiFormatTest {

    @Test
    fun pinyinEmptyOrNullShowsFallback() {
        assertEquals("无", formatPinyin(null))
        assertEquals("无", formatPinyin(""))
        assertEquals("无", formatPinyin("  "))
    }

    @Test
    fun pinyinWrapsWithParentheses() {
        assertEquals("(shi)", formatPinyin("shi"))
        assertEquals("(lüe)", formatPinyin("lüe"))
    }

    @Test
    fun unicodeShowsFallbackWhenBothMissing() {
        assertEquals("无", formatUnicode(null, null))
        assertEquals("无", formatUnicode("", ""))
    }

    @Test
    fun unicodeOnlyCodePointIsBracketed() {
        assertEquals("〔U+7684〕", formatUnicode("", "U+7684"))
        assertEquals("〔U+1F600〕", formatUnicode(null, "U+1F600"))
    }

    @Test
    fun unicodeBlockOnlyShowsBlock() {
        assertEquals("基本", formatUnicode("基本", null))
    }

    @Test
    fun unicodeJoinsBlockAndCodePoint() {
        assertEquals("基本 〔U+7684〕", formatUnicode("基本", "U+7684"))
        assertEquals("中日韩统一表意文字扩展A 〔U+3400〕", formatUnicode("中日韩统一表意文字扩展A", "U+3400"))
    }
}
