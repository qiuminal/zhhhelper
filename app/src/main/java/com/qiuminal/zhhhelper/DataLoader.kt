package com.qiuminal.zhhhelper

import android.content.Context
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.Adler32

/**
 * 从 assets/tables.bin 加载码表二进制字典（由 Gradle 任务 generateTablesBin
 * 把 data/ 下三个原始 txt 码表编译而来），按字头 UTF-16 码元二分查找。
 *
 * 二进制格式（大端序，见 app/build.gradle generateTablesBin 注释）：
 *   Header(24B): magic "ZHHT" | version u32 | recordCount u32
 *                | indexOffset u32 | valuesOffset u32 | checksum u32(Adler32)
 *   Index: recordCount * 12B，按 (keyU16_1, keyU16_2) 升序
 *   Values: 每记录 7 字段（codes/rootCodes/components/pinyin/block/code/zheng）
 *
 * 无需构建 HashMap，内存占用小、启动快、数据不可直接编辑。
 */
object DataLoader {

    private const val FILE_BIN = "tables.bin"
    private const val MAGIC = 0x5A484854 // "ZHHT"

    @Volatile
    private var buf: ByteBuffer? = null
    private var count = 0
    private var indexOffset = 0
    private var valuesOffset = 0

    /**
     * 加载二进制码表（幂等，校验 magic/version/checksum，失败则保持空数据）。
     */
    @Synchronized
    fun load(context: Context) {
        try {
            val bytes = context.assets.open(FILE_BIN).use { it.readBytes() }
            val b = ByteBuffer.wrap(bytes) // 默认大端
            if (b.int != MAGIC) return
            val version = b.int
            if (version != 1) return
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

    /**
     * 多字查询：按输入顺序返回每个查到的字的卡片数据。
     * 只跳过空白；是否收录完全以码表为准——
     * 〇(U+3007) 等 Unicode 类别非 Letter/Digit 的汉字此前会被
     * isLetterOrDigit 误过滤导致查不到，故不再按类别过滤。
     */
    fun queryAll(text: String?): List<CharData> {
        val b = buf ?: return emptyList()
        if (text.isNullOrEmpty()) return emptyList()
        val result = ArrayList<CharData>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp)) continue
            val key = String(Character.toChars(cp))
            val d = lookup(b, key) ?: continue
            result.add(d)
        }
        return result
    }

    /** 查询单个字（兼容旧接口）。 */
    fun query(text: String?): CharData? = queryAll(text).firstOrNull()

    /** 已收录字数。 */
    fun getCount(): Int = count

    /** 在索引中二分查找单个字。 */
    private fun lookup(b: ByteBuffer, key: String): CharData? {
        val c1 = key[0].code
        val c2 = if (key.length > 1) key[1].code else 0
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val pos = indexOffset + mid * 12
            val k1 = b.getShort(pos).toInt() and 0xFFFF
            val k2 = b.getShort(pos + 2).toInt() and 0xFFFF
            val cmp = compareKey(c1, k1, c2, k2)
            if (cmp < 0) {
                hi = mid - 1
            } else if (cmp > 0) {
                lo = mid + 1
            } else {
                val off = valuesOffset + b.getInt(pos + 4)
                val len = b.getInt(pos + 8)
                return readRecord(b, off, len, key)
            }
        }
        return null
    }

    /** 按 UTF-16 码元比较两键。 */
    private fun compareKey(a1: Int, b1: Int, a2: Int, b2: Int): Int = when {
        a1 < b1 || (a1 == b1 && a2 < b2) -> -1
        a1 > b1 || (a1 == b1 && a2 > b2) -> 1
        else -> 0
    }

    /** 读取一条记录：7 个长度前缀字段。 */
    private fun readRecord(b: ByteBuffer, off: Int, len: Int, key: String): CharData {
        val d = CharData()
        d.charText = key
        var p = off
        val end = off + len
        var field = 0
        while (p < end && field < 7) {
            val fLen = b.getShort(p).toInt() and 0xFFFF
            p += 2
            val s = String(b.array(), p, fLen, StandardCharsets.UTF_8)
            p += fLen
            when (field) {
                0 -> d.codes = s
                1 -> d.rootCodes = s
                2 -> d.components = s
                3 -> d.pinyin = s
                4 -> d.unicodeBlock = s
                5 -> d.unicodeCode = s
                6 -> d.zhengCode = s
            }
            field++
        }
        return d
    }
}
