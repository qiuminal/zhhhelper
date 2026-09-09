package com.qiuminal.zhhhelper

internal const val ARTICLE_RESULT_FALLBACK_PAGE_SIZE = 4

internal data class ArticleResult(
    val title: String,
    val characters: Int,
    val elapsedSeconds: Int,
    val speed: Int,
    val accuracy: Int,
    val errors: Int,
    val finishedAt: Long,
)

internal fun articleResultPageCount(size: Int, pageSize: Int): Int {
    val safeSize = pageSize.coerceAtLeast(1)
    return if (size <= 0) 1 else (size + safeSize - 1) / safeSize
}

internal fun articleResultPageLabel(page: Int, size: Int, pageSize: Int): String {
    val pages = articleResultPageCount(size, pageSize)
    return "${page.coerceIn(0, pages - 1) + 1}/$pages"
}

internal fun articleResultPage(results: List<ArticleResult>, page: Int, pageSize: Int): List<ArticleResult> {
    val safeSize = pageSize.coerceAtLeast(1)
    val safePage = page.coerceIn(0, articleResultPageCount(results.size, safeSize) - 1)
    val start = safePage * safeSize
    return results.subList(start, minOf(start + safeSize, results.size))
}
