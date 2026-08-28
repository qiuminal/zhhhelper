package com.qiuminal.zhhhelper

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import java.util.HashMap

/**
 * 全局字体管理器。
 *
 * 四款内置字体全部从 res/font 资源加载（Android 官方字体机制，
 * ResourcesCompat.getFont，兼容 API 23+），不再走 assets +
 * Typeface.createFromAsset，避免大字体在部分系统上加载失败或返回空字体，
 * 导致整包字体静默失效的问题。
 *
 * fallback 顺序：TumanPUA（虎码私有区部件）→ 霞鹜文楷（常用汉字）→
 * 遍黑体 P1（CJK 扩展 B-F）→ 遍黑体 P2（CJK 扩展 G），
 * 全部字体都无字形时交给系统字体（emoji、拉丁等）。
 *
 * load() 必须在后台线程调用（会解压并解析约 59MB 字体），
 * 完成后在主线程 applyToHierarchy() 应用到整棵视图树。
 */
object AppFonts {

    private val FONT_RES_IDS = intArrayOf(
        R.font.tuman_pua,
        R.font.lxgw_wenkai_screen,
        R.font.plangothic_p1,
        R.font.plangothic_p2,
    )

    @Volatile
    private var loaded = false

    private var familyTypeface: Typeface? = null
    private var fonts: Array<Typeface?>? = null
    private var paints: Array<Paint?>? = null
    private val charFontCache = HashMap<Int, Typeface?>()

    /**
     * 后台加载字体。幂等，线程安全；失败的单款字体以 null 跳过，
     * 不阻塞其余字体。
     */
    @Synchronized
    fun load(context: Context) {
        if (loaded) {
            return
        }
        val appContext = context.applicationContext
        familyTypeface = getFontSafely(appContext, R.font.zhhhelper_fonts)
        val newFonts = arrayOfNulls<Typeface>(FONT_RES_IDS.size)
        val newPaints = arrayOfNulls<Paint>(FONT_RES_IDS.size)
        for (i in FONT_RES_IDS.indices) {
            newFonts[i] = getFontSafely(appContext, FONT_RES_IDS[i])
            val paint = Paint()
            paint.typeface = newFonts[i]
            newPaints[i] = paint
        }
        fonts = newFonts
        paints = newPaints
        loaded = true
    }

    fun isLoaded(): Boolean = loaded

    private fun getFontSafely(context: Context, resId: Int): Typeface? = try {
        ResourcesCompat.getFont(context, resId)
    } catch (e: Exception) {
        null
    }

    /**
     * 递归应用到整棵视图树，实现全 APP 全局字体。
     * 需在 load() 完成后于主线程调用。
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
        val tfs = fonts ?: return

        // API 26+：先把四字体族设为控件 base typeface。
        // 动态文本（如搜索框输入）不经 span 也能命中内置字体，
        // 未覆盖字符自动回退系统字体。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val family = familyTypeface
            if (family != null) {
                val style = textView.typeface?.style ?: Typeface.NORMAL
                textView.setTypeface(family, style)
            }
        }

        // 提示文字（EditText hint）做字符级回退
        if (textView is EditText) {
            val hint = textView.hint
            if (hint != null && hint.isNotEmpty()) {
                textView.setHint(style(hint, tfs))
            }
            // 输入正文由 base typeface 负责（26+），此处不重设，避免打断输入
            return
        }

        // 正文：字符级 span（全 API 生效）
        val text = textView.text
        if (text != null && text.isNotEmpty()) {
            textView.setText(style(text, tfs))
        }
    }

    /**
     * 返回能渲染该字符的第一款内置字体；全部无法渲染时返回 null（系统字体）。
     */
    private fun fontForCodePoint(codePoint: Int, tfs: Array<Typeface?>): Typeface? {
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
    private fun style(text: CharSequence?, tfs: Array<Typeface?>): CharSequence? {
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
            val font = fontForCodePoint(cp, tfs)
            while (end < length) {
                val nextCp = plain.codePointAt(end)
                if (fontForCodePoint(nextCp, tfs) != font) {
                    break
                }
                end += Character.charCount(nextCp)
            }
            if (font != null) {
                spannable.setSpan(
                    FontTypefaceSpan(font),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            start = end
        }
        return spannable
    }

    /**
     * 兼容 API 23+ 的自定义字体 span（框架 TypefaceSpan(Typeface) 构造器需 API 28+）。
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
