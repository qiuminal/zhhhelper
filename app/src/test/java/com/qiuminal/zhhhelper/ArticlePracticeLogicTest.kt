package com.qiuminal.zhhhelper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticlePracticeLogicTest {
    @Test
    fun normalizeText_unifiesLineEndingsAndTrailingNewlines() {
        val (text, truncated) = ArticlePracticeLogic.normalizeText("甲\r\n乙\r丙\n\n")
        assertEquals("甲\n乙\n丙\n", text)
        assertFalse(truncated)
    }

    @Test
    fun normalizeText_truncatesAtUtf16Limit() {
        val (text, truncated) = ArticlePracticeLogic.normalizeText("甲".repeat(8001))
        assertEquals(8000, text.length)
        assertTrue(truncated)
    }

    @Test
    fun codePoints_keepsSupplementaryCharactersAsOneCharacter() {
        assertArrayEquals(intArrayOf('甲'.code, 0x20000, '乙'.code), ArticlePracticeLogic.codePoints("甲𠀀乙"))
    }

    @Test
    fun stats_matchesDemoRules() {
        val result = ArticlePracticeLogic.stats(
            ArticlePracticeLogic.codePoints("甲乙丙丁"),
            ArticlePracticeLogic.codePoints("甲错丙丁"),
            elapsedSeconds = 2,
        )
        assertEquals(4, result.total)
        assertEquals(2, result.elapsedSeconds)
        assertEquals(90, result.speed)
        assertEquals(75, result.accuracy)
        assertEquals(1, result.errors)
    }

    @Test
    fun speed_countsOnlyCorrectWhitelistCharactersAsWords() {
        val reference = ArticlePracticeLogic.codePoints("甲，A1")
        val input = ArticlePracticeLogic.codePoints("甲错A1")
        assertEquals(3, ArticlePracticeLogic.correctCount(reference, input))
        assertEquals(90, ArticlePracticeLogic.correctCharactersPerMinute(3, 2))
    }
}
