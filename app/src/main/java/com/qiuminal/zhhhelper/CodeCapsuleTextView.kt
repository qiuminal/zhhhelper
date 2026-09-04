package com.qiuminal.zhhhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * 编码栏 TextView：支持把命中的标签编码以「胶囊形高亮」提示对应关系。
 * 点击标签胶囊闪烁时，仅对命中片段绘制圆角胶囊底色（无矩形块、无描边），
 * 文字本身仍由 TextView 正常绘制，避免矩形背景盖住字形。
 */
class CodeCapsuleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var highlightRanges: List<IntRange> = emptyList()
    private var highlightColor = 0
    private val highlightRect = RectF()
    private val measurePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val glyphBounds = Rect()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        // Codes TextView is wrap_content, so the last code letter ends exactly at the
        // view right edge and the capsule's right arc gets clipped. Reserve end padding
        // (text start unchanged) so the highlight always has room to draw its right cap.
        val endCap = (resources.displayMetrics.density * 7f).toInt()
        if (paddingRight < endCap) {
            setPadding(paddingLeft, paddingTop, endCap, paddingBottom)
        }
    }

    /** 设置要高亮的编码片段（胶囊形底色）；传空列表或调用 [clearHighlightRanges] 恢复。 */
    fun setHighlightRanges(ranges: List<IntRange>, color: Int) {
        highlightRanges = ranges
        highlightColor = color
        invalidate()
    }

    fun clearHighlightRanges() {
        if (highlightRanges.isNotEmpty()) {
            highlightRanges = emptyList()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        // 先画胶囊底色，再让 TextView 绘制文字，避免色块盖住字形
        drawCapsuleHighlights(canvas)
        super.onDraw(canvas)
    }

    private fun drawCapsuleHighlights(canvas: Canvas) {
        if (highlightRanges.isEmpty()) return
        val textLayout = layout ?: return
        val textLength = text?.length ?: 0
        if (textLength == 0) return
        fillPaint.color = highlightColor
        // Outward margins around tight glyph bounds: widest on left/right, and a bit
        // more room below the baseline so descenders (p/q/y/g/j) stay inside.
        val density = resources.displayMetrics.density
        val padHorizontal = density * 4.5f
        val padTop = density * 2.5f
        val padBottom = density * 3.5f
        val cornerRadius = density * 6f

        // 以当前 TextView 的画笔度量字形，保证字母尺寸/字重/字号变化后胶囊依然贴合
        measurePaint.set(paint)
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        for (range in highlightRanges) {
            val start = range.first
            val endExclusive = range.last + 1
            if (start < 0 || endExclusive > textLength || endExclusive <= start) continue
            val token = text!!.substring(start, endExclusive)
            measurePaint.getTextBounds(token, 0, token.length, glyphBounds)

            val line = textLayout.getLineForOffset(start)
            val baseline = textLayout.getLineBaseline(line).toFloat()
            val top = baseline + glyphBounds.top - padTop
            val bottom = baseline + glyphBounds.bottom + padBottom
            if (bottom <= top) continue

            val left = textLayout.getPrimaryHorizontal(start) - padHorizontal
            val right = textLayout.getPrimaryHorizontal(endExclusive) + padHorizontal
            if (right <= left) continue

            highlightRect.set(left, top, right, bottom)
            canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, fillPaint)
        }
        canvas.restore()
    }
}
