package com.qiuminal.zhhhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.ceil

/**
 * 文章字帖专用排版视图：中文两端对齐、逐字状态和光标使用同一份行布局。
 * 光标直接按所在行的真实 top/bottom 绘制，避免 TextView ReplacementSpan 在第二行后受行距影响错位。
 */
class ArticleCopybookView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    companion object {
        private const val COLOR_PENDING = 0xFF333333.toInt()
        private const val COLOR_CORRECT = 0xFF2E9E5B.toInt()
        private const val COLOR_CORRECT_BG = 0xFFE8F5E9.toInt()
        private const val COLOR_WRONG = 0xFFD64545.toInt()
        private const val COLOR_WRONG_BG = 0xFFFDECEA.toInt()
        private const val COLOR_CURSOR = 0xFF5B7FB5.toInt()
        private const val FORBIDDEN_LINE_START = "，。、；：？！）》】」』”’…—·～｝〕〗〙〟〞｡､｣"
        private const val FORBIDDEN_LINE_END = "（《〈「『【〔〖｛“‘"
    }

    private val caretWidthPx = dp(2.5f)
    private val caretTextGapPx = 2f
    private val caretSlotWidthPx = caretWidthPx + caretTextGapPx
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var reference = IntArray(0)
    private var input = IntArray(0)
    private var textSizePx = sp(26f)
    private var lineSpacingPx = dp(8f)
    private val lines = mutableListOf<Line>()
    private var contentHeight = 0f
    private var builtWidth = -1
    private var layoutDirty = true

    fun setPractice(reference: IntArray, input: IntArray) {
        val referenceChanged = !this.reference.contentEquals(reference)
        this.reference = reference.copyOf()
        this.input = input.copyOf()
        if (referenceChanged) layoutDirty = true
        requestLayout()
        invalidate()
    }

    fun setTextSizeSp(value: Float) {
        textSizePx = sp(value)
        layoutDirty = true
        requestLayout()
        invalidate()
    }

    fun currentPositionBottom(): Int {
        if (lines.isEmpty()) return paddingTop
        val current = input.size.coerceAtMost(reference.size)
        var bottom = paddingTop.toFloat()
        for (line in lines) {
            bottom += line.ascent + line.descent
            if (current <= line.runs.last().index || line === lines.last()) return bottom.toInt()
            bottom += lineSpacingPx
        }
        return bottom.toInt()
    }

    /**
     * 计算跟随滚动目标（返回「字帖视图坐标系」内的偏移，未做 ScrollView padding 换算，可为负）：
     * 让光标行下方始终保留 2 个整行的余量，即光标刚进入可视区倒数第二行时滚动就开始，
     * 其下一行（甚至下下行）已提前进入视口；当光标已到全文最后一行时退化为保留 1 行。
     * viewportHeight 应为 ScrollView 真实可视区高度（不含 padding）。
     * 调用方需自行换算并钳制：最终 scrollY = 返回值 - paddingBottom（不小于 0）。
     */
    fun scrollTarget(viewportHeight: Int): Int {
        if (lines.isEmpty() || viewportHeight <= 0) return 0
        val current = input.size.coerceAtMost(reference.size)
        var currentLine = lines.lastIndex
        for ((index, line) in lines.withIndex()) {
            if (current <= line.runs.last().index) {
                currentLine = index
                break
            }
        }
        val lineHeight = lines[currentLine].ascent + lines[currentLine].descent + lineSpacingPx
        val bottom = currentPositionBottom()
        val slack = if (currentLine >= lines.lastIndex) lineHeight else 2f * lineHeight
        return (bottom + slack - viewportHeight).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        ensureLayout((width - paddingLeft - paddingRight).coerceAtLeast(0))
        val desiredHeight = ceil(contentHeight + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w != oldw) {
            layoutDirty = true
            ensureLayout((w - paddingLeft - paddingRight).coerceAtLeast(0))
        }
    }

    private fun ensureLayout(availableWidth: Int) {
        val textWidth = (availableWidth - caretSlotWidthPx).toInt().coerceAtLeast(0)
        if (!layoutDirty && builtWidth == textWidth) return
        builtWidth = textWidth
        layoutDirty = false
        buildLines(textWidth.toFloat())
    }

    private fun buildLines(availableWidth: Float) {
        lines.clear()
        contentHeight = 0f
        if (reference.isEmpty() || availableWidth <= 0f) return
        var index = 0
        var runs = mutableListOf<Run>()
        var width = 0f
        while (index < reference.size) {
            val run = makeRun(reference[index], index)
            if (runs.isEmpty() || width + run.width <= availableWidth) {
                runs.add(run)
                width += run.width
                index++
                continue
            }
            if (isForbiddenLineStart(run.codePoint)) {
                val carried = mutableListOf<Run>()
                while (runs.isNotEmpty()) {
                    val removed = runs.removeAt(runs.lastIndex)
                    width -= removed.width
                    carried.add(0, removed)
                    if (!isForbiddenLineStart(carried.first().codePoint)) break
                }
                if (runs.isNotEmpty()) finishLine(runs, width, availableWidth, false)
                runs = carried
                width = runs.sumOf { it.width.toDouble() }.toFloat()
                if (width + run.width <= availableWidth) {
                    runs.add(run)
                    width += run.width
                    index++
                }
            } else {
                val carried = mutableListOf<Run>()
                while (runs.isNotEmpty() && isForbiddenLineEnd(runs.last().codePoint)) {
                    val removed = runs.removeAt(runs.lastIndex)
                    width -= removed.width
                    carried.add(0, removed)
                }
                if (runs.isNotEmpty()) finishLine(runs, width, availableWidth, false)
                runs = carried
                width = runs.sumOf { it.width.toDouble() }.toFloat()
            }
        }
        if (runs.isNotEmpty()) finishLine(runs, width, availableWidth, true)
        if (lines.isNotEmpty()) contentHeight -= lineSpacingPx
    }

    private fun makeRun(codePoint: Int, index: Int): Run {
        paint.textSize = textSizePx
        paint.typeface = AppFonts.typefaceForCodePoint(codePoint)
        val value = String(Character.toChars(codePoint))
        return Run(codePoint, index, value, paint.measureText(value), paint.typeface)
    }

    private fun finishLine(runs: List<Run>, width: Float, availableWidth: Float, last: Boolean) {
        var ascent = 0f
        var descent = 0f
        for (run in runs) {
            paint.textSize = textSizePx
            paint.typeface = run.font
            val fm = paint.fontMetrics
            ascent = maxOf(ascent, -fm.ascent)
            descent = maxOf(descent, fm.descent)
        }
        val expandAfter = BooleanArray(runs.size)
        var gapCount = 0
        if (!last) {
            for (i in 0 until runs.lastIndex) {
                if (isCjkLike(runs[i].codePoint) && isCjkLike(runs[i + 1].codePoint)) {
                    expandAfter[i] = true
                    gapCount++
                }
            }
        }
        val gap = if (gapCount > 0 && availableWidth > width) (availableWidth - width) / gapCount else 0f
        val visualWidths = FloatArray(runs.size) { runs[it].width }
        for (i in visualWidths.indices) {
            if (expandAfter[i]) visualWidths[i] += gap
        }
        lines += Line(runs.toList(), visualWidths, ascent, descent)
        contentHeight += ascent + descent + lineSpacingPx
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lines.isEmpty()) return
        val current = input.size.coerceAtMost(reference.size)
        var lineTop = paddingTop.toFloat()
        for (line in lines) {
            val baseline = lineTop + line.ascent
            val lineBottom = lineTop + line.ascent + line.descent
            var x = paddingLeft + caretSlotWidthPx
            for ((runPosition, run) in line.runs.withIndex()) {
                val visualWidth = line.visualWidths[runPosition]
                val caretX = if (runPosition == 0) x - caretSlotWidthPx else x
                if (run.index == current) drawCaret(canvas, caretX, lineTop, lineBottom)
                paint.textSize = textSizePx
                paint.typeface = run.font
                val state = when {
                    run.index >= input.size -> 0
                    input[run.index] == run.codePoint -> 1
                    else -> 2
                }
                if (state != 0) {
                    paint.color = if (state == 1) COLOR_CORRECT_BG else COLOR_WRONG_BG
                    canvas.drawRect(x, lineTop, x + visualWidth + 0.5f, lineBottom, paint)
                }
                paint.color = when (state) {
                    1 -> COLOR_CORRECT
                    2 -> COLOR_WRONG
                    else -> COLOR_PENDING
                }
                canvas.drawText(run.value, x, baseline, paint)
                x += visualWidth
            }
            if (current == reference.size && line === lines.last()) {
                drawCaret(canvas, x, lineTop, lineBottom)
            }
            lineTop = lineBottom + lineSpacingPx
        }
    }

    private fun drawCaret(canvas: Canvas, x: Float, top: Float, bottom: Float) {
        paint.color = COLOR_CURSOR
        val inset = (bottom - top) * 0.12f
        canvas.drawRect(x, top + inset - 1f, x + dp(2.5f), bottom - inset + 1f, paint)
    }

    private fun isForbiddenLineStart(codePoint: Int): Boolean =
        codePoint <= Char.MAX_VALUE.code && codePoint.toChar() in FORBIDDEN_LINE_START

    private fun isForbiddenLineEnd(codePoint: Int): Boolean =
        codePoint <= Char.MAX_VALUE.code && codePoint.toChar() in FORBIDDEN_LINE_END

    private fun isCjkLike(codePoint: Int): Boolean =
        codePoint in 0x2E80..0x2EFF ||
            codePoint in 0x3000..0x303F ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0xFF00..0xFFEF ||
            codePoint in 0x20000..0x2FA1F

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private data class Run(
        val codePoint: Int,
        val index: Int,
        val value: String,
        val width: Float,
        val font: Typeface?,
    )

    private data class Line(
        val runs: List<Run>,
        val visualWidths: FloatArray,
        val ascent: Float,
        val descent: Float,
    )
}
