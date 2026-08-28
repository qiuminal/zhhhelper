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
 * 从 assets 加载三个码表并合并为内存数据，主键均为「字头」：
 *   zi.txt    字头 + 编码
 *   chai.txt  拆分（两行）+ 拼音 + U码
 *   zheng.txt 整句码
 */
public class DataLoader {

    private static final String FILE_ZI = "zi.txt";
    private static final String FILE_CHAI = "chai.txt";
    private static final String FILE_ZHENG = "zheng.txt";

    private static Map<String, CharData> dataMap;

    /**
     * 加载数据。每次调用都重新从 assets 读取并重建内存表。
     */
    public static synchronized void load(Context context) {
        Map<String, CharData> map = new HashMap<>();

        // 1) zi.txt：字头 + 编码
        try {
            readLines(context, FILE_ZI, line -> {
                int idx = line.indexOf('\t');
                if (idx <= 0) return;
                String key = line.substring(0, idx);
                CharData d = new CharData();
                d.charText = key;
                d.codes = line.substring(idx + 1).trim();
                map.put(key, d);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2) chai.txt：拆分两行 + 拼音 + U码
        try {
            readLines(context, FILE_CHAI, line -> {
                String[] parts = line.split("\t", -1);
                if (parts.length < 6) return;
                String key = stripBom(parts[0]).trim();
                if (key.isEmpty()) return;
                CharData d = map.get(key);
                if (d == null) {
                    d = new CharData();
                    d.charText = key;
                    map.put(key, d);
                }
                d.rootCodes = parts[1].trim();            // 拆分第1行
                d.components = parts[2].trim();           // 拆分第2行
                d.pinyin = parts[3].trim();               // 拼音
                d.unicode = concat(parts[4].trim(), parts[5].trim()); // U码（第5+6列拼接）
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 3) zheng.txt：整句码
        try {
            readLines(context, FILE_ZHENG, line -> {
                int idx = line.indexOf('\t');
                if (idx <= 0) return;
                String key = line.substring(0, idx);
                CharData d = map.get(key);
                if (d == null) {
                    d = new CharData();
                    d.charText = key;
                    map.put(key, d);
                }
                d.zhengCode = line.substring(idx + 1).trim();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        dataMap = map;
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

    private interface LineHandler {
        void handle(String line);
    }

    private static void readLines(Context context, String asset, LineHandler handler) throws IOException {
        try (InputStream is = context.getAssets().open(asset);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handler.handle(line);
            }
        }
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static String concat(String a, String b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " " + b;
    }
}
