package com.qiuminal.zhhhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun rootPairsMatchesCodeToComponent() {
        assertEquals(listOf("Sc" to "屮", "Fi" to "一"), parseRootPairs("Sc Fi", "屮 一"))
    }

    @Test
    fun rootPairsHandlesConcatenatedComponents() {
        // 部件无空格连写（如「上」的 ⺊一）与混合空格（如「学」的 ⺍冖 子）
        assertEquals(listOf("Yb" to "⺊", "Fi" to "一"), parseRootPairs("Yb Fi ", "⺊一"))
        assertEquals(listOf("Ks" to "⺍", "Wg" to "冖", "Hi" to "子"), parseRootPairs("Ks Wg Hi", "⺍冖 子"))
    }

    @Test
    fun rootPairsHandlesSurrogateAndPuaComponents() {
        // 增补平面部件（在 U+20087，代理对）与 PUA 部件（那 U+E430）
        val za = String(Character.toChars(0x20087))
        val pua = String(Character.toChars(0xE430))
        assertEquals(listOf("Ns" to za, "Vy" to "⺝"), parseRootPairs("Ns Vy ", "$za ⺝"))
        assertEquals(listOf("Ae" to "㇆", "Us" to pua, "Te" to "阝"), parseRootPairs("Ae Us Te", "㇆$pua 阝"))
    }

    @Test
    fun rootPairsCountMismatchReturnsNull() {
        assertNull(parseRootPairs("Pj Mk Fi", "𰃮冂一口"))
    }

    @Test
    fun rootPairsEmptyCodesReturnsNull() {
        assertNull(parseRootPairs(null, "一"))
        assertNull(parseRootPairs("", "一"))
        assertNull(parseRootPairs("   ", "一"))
    }

    @Test
    fun rootPairsAllEmptyReturnsNull() {
        assertNull(parseRootPairs(null, null))
        assertNull(parseRootPairs("", ""))
    }
}
