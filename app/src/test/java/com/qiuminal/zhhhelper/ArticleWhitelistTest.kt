package com.qiuminal.zhhhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleWhitelistTest {
    @Test
    fun filter_removesWhitespaceLineBreaksAndUnknownSymbols() {
        val allowed = intArrayOf('A'.code, 'B'.code, '中'.code).sortedArray()
        assertEquals("AB中B", filterArticleText("A B\r\n中🙂B", allowed))
    }

    @Test
    fun filter_handlesSupplementaryCodePointsAsSingleCharacter() {
        val extensionB = 0x20000
        val allowed = intArrayOf('甲'.code, extensionB).sortedArray()
        assertEquals("甲𠀀", filterArticleText("甲𠀀乙", allowed))
    }

    @Test
    fun filter_preservesAsciiSpaceOnlyWhenWhitelistContainsIt() {
        val allowed = intArrayOf(' '.code, 'a'.code).sortedArray()
        assertEquals("a a", filterArticleText("a a\t", allowed))
    }
}
