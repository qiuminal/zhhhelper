package com.qiuminal.zhhhelper

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 全局字体管理器。
 *
 * 加载：四款字体以 assets/fonts/ 单份随包分发（压缩存储控制体积），
 * 在后台线程解压到 cacheDir/fonts/ 后用 Typeface.createFromFile 加载
 * （mmap 直读，规避大字体走资源/流式加载在部分系统上失效的问题）。
 *
 * 选择：字符→字体 由构建期生成的 assets/font_coverage.bin（依据四款字体
 * cmap 逐码点判定，见 tools/gen_font_coverage.py）决定，运行时二分查找；
 * 不再依赖 Paint.hasGlyph（部分设备对大字体判定异常，会导致选中错误字体
 * 或漏选，最终退化为系统字体）。
 *
 * fallback 顺序：TumanPUA(1) → 霞鹜文楷(2) → 遍黑体P1(3) → 遍黑体P2(4)，
 * 全部没有字形时不给 span，交给系统字体（emoji、拉丁等）。
 *
 * load() 需在后台线程调用；完成后在主线程 applyToHierarchy() 应用全局。
 */
object AppFonts {

    private val FONT_ASSETS = arrayOf(
        "fonts/TumanPUA.ttf",
        "fonts/LXGWWenKaiGBScreen.ttf",
        "fonts/PlangothicP1.ttf",
        "fonts/PlangothicP2.ttf",
    )

    @Volatile
    private var loaded = false

    private var fonts: Array<Typeface?>? = null

    /** 覆盖表：[startCp, endCp, fontIdx(1..4)] 三元组平铺数组，按 startCp 升序且互不重叠。 */
    private var rangeData: IntArray? = null

    /**
     * 后台加载：覆盖表 + 解压字体 + createFromFile。幂等，线程安全；
     * 单款字体失败只影响该字体，不阻塞其余。
     */
    @Synchronized
    fun load(context: Context) {
        if (loaded) {
            return
        }
        val appContext = context.applicationContext
        rangeData = loadCoverage(appContext)
        val dir = File(appContext.cacheDir, "fonts").apply { mkdirs() }
        val newFonts = arrayOfNulls<Typeface>(FONT_ASSETS.size)
        for (i in FONT_ASSETS.indices) {
            val file = ensureExtracted(appContext, dir, FONT_ASSETS[i])
            newFonts[i] = file?.let { extracted ->
                try {
                    Typeface.createFromFile(extracted)
                } catch (e: Exception) {
                    null
                }
            }
        }
        fonts = newFonts
        loaded = true
    }

    fun isLoaded(): Boolean = loaded

    /** 把 assets 里的字体解压到缓存目录（已存在且大小一致则跳过），返回缓存文件。 */
    private fun ensureExtracted(context: Context, dir: File, assetPath: String): File? = try {
        val target = File(dir, assetPath.substringAfter('/'))
        val expected = try {
            context.assets.open(assetPath).use { it.available().toLong() }
        } catch (e: Exception) {
            -1L
        }
        if (!target.exists() || (expected > 0L && target.length() != expected)) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
        target
    } catch (e: Exception) {
        null
    }

    private fun loadCoverage(context: Context): IntArray? = try {
        val bytes = context.assets.open("font_coverage.bin").use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.int
        val data = IntArray(count * 3)
        for (i in 0 until count) {
            data[i * 3] = buf.int
            data[i * 3 + 1] = buf.int
            data[i * 3 + 2] = buf.get().toInt()
            buf.position(buf.position() + 3)
        }
        data
    } catch (e: Exception) {
        null
    }

