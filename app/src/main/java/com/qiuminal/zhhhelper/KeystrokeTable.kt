package com.qiuminal.zhhhelper

import android.content.Context
import java.nio.ByteBuffer
import java.util.zip.Adler32

/**
 * 从 assets/keystrokes.bin 加载 Rime 虎码词典的单字最短编码
 * （由 Gradle 任务 generateKeystrokesBin 编译 data/tiger.dict.yaml 而来），
 * 供练单「击键」统计与「键准」逐键比对使用：每个字取最短编码（简码）。
 *
 * 二进制格式（大端序，见 app/build.gradle generateKeystrokesBin 注释）：
 *   Header(20B): magic "ZHKY" | version u32=2 | charCount u32
 *                | dataOffset u32(=20) | checksum u32(Adler32 over data)
 *   Data: charCount * 9B，按 (keyU16_1, keyU16_2) 升序：
 *     keyU16_1 u16 | keyU16_2 u16(0=BMP) | codeLen u8 | code[4]（a-z，不足补 0）
 *
 * 与 DataLoader 一致：不做 HashMap，直接在字节缓冲上二分查找，内存占用小。
 *
 * 注意：索引按 UTF-16 码元建键，当前仅服务练单（六套内置练习文本均不含
 * 扩展区汉字，按码元/码点计键结果一致）。若未来加入扩展区字根/字集，
 * 需与 DataLoader 一样改为按 codePoint 遍历建键，避免每字按两个码元重复计键。
 */
object KeystrokeTable {

    private const val FILE_BIN = "keystrokes.bin"
    private const val MAGIC = 0x5A484B59 // "ZHKY"
    private const val VERSION = 2
    private const val ENTRY_SIZE = 9

    @Volatile
    private var buf: ByteBuffer? = null
    private var count = 0
    private var dataOffset = 0

    /** 加载击键码表（幂等，校验 magic/version/checksum，失败则保持空数据）。 */
    @Synchronized
    fun load(context: Context) {
        try {
            val bytes = context.assets.open(FILE_BIN).use { it.readBytes() }
            val b = ByteBuffer.wrap(bytes) // 默认大端
            if (b.int != MAGIC) return
            if (b.int != VERSION) return
            count = b.int
            dataOffset = b.int
            val expected = b.int.toLong() and 0xFFFFFFFFL
            val crc = Adler32()
            crc.update(bytes, dataOffset, bytes.size - dataOffset)
            if (crc.value != expected) return
            buf = b
        } catch (e: Exception) {
            // 非关键路径失败：静默降级，避免打扰用户
        }
    }

    fun isLoaded(): Boolean = buf != null

    /** 单字最短编码（字母串）；词典未收录返回 null。 */
    fun shortestCode(ch: Char): String? {
        val b = buf ?: return null
        val c1 = ch.code
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val pos = dataOffset + mid * ENTRY_SIZE
            val k1 = b.getShort(pos).toInt() and 0xFFFF
            val k2 = b.getShort(pos + 2).toInt() and 0xFFFF
            val cmp = compareKey(c1, k1, 0, k2)
            if (cmp < 0) {
                hi = mid - 1
            } else if (cmp > 0) {
                lo = mid + 1
            } else {
                val len = b.get(pos + 4).toInt() and 0xFF
                val sb = StringBuilder(len)
                for (i in 0 until len) {
                    sb.append(b.get(pos + 5 + i).toInt().toChar())
                }
                return sb.toString()
            }
        }
        return null
    }

    /** 单字最短编码长度；词典未收录返回 null。 */
    fun minCodeLen(ch: Char): Int? = shortestCode(ch)?.length

    private fun compareKey(a1: Int, b1: Int, a2: Int, b2: Int): Int = when {
        a1 < b1 || (a1 == b1 && a2 < b2) -> -1
        a1 > b1 || (a1 == b1 && a2 > b2) -> 1
        else -> 0
    }
}
