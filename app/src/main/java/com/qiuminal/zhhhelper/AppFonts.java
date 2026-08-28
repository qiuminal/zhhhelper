package com.qiuminal.zhhhelper;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局字体管理器。
 *
 * 内置四款字体，按「字符级 fallback」顺序逐字符选择渲染字体：
 *   1) TumanPUA（虎码私有区部件）
 *   2) 霞鹜文楷屏幕阅读版 LXGW WenKai GB Screen（常用汉字）
 *   3) 遍黑体 Plangothic P1（CJK 扩展 B–F）
 *   4) 遍黑体 Plangothic P2（CJK 扩展 G）
 *
 * 某字符在四款字体中都找不到字形时，不设置 span，交给系统默认字体渲染
 * （如 emoji、拉丁字符等）。
 */
public final class AppFonts {

    private static final String[] FONT_PATHS = {
            "fonts/TumanPUA.ttf",
            "fonts/LXGWWenKaiGBScreen.ttf",
            "fonts/PlangothicP1.ttf",
            "fonts/PlangothicP2.ttf",
    };

    private static Typeface[] typefaces;
    private static Paint[] paints;
    private static final Map<Integer, Typeface> charFontCache = new HashMap<>();

    private AppFonts() {
    }

    /**
     * 加载四款内置字体。幂等，可重复调用；必须在 apply 系列方法之前调用一次。
     */
    public static synchronized void init(Context context) {
        if (typefaces != null) {
            return;
        }
        AssetManager am = context.getAssets();
        typefaces = new Typeface[FONT_PATHS.length];
        paints = new Paint[FONT_PATHS.length];
        for (int i = 0; i < FONT_PATHS.length; i++) {
            typefaces[i] = Typeface.createFromAsset(am, FONT_PATHS[i]);
            Paint p = new Paint();
            p.setTypeface(typefaces[i]);
            paints[i] = p;
        }
    }

    /**
     * 返回能渲染该字符的第一款内置字体；全部无法渲染时返回 null（交给系统字体）。
     */
    private static Typeface fontForCodePoint(int codePoint) {
        if (typefaces == null) {
            return null;
        }
        Typeface cached = charFontCache.get(codePoint);
        if (cached != null || charFontCache.containsKey(codePoint)) {
            return cached;
        }
        Typeface result = null;
        for (int i = 0; i < typefaces.length; i++) {
            if (paints[i].hasGlyph(new String(Character.toChars(codePoint)))) {
                result = typefaces[i];
                break;
            }
        }
        charFontCache.put(codePoint, result);
        return result;
    }

    /**
     * 对文本逐字符应用 fallback 字体，返回可直接 setText 的 CharSequence。
     */
    public static CharSequence style(CharSequence text) {
        if (text == null || text.length() == 0) {
            return text;
        }
        String plain = text.toString();
        SpannableString spannable = new SpannableString(text);
        int length = plain.length();
        int start = 0;
        while (start < length) {
            int cp = plain.codePointAt(start);
            int end = start + Character.charCount(cp);
            Typeface font = fontForCodePoint(cp);
            while (end < length) {
                int nextCp = plain.codePointAt(end);
                if (fontForCodePoint(nextCp) != font) {
                    break;
                }
                end += Character.charCount(nextCp);
            }
            if (font != null) {
                spannable.setSpan(new FontTypefaceSpan(font), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            start = end;
        }
        return spannable;
    }

    /**
     * 应用到单个 TextView（文本 + EditText 的提示文字）。
     */
    public static void apply(TextView textView) {
        if (textView == null || typefaces == null) {
            return;
        }
        if (textView instanceof EditText) {
            CharSequence hint = textView.getHint();
            if (hint != null && hint.length() > 0) {
                textView.setHint(style(hint));
            }
        }
        CharSequence text = textView.getText();
        if (text != null && text.length() > 0) {
            textView.setText(style(text));
        }
    }

    /**
     * 递归应用到整棵视图树，实现全 APP 全局字体。
     */
    public static void applyToHierarchy(View root) {
        if (root == null || typefaces == null) {
            return;
        }
        if (root instanceof TextView) {
            apply((TextView) root);
        } else if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToHierarchy(group.getChildAt(i));
            }
        }
    }

    /**
     * 兼容 API 21+ 的自定义字体 span（框架 TypefaceSpan(Typeface) 构造器需 API 28+）。
     */
    private static final class FontTypefaceSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        FontTypefaceSpan(Typeface typeface) {
            this.typeface = typeface;
        }

        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setTypeface(typeface);
        }

        @Override
        public void updateMeasureState(TextPaint textPaint) {
            textPaint.setTypeface(typeface);
        }
    }
}
