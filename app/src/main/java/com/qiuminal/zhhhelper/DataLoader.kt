package com.qiuminal.zhhhelper

import android.content.Context
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.HashMap

/**
 * 从 assets 加载三个码表并合并为内存数据，主键均为「字头」：
 *   zi.txt    字头 + 编码
 *   chai.txt  拆分（两行）+ 拼音 + U码（区块 + 码点两列）
 *   zheng.txt 整句码
 */
object DataLoader {

    private const val FILE_ZI = "zi.txt"
    private const val FILE_CHAI = "chai.txt"
    private const val FILE_ZHENG = "zheng.txt"

    private var dataMap: MutableMap<String, CharData>? = null

    /**
     * 加载数据。每次调用都重新从 assets 读取并重建内存表。
     */
    @Synchronized
    fun load(context: Context) {
        val map = HashMap<String, CharData>()

        // 1) zi.txt：字头 + 编码
        try {
            readLines(context, FILE_ZI) { line ->
                val idx = line.indexOf('\t')
                if (idx <= 0) return@readLines
                val key = line.substring(0, idx)
                val d = CharData()
                d.charText = key
                d.codes = line.substring(idx + 1).trim()
                map[key] = d
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 2) chai.txt：拆分两行 + 拼音 + U码
        try {
            readLines(context, FILE_CHAI) { line ->
                val parts = line.split('\t')
                if (parts.size < 6) return@readLines
                val key = stripBom(parts[0]).trim()
                if (key.isEmpty()) return@readLines
                val d = map[key] ?: CharData().also { newChar ->
                    newChar.charText = key
                    map[key] = newChar
                }
                d.rootCodes = parts[1].trim()            // 拆分第1行
                d.components = parts[2].trim()           // 拆分第2行
                d.pinyin = parts[3].trim()               // 拼音
                d.unicodeBlock = parts[4].trim() // U码区块（第5列，如 CJK）
                d.unicodeCode = parts[5].trim()  // Unicode 码点（第6列，如 U+7684）
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 3) zheng.txt：整句码
        try {
            readLines(context, FILE_ZHENG) { line ->
                val idx = line.indexOf('\t')
                if (idx <= 0) return@readLines
                val key = line.substring(0, idx)
                val d = map[key] ?: CharData().also { newChar ->
                    newChar.charText = key
                    map[key] = newChar
                }
                d.zhengCode = line.substring(idx + 1).trim()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        dataMap = map
    }

    /**
     * 查询单个字。
     * 输入可能是整句话或带标点的文本，这里自动提取第一个可作为字头的
     * 汉字字符（跳过空白与标点，兼容全角括号等场景）。
     */
    fun query(text: String?): CharData? {
        val map = dataMap ?: return null
        if (text == null) return null
        val key = extractQueryKey(text)
        if (key == null || key.isEmpty()) return null
        return map[key]
    }

    /**
     * 获取已加载字数
     */
    fun getCount(): Int = dataMap?.size ?: 0

    /**
     * 提取第一个非空白、非标点的字符作为查询键。
     */
    private fun extractQueryKey(text: String): String? {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp)) continue
            if (!Character.isLetterOrDigit(cp)) continue
            return String(Character.toChars(cp))
        }
        return null
    }

    private fun readLines(context: Context, asset: String, handler: (String) -> Unit) {
        context.assets.open(asset).bufferedReader(StandardCharsets.UTF_8).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                handler(line)
                line = reader.readLine()
            }
        }
    }

    private fun stripBom(s: String): String =
        if (s.isNotEmpty() && s[0] == '\uFEFF') s.substring(1) else s

}
