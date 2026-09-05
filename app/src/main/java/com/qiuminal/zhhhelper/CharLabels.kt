package com.qiuminal.zhhhelper

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.Adler32

/**
 * label.txt 标签码表（构建期由 generateLabelsBin 编译为 assets/labels.bin）。
 * 供拆分查询卡片、分享图片、练单错字速查弹窗三处共用：
 *  - 字头右侧的标签胶囊（每种标签一种配色）
 *  - 编码栏中命中「标签编码」的编码片段按对应标签主色着色
 *  - 标签配色与编码栏默认色 code_blue 区分
 */
object CharLabels {

    data class LabelEntry(val code: String, val label: String)

    data class LabelColors(val text: Int, val bg: Int, val stroke: Int)

    private const val ASSET_NAME = "labels.bin"
    private const val MAGIC = 0x5A484C42 // "ZHLB"
    private const val VERSION = 1
    // 胶囊体积与纵向补偿（视觉微调集中在这里）
    private const val CHIP_HEIGHT_DP = 18          // 高度由 20 压扁 2dp
    private const val CHIP_PADDING_H_DP = 7        // 两侧各减 1dp => 总宽度缩短 2dp
    private const val CHIP_EXTRA_DOWN_DP = 1f      // 在主字体腰部基准上再下移 1dp

    @Volatile
    private var buf: ByteBuffer? = null
    private var count = 0
    private var indexOffset = 0
    private var valuesOffset = 0
    private val flashHandler = Handler(Looper.getMainLooper())
    private var flashSequence = 0

    @Synchronized
    fun load(context: Context) {
        try {
            val bytes = context.assets.open(ASSET_NAME).use { it.readBytes() }
            val b = ByteBuffer.wrap(bytes) // 默认大端
            if (b.int != MAGIC) return
            if (b.int != VERSION) return
            count = b.int
            indexOffset = b.int
            valuesOffset = b.int
            val expected = b.int.toLong() and 0xFFFFFFFFL
            val crc = Adler32()
            crc.update(bytes, indexOffset, bytes.size - indexOffset)
            if (crc.value != expected) return
            buf = b
        } catch (e: Exception) {
            // 非关键路径失败：静默降级，避免打扰用户
        }
    }

    fun isLoaded(): Boolean = buf != null

