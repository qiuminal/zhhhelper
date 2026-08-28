package com.qiuminal.zhhhelper;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 从 assets/huoma_data.txt 加载全部字数据到内存
 * 数据格式：每行一个字，制表符分隔
 *   字头\t编码\t拆分\t拼音\tUnicode\t字根编码(可选)
 */
public class DataLoader {

    private static final String DATA_FILE = "huoma_data.txt";
    private static Map<String, CharData> dataMap;

    /**
     * 加载数据。每次调用都重新从 assets 读取并重建内存表，
     * 保证界面上的“刷新”按钮能恢复被覆盖/损坏的内存数据。
     */
    public static synchronized void load(Context context) {
        Map<String, CharData> map = new HashMap<>();
        try (InputStream is = context.getAssets().open(DATA_FILE);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                CharData d = CharData.parse(line);
                if (d != null && d.charText != null && !d.charText.isEmpty()) {
                    map.put(d.charText, d);
                }
            }
            dataMap = map;
        } catch (IOException e) {
            e.printStackTrace();
            // 加载失败时保留旧数据或空表，避免崩溃
            if (dataMap == null) {
                dataMap = map;
            }
        }
    }

    /**
     * 查询单个字。
     * 输入可能是整句话或带标点的文本，这里自动提取第一个可作为字头的
     * 汉字字符（跳过空白与标点，兼容全角括号等场景）。
     */
    public static CharData query(String text) {
        if (dataMap == null || text == null) return null;
        String key = extractQueryKey(text);
        if (key == null || key.isEmpty()) return null;
        return dataMap.get(key);
    }

    /**
     * 提取第一个非空白、非标点的字符作为查询键。
     */
    private static String extractQueryKey(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) continue;
            if (!Character.isLetterOrDigit(cp)) continue;
            return new String(Character.toChars(cp));
        }
        return null;
    }

    /**
     * 获取已加载字数
     */
    public static int getCount() {
        return dataMap == null ? 0 : dataMap.size();
    }
}
