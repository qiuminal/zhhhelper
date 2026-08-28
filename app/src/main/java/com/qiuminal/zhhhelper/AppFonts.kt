package com.qiuminal.zhhhelper

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.util.HashMap

/**
 * 全局字体管理器（由 Java 版迁移，行为保持一致）。
 *
 * 内置四款字体，按「字符级 fallback」顺序逐字符选择渲染字体：
 *   1) TumanPUA（虎码私有区部件）
 *   2) 霞鹜文楷屏幕阅读版 LXGW WenKai GB Screen（常用汉字）
 *   3) 遍黑体 Plangothic P1（CJK 扩展 B-F）
 *   4) 遍黑体 Plangothic P2（CJK 扩展 G）
 *
 * 某字符在四款字体中都找不到字形时，不设置 span，交给系统默认字体渲染
 * （如 emoji、拉丁字符等）。
 */
object AppFonts {

    private val FONT_PATHS = arrayOf(
        "fonts/TumanPUA.ttf",
        "fonts/LXGWWenKaiGBScreen.ttf",
        "fonts/PlangothicP1.ttf",
        "fonts/PlangothicP2.ttf",
    )

    private var typefaces: Array<Typeface?>? = null
    private var paints: Array<Paint?>? = null
    private val charFontCache = HashMap<Int, Typeface?>()

    /**
     * 加载四款内置字体。幂等，可重复调用；必须在 apply 系列方法之前调用一次。
     */
    @Synchronized
    fun init(context: Context) {
        if (typefaces != null) {
            return
        }
        val am = context.assets
        val newTypefaces = arrayOfNulls<Typeface>(FONT_PATHS.size)
        val newPaints = arrayOfNulls<Paint>(FONT_PATHS.size)
        // 先赋值再逐项填充，与 Java 版保持一致的初始化语义
        typefaces = newTypefaces
        paints = newPaints
        for (i in FONT_PATHS.indices) {
            newTypefaces[i] = Typeface.createFromAsset(am, FONT_PATHS[i])
            val p = Paint()
            p.typeface = newTypefaces[i]
            newPaints[i] = p
        }
    }

    /**
     * 返回能渲染该字符的第一款内置字体；全部无法渲染时返回 null（交给系统字体）。
     */
    private fun fontForCodePoint(codePoint: Int): Typeface? {
        val tfs = typefaces ?: return null
        val pts = paints ?: return null
        val cached = charFontCache[codePoint]
        if (cached != null || charFontCache.containsKey(codePoint)) {
            return cached
        }
        var result: Typeface? = null
        for (i in tfs.indices) {
            val paint = pts[i] ?: continue
            if (paint.hasGlyph(String(Character.toChars(codePoint)))) {
                result = tfs[i]
                break
            }
        }
        charFontCache[codePoint] = result
        return result
    }

    /**
     * 对文本逐字符应用 fallback 字体，返回可直接 setText 的 CharSequence。
     */
    fun style(text: CharSequence?): CharSequence? {
        if (text == null || text.length == 0) {
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
                if (fontForCodePoint(nextCp) != font) {
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
     * 应用到单个 TextView（文本 + EditText 的提示文字）。
     */
    fun apply(textView: TextView?) {
        if (textView == null || typefaces == null) {
            return
        }
        if (textView is EditText) {
            val hint = textView.hint
            if (hint != null && hint.isNotEmpty()) {
                textView.setHint(style(hint))
            }
        }
        val text = textView.text
        if (text != null && text.isNotEmpty()) {
            textView.setText(style(text))
        }
    }

    /**
     * 递归应用到整棵视图树，实现全 APP 全局字体。
     */
    fun applyToHierarchy(root: View?) {
        if (root == null || typefaces == null) {
            return
        }
        if (root is TextView) {
            apply(root)
        } else if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                applyToHierarchy(root.getChildAt(i))
            }
        }
    }

    /**
     * 兼容 API 21+ 的自定义字体 span（框架 TypefaceSpan(Typeface) 构造器需 API 28+）。
     */
    private class FontTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }

        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }
    }
}
