package com.qiuminal.zhhhelper

/**
 * 剪贴板载文预处理：删除跟打 QQ 机器人发文的「头部/尾部」标记行，只保留正文，
 * 并从头部提取标题、从尾部提取「段号 / 总段」，供 UI 对应位置展示（比照虎魄 / 晴跟打PRO）。
 *
 * 结构约定：机器人发文为「首行标题/元数据 + 中间正文 + 以 ----- 开头的尾行」。
 * 处理策略：
 *  - 尾行：任意以 2+ 个 ASCII 短横 `-` 开头的整行删除（覆盖各群/各器尾部，含段号非数字如「第groupmatch段」），
 *    并尽量从中解析段号 `第(\d+)段` 与总段 `共(\d+)段`。
 *  - 头行：显式样式（虎魄 `〖发文〗:…`、晴 `[难度]《书名》作者:…`）优先；
 *    若存在尾行但未命中显式头行，则把「首个非空行」视为标题行删除（覆盖「早安三国打工人40-普2.77」
 *    「092…日赛第750期——平凡之路」等纯文本标题），保证正文不被标题/难度/日期等污染。
 *  - 预测/统计行：如「预测速度156.64 … 置信60%」整行删除。
 * 仅用于剪贴板载文，不处理用户自行导入的 TXT。
 */
object ArticleTextCleaner {

    data class Cleaned(
        val body: String,
        val title: String?,
        val segNo: String?,
        val segTotal: String?,
    )

    // 尾行：以 2+ 个 ASCII 短横开头（各跟打器/各群发文尾部统一用 ----- 开头），
    // 并含「段」或跟打器标识，避免误删正文中的 markdown 分隔线 `---`
    private val footerPatterns = listOf(
        Regex("""^[\s\u3000]*-{2,}.*段.*$"""),
        Regex("""^[\s\u3000]*-{2,}.*(?:赛文|练单|免费版|跟打|管理系统|共\s*\d+\s*字).*$"""),
    )

    // 显式头部元数据行
    private val headerPatterns = listOf(
        // 虎魄：〖发文〗: … / 〖难度〗: … / 〖字数〗: …（可带「皇叔 」前缀）
        Regex("""^[\s\u3000]*(?:皇叔\s+)?〖[^〗]+〗\s*[:：].*$"""),
        // 晴：[普(3.42)]《冠军之光》作者：林海听涛 [字数 269]
        Regex("""^[\s\u3000]*\[[^\]]*]\s*《[^》]*》\s*作者\s*[:：].*$"""),
        // 兜底：含《书名》作者… 且带 [字数 n] 的整行
        Regex("""^[\s\u3000]*.*《[^》]*》\s*作者\s*[:：].*\[\s*字数\s*\d+\s*].*$"""),
    )

    // 其它需整行删除的跟打器附加信息行（无标题/段号，直接丢弃）
    private val dropPatterns = listOf(
        // 晴跟打预测行：预测速度156.64 个难普(2.18) 击键6.51 码长2.50 置信60%
        Regex("""^[\s\u3000]*预测速度.*$"""),
        // 兜底：同一行同时含 击键/码长/置信% 的跟打器统计/预测行
        Regex("""^[\s\u3000]*.*击键\s*[\d.]+.*码长\s*[\d.]+.*置信\s*\d+\s*%.*$"""),
    )

    // 段号可为数字或字母数字编码（虎魄 base-N，如 mjwb/kqvi/groupmatch）
    private val segNoRegex = Regex("""第\s*([0-9A-Za-z]+)\s*段""")
    private val segTotalRegex = Regex("""共\s*([0-9A-Za-z]+)\s*段""")
    private val bookTitleRegex = Regex("""《([^》]*)》""")
    private val hupoTitleStripLead = Regex("""^[\s\u3000]*(?:皇叔\s+)?〖[^〗]+〗\s*[:：]\s*""")
    private val hupoTitleStripTail = Regex("""\s*〖[^〗]*〗\s*[:：].*$""")
    // 纯文本标题行尾部的难度标记，如「-普2.77」「-难5.0」
    private val plainTitleDiffTail = Regex("""\s*[-－—–]\s*(?:普|难|易|虐|嗯|捷)\s*[\d.]+\s*$""")

    /** 解析剪贴板文本：返回正文 + 从头/尾提取的标题与段号/总段。 */
    fun parseClipboard(raw: String): Cleaned {
        if (raw.isBlank()) return Cleaned(raw, null, null, null)
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        var title: String? = null
        var segNo: String? = null
        var segTotal: String? = null
        var footerFound = false
        val bodyLines = ArrayList<String>()
        for (line in normalized.split("\n")) {
            val t = line.trim()
            if (t.isNotEmpty() && footerPatterns.any { it.matches(t) }) {
                footerFound = true
                if (segNo == null) segNo = segNoRegex.find(t)?.groupValues?.get(1)
                if (segTotal == null) segTotal = segTotalRegex.find(t)?.groupValues?.get(1)
                continue
            }
            if (t.isNotEmpty() && headerPatterns.any { it.matches(t) }) {
                if (title == null) title = extractTitle(t)
                continue
            }
            if (t.isNotEmpty() && dropPatterns.any { it.matches(t) }) {
                continue
            }
            bodyLines.add(line)
        }
        // 有尾行但未命中显式头部 → 首个非空行按标题处理（保证其后仍有正文，避免吃掉唯一内容行）
        if (footerFound && title == null) {
            val idx = bodyLines.indexOfFirst { it.trim().isNotEmpty() }
            if (idx >= 0 && bodyLines.drop(idx + 1).any { it.trim().isNotEmpty() }) {
                title = plainTitleDiffTail.replace(bodyLines[idx].trim(), "").trim()
                bodyLines.removeAt(idx)
            }
        }
        val body = bodyLines.joinToString("\n").trim('\n', ' ', '\t', '\r', '\u3000')
        return Cleaned(body, title?.takeIf { it.isNotEmpty() }, segNo, segTotal)
    }

    /** 兼容旧用法：只取正文。 */
    fun cleanClipboard(raw: String): String = parseClipboard(raw).body

    private fun extractTitle(headerLine: String): String {
        // 晴：优先取《书名》
        bookTitleRegex.find(headerLine)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotEmpty()) return name
        }
        // 虎魄：去掉 〖发文〗: 前缀与 〖难度/字数〗: 后缀
        var s = hupoTitleStripLead.replace(headerLine, "")
        s = hupoTitleStripTail.replace(s, "")
        return s.trim()
    }
}
