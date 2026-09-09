package com.qiuminal.zhhhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleResultHistoryTest {
    private fun result(index: Int) = ArticleResult("t$index", index, index, index, 100, 0, index.toLong())

    @Test
    fun page_usesMeasuredDynamicSize() {
        val results = (0 until 12).map(::result)
        assertEquals(listOf(0, 1, 2), articleResultPage(results, 0, 3).map { it.characters })
        assertEquals(4, articleResultPageCount(results.size, 3))
    }

    @Test
    fun page_returnsRemainingRowsAndClampsIndex() {
        val results = (0 until 12).map(::result)
        assertEquals(listOf(10, 11), articleResultPage(results, 2, 5).map { it.characters })
        assertEquals(listOf(10, 11), articleResultPage(results, 99, 5).map { it.characters })
    }

    @Test
    fun emptyHistory_hasOneEmptyPage() {
        assertEquals(1, articleResultPageCount(0, 4))
        assertEquals("1/1", articleResultPageLabel(0, 0, 4))
        assertEquals(emptyList<ArticleResult>(), articleResultPage(emptyList(), 0, 4))
    }
}
