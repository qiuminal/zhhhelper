package com.qiuminal.zhhhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeMathTest {

    @Test
    fun keyAccuracyPerfectAndWithErrors() {
        assertEquals(100.0, keyAccuracyPct(33, 0), 1e-9)
        assertEquals(96.9696969697, keyAccuracyPct(33, 1), 1e-9)
        assertEquals(0.0, keyAccuracyPct(10, 99), 1e-9)
        assertEquals(100.0, keyAccuracyPct(0, 5), 1e-9)
    }

    @Test
    fun kpsSpeedNormalCase() {
        // 剩余 33 键 / 30 秒 → 击键 1.1 键/秒；首字键数 4、10 字全对 → 速度按方案 A 推算
        val (kps, speed, infinite) = computeKpsAndSpeed(10, 33, 30_000L, 4, allowInfinite = true)
        assertTrue(!infinite)
        assertEquals(1.1, kps, 1e-9)
        assertEquals(10 * 33.0 / (30.0 * 37.0) * 60.0, speed, 1e-9)
    }

    @Test
    fun singleBatchCommitsAreInfinite() {
        val (kps, speed, infinite) = computeKpsAndSpeed(10, 0, 0L, 3, allowInfinite = true, singleBatch = true)
        assertTrue(infinite)
        assertTrue(kps.isInfinite() && speed.isInfinite())
    }

    @Test
    fun zeroKeysWithoutInfiniteIsZero() {
        val (kps, speed, infinite) = computeKpsAndSpeed(10, 0, 30_000L, 3, allowInfinite = false)
        assertTrue(!infinite)
        assertEquals(0.0, kps, 1e-9)
        assertEquals(0.0, speed, 1e-9)
    }

    @Test
    fun editDistanceBasics() {
        assertEquals(0, levenshteinDistance("abc", "abc"))
        assertEquals(1, levenshteinDistance("abc", "abd"))
        assertEquals(1, levenshteinDistance("abcd", "abc"))
        assertEquals(1, levenshteinDistance("abc", "abcd"))
        assertEquals(3, levenshteinDistance("abc", "xyz"))
        assertEquals(2, levenshteinDistance("", "ab"))
        assertEquals(2, levenshteinDistance("ab", ""))
    }
}