    /** 返回码点对应的字体索引（1..4）；0 表示四款字体都没有该字形。 */
    private fun fontIndexForCodePoint(cp: Int): Int {
        val data = rangeData ?: return 0
        var lo = 0
        var hi = data.size / 3 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val i = mid * 3
            if (cp < data[i]) {
                hi = mid - 1
            } else if (cp > data[i + 1]) {
                lo = mid + 1
            } else {
                return data[i + 2]
            }
        }
        return 0
    }

    private fun fontForCodePoint(cp: Int): Typeface? {
        val tfs = fonts ?: return null
        val idx = fontIndexForCodePoint(cp)
        return if (idx in 1..tfs.size) tfs[idx - 1] else null
    }

    /** 公开查询：返回码点对应的内置字体；四款都没有字形时返回 null（用系统字体）。 */
    fun typefaceForCodePoint(cp: Int): Typeface? = fontForCodePoint(cp)

    /**
     * 递归应用到整棵视图树：静态文本与结果文本走 style() 重新 setText，
     * EditText 只处理 hint + 原地样式（正文由输入监听器实时套用，避免打断输入）。
     */
    fun applyToHierarchy(root: View?) {
        if (root == null || !loaded) {
            return
        }
        if (root is TextView) {
            applyToTextView(root)
        } else if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                applyToHierarchy(root.getChildAt(i))
            }
        }
    }

    private fun applyToTextView(textView: TextView) {
        if (textView is EditText) {
            val hint = textView.hint
            if (hint != null && hint.isNotEmpty()) {
                textView.setHint(style(hint))
            }
            val text = textView.text
            if (text != null && text.isNotEmpty()) {
                styleInPlace(text)
            }
            return
        }
        val text = textView.text
        if (text != null && text.isNotEmpty()) {
            textView.setText(style(text))
        }
    }

    /**
     * 对文本逐字符应用 fallback 字体，返回可直接 setText 的 CharSequence。
     */
    fun style(text: CharSequence?): CharSequence? {
        if (text == null || text.isEmpty()) {
            return text
        }
        val plain = text.toString()
        val spannable = SpannableString(text)
        val length = plain.length
        var start = 0
        while (start < length) {
            val cp = plain.codePointAt(start)
            var end = start + Character.charCount(cp)
            val font = fontForCodePoint(cp)
            while (end < length) {
                val nextCp = plain.codePointAt(end)
                if (fontForCodePoint(nextCp) !== font) {
                    break
                }
                end += Character.charCount(nextCp)
            }
            if (font != null) {
                spannable.setSpan(FontTypefaceSpan(font), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            start = end
        }
        return spannable
    }

    /**
     * 原地为 Editable（如搜索框输入）套用字体 span，不替换文本、不打断输入法。
     */
    fun styleInPlace(text: Editable) {
        if (!loaded || text.isEmpty()) {
            return
        }
        text.getSpans(0, text.length, FontTypefaceSpan::class.java).forEach { text.removeSpan(it) }
        val length = text.length
        var start = 0
        while (start < length) {
            val cp = Character.codePointAt(text, start)
            var end = start + Character.charCount(cp)
            val idx = fontIndexForCodePoint(cp)
            val font = if (idx in 1..4) fonts?.get(idx - 1) else null
            while (end < length) {
                val nextCp = Character.codePointAt(text, end)
                if (fontIndexForCodePoint(nextCp) != idx) {
                    break
                }
                end += Character.charCount(nextCp)
            }
            if (font != null) {
                text.setSpan(FontTypefaceSpan(font), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            start = end
        }
    }

    /**
     * 兼容 API 23+ 的自定义字体 span（框架 TypefaceSpan(Typeface) 构造器需 API 28+）。
     * 仅替换 typeface，保留视图原有的 fakeBold/fakeItalic 状态。
     */
    private class FontTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(textPaint: TextPaint) {
            textPaint.typeface = resolveStyle(textPaint)
        }

        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.typeface = resolveStyle(textPaint)
        }

        /** 保留画笔已有字重/斜体：叠加 StyleSpan 设置的粗体与仿粗体，再映射到自定义字体。 */
        private fun resolveStyle(textPaint: TextPaint): Typeface {
            val old = textPaint.typeface
            var style = old?.style ?: Typeface.NORMAL
            if (textPaint.isFakeBoldText) {
                style = style or Typeface.BOLD
            }
            if (style == Typeface.NORMAL) {
                return typeface
            }
            val styled = Typeface.create(typeface, style)
            if ((style and Typeface.BOLD) != 0 && (styled.style and Typeface.BOLD) == 0) {
                // 自定义字体无粗体文件时用仿粗体补齐，保证加粗仍可见
                textPaint.isFakeBoldText = true
            }
            return styled
        }
    }
}