    /** 返回某字在 label.txt 中的全部 (编码,标签) 去重记录，保持首次出现顺序。 */
    fun entriesFor(char: String?): List<LabelEntry> {
        val src = buf ?: return emptyList()
        if (char.isNullOrEmpty()) return emptyList()
        val b = src.duplicate()
        val c1 = char[0].code
        val c2 = if (char.length > 1) char[1].code else 0
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val pos = indexOffset + mid * 12
            val k1 = b.getShort(pos).toInt() and 0xFFFF
            val k2 = b.getShort(pos + 2).toInt() and 0xFFFF
            val cmp = when {
                c1 < k1 || (c1 == k1 && c2 < k2) -> -1
                c1 > k1 || (c1 == k1 && c2 > k2) -> 1
                else -> 0
            }
            if (cmp < 0) {
                hi = mid - 1
            } else if (cmp > 0) {
                lo = mid + 1
            } else {
                val off = valuesOffset + b.getInt(pos + 4)
                val len = b.getInt(pos + 8)
                return readEntries(b, off, len)
            }
        }
        return emptyList()
    }

    private fun readEntries(b: ByteBuffer, off: Int, len: Int): List<LabelEntry> {
        val result = ArrayList<LabelEntry>()
        var p = off
        val end = off + len
        if (p >= end) return result
        var remaining = b.get(p).toInt() and 0xFF
        p += 1
        while (remaining > 0 && p < end) {
            val codeLen = b.get(p).toInt() and 0xFF
            p += 1
            if (p + codeLen > end) break
            val code = String(b.array(), p, codeLen, StandardCharsets.US_ASCII)
            p += codeLen
            if (p >= end) break
            val labelLen = b.get(p).toInt() and 0xFF
            p += 1
            if (p + labelLen > end) break
            val label = String(b.array(), p, labelLen, StandardCharsets.UTF_8)
            p += labelLen
            result.add(LabelEntry(code, label))
            remaining--
        }
        return result
    }

    /** 去重后的标签名列表（胶囊展示顺序 = label.txt 首次出现顺序）。 */
    fun labelsFor(char: String?): List<String> {
        val out = ArrayList<String>()
        for (e in entriesFor(char)) {
            if (!out.contains(e.label)) out.add(e.label)
        }
        return out
    }

    private fun entryForCode(entries: List<LabelEntry>, code: String): LabelEntry? {
        for (e in entries) {
            if (e.code == code) return e
        }
        return null
    }

    /**
     * 编码栏着色：逐编码匹配「标签编码」，命中的编码片段用对应标签主色，
     * 未命中片段保持编码栏默认色（xml 中 code_blue）。无命中时不加任何 span。
     */
    fun styleCodes(context: Context, codes: CharSequence?, char: String?): CharSequence {
        if (codes.isNullOrEmpty() || char.isNullOrEmpty()) return codes ?: ""
        val entries = entriesFor(char)
        if (entries.isEmpty()) return codes
        val spannable = SpannableString(codes)
        val tokenRegex = Regex("[A-Za-z]+")
        for (match in tokenRegex.findAll(codes.toString())) {
            val entry = entryForCode(entries, match.value) ?: continue
            val colors = paletteFor(context, entry.label)
            spannable.setSpan(
                ForegroundColorSpan(colors.text),
                match.range.first,
                match.range.last + 1,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    /** 每种标签的配色：text 同时用于对应编码着色，bg/stroke 用于胶囊。 */
    fun paletteFor(context: Context, label: String): LabelColors = when (label) {
        "回头码" -> labelColors(
            context, R.color.label_text_huishou, R.color.label_bg_huishou, R.color.label_stroke_huishou
        )
        "先中容错" -> labelColors(
            context, R.color.label_text_xianzhong, R.color.label_bg_xianzhong, R.color.label_stroke_xianzhong
        )
        "顺取容错" -> labelColors(
            context, R.color.label_text_shunqu, R.color.label_bg_shunqu, R.color.label_stroke_shunqu
        )
        "重复末码" -> labelColors(
            context, R.color.label_text_chongfu, R.color.label_bg_chongfu, R.color.label_stroke_chongfu
        )
        "音补" -> labelColors(
            context, R.color.label_text_yinbu, R.color.label_bg_yinbu, R.color.label_stroke_yinbu
        )
        else -> labelColors(context, R.color.text_secondary, R.color.border_light, R.color.border_light)
    }

    private fun labelColors(context: Context, textRes: Int, bgRes: Int, strokeRes: Int): LabelColors =
        LabelColors(
            ContextCompat.getColor(context, textRes),
            ContextCompat.getColor(context, bgRes),
            ContextCompat.getColor(context, strokeRes)
        )

    /**
     * 向「字头」所在横向行尾部追加标签胶囊（参考历史胶囊但更扁、更紧凑）。
     * 容器须为 horizontal LinearLayout；无标签时不追加任何 View。
     * @param codesTv 传入编码栏 TextView 后，标签可点击并闪烁对应标签编码。
     */
    fun addLabelChips(
        context: Context,
        container: LinearLayout,
        char: String?,
        anchorCharTv: TextView? = null,
        codesTv: TextView? = null,
        chipHeightDp: Int = CHIP_HEIGHT_DP,
        textSizeSp: Float = 11f,
        horizontalPaddingDp: Int = CHIP_PADDING_H_DP
    ) {
        if (!isLoaded()) return
        val labels = labelsFor(char)
        if (labels.isEmpty()) return
        val density = context.resources.displayMetrics.density
        // 以主字体霞鹜文楷的「字形墨迹中心（腰部）」为基准做纵向微调并整体再下移 1dp：
        // 胶囊默认按文本行盒垂直居中，而行盒顶部带 font padding，会使胶囊视觉上偏高。
        // 常用字基本走霞鹜文楷，对齐以此为准；遍黑体/TumanPUA 等回退生僻字降低对齐优先级。
        val shiftY = if (anchorCharTv != null) {
            glyphCenterOffset(anchorCharTv) + CHIP_EXTRA_DOWN_DP * density
        } else {
            0f
        }
        for (label in labels) {
            val colors = paletteFor(context, label)
            val chip = TextView(context)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (chipHeightDp * density).toInt()
            )
            lp.gravity = Gravity.CENTER_VERTICAL
            lp.marginStart = (6 * density).toInt()
            chip.layoutParams = lp
            chip.text = AppFonts.style(label) ?: label
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            chip.setTextColor(colors.text)
            chip.includeFontPadding = false
            chip.gravity = Gravity.CENTER
            chip.setPadding(
                (horizontalPaddingDp * density).toInt(),
                0,
                (horizontalPaddingDp * density).toInt(),
                0
            )
            if (shiftY != 0f) {
                chip.translationY = shiftY
            }
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.RECTANGLE
            gd.cornerRadius = (chipHeightDp / 2f) * density
            gd.setColor(colors.bg)
            gd.setStroke(Math.max(1, (density * 1f).toInt()), colors.stroke)
            chip.background = gd
            if (codesTv != null) {
                chip.isClickable = true
                chip.setOnClickListener {
                    flashLabelCodes(codesTv, char, label)
                }
            }
            container.addView(chip)
        }
    }

    /**
     * 点击标签胶囊：让该字编码栏中命中「该标签编码」的编码片段高亮闪烁一下，
     * 提示标签与编码的对应关系。错字速查弹窗传入的是弹窗内的编码栏，同样生效。
     */
    fun flashLabelCodes(codesTv: TextView?, char: String?, label: String) {
        if (codesTv == null || char.isNullOrEmpty()) return
        val text = codesTv.text ?: return
        val codes = text.toString()
        val entries = entriesFor(char)
        if (entries.isEmpty()) return
        // 取该标签下的去重编码（同字同码重复行只保留一次）
        val codeSet = LinkedHashSet<String>()
        for (e in entries) {
            if (e.label == label) codeSet.add(e.code)
        }
        if (codeSet.isEmpty()) return
        val ranges = ArrayList<IntRange>()
        for (m in Regex("[A-Za-z]+").findAll(codes)) {
            if (m.value in codeSet) ranges.add(m.range)
        }
        if (ranges.isEmpty()) return

        val bg = paletteFor(codesTv.context, label).bg
        val seq = ++flashSequence
        flashHandler.removeCallbacksAndMessages(null)

        val capsuleView = codesTv as? CodeCapsuleTextView
        if (capsuleView != null) {
            // 胶囊形高亮：圆角底色、无描边，闪烁 4 次便于感知
            val on = Runnable {
                if (seq == flashSequence) capsuleView.setHighlightRanges(ranges, bg)
            }
            val off = Runnable {
                if (seq == flashSequence) capsuleView.clearHighlightRanges()
            }
            flashHandler.postDelayed(on, 0L)
            flashHandler.postDelayed(off, 140L)
            flashHandler.postDelayed(on, 280L)
            flashHandler.postDelayed(off, 420L)
            flashHandler.postDelayed(on, 560L)
            flashHandler.postDelayed(off, 700L)
            flashHandler.postDelayed(on, 840L)
            flashHandler.postDelayed(off, 980L)
            return
        }

        // 普通 TextView 兜底：矩形底色闪烁（同样 4 次）
        val base = SpannableString(text) // 保留原有编码颜色与字体 span
        val on = Runnable {
            if (seq != flashSequence) return@Runnable
            val s = SpannableString(base)
            for (range in ranges) {
                s.setSpan(
                    BackgroundColorSpan(bg),
                    range.first,
                    range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            codesTv.setText(s)
        }
        val off = Runnable {
            if (seq != flashSequence) return@Runnable
            codesTv.setText(base)
        }
        flashHandler.postDelayed(on, 0L)
        flashHandler.postDelayed(off, 140L)
        flashHandler.postDelayed(on, 280L)
        flashHandler.postDelayed(off, 420L)
        flashHandler.postDelayed(on, 560L)
        flashHandler.postDelayed(off, 700L)
        flashHandler.postDelayed(on, 840L)
        flashHandler.postDelayed(off, 980L)
    }

    /**
     * 以霞鹜文楷为基准，计算字头行盒中心与常用字「字形腰部」的纵向差值（向下为正，单位 px）。
     * 三种展示位置共用同一基准，保证主字体（覆盖绝大多数汉字）与胶囊对齐稳定。
     */
    private fun glyphCenterOffset(anchor: TextView): Float {
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.typeface = AppFonts.mainFontTypeface() ?: anchor.typeface
            paint.textSize = anchor.textSize
            val fm = paint.fontMetrics
            val bounds = Rect()
            paint.getTextBounds("道", 0, 1, bounds)
            (bounds.top + bounds.bottom - fm.top - fm.bottom) / 2f
        } catch (e: Exception) {
            0f
        }
    }
}
