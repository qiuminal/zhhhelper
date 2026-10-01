package com.qiuminal.zhhhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleTextCleanerTest {

    private val body1 =
        "来。“舅丈，小心！这波动似是随机传送！”穆山只来得及将一只手搭在荆雨的肩膀上，随机一阵波动扫过。"
    private val body2 =
        "黛玻菈摇头，然后起身。“就到此为止吧，施策尔先生。”这是要宣布谈判破裂，然后走人了。"

    @Test
    fun strips_hupo_header_and_footer() {
        val raw = "〖发文〗: 开局长生，苟在下界吃土飞升 〖难度〗: 普 4.28\n" +
            body1 + "\n" +
            "-----第1105段-共18382段 进度132480/2205776字 本段120字 虎魄免费版 QQ604836383"
        assertEquals(body1, ArticleTextCleaner.cleanClipboard(raw))
    }

    @Test
    fun parses_hupo_title_and_segment() {
        val raw = "〖发文〗: 开局长生，苟在下界吃土飞升 〖难度〗: 普 4.28\n" +
            body1 + "\n" +
            "-----第1105段-共18382段 进度132480/2205776字 本段120字 虎魄免费版 QQ604836383"
        val c = ArticleTextCleaner.parseClipboard(raw)
        assertEquals(body1, c.body)
        assertEquals("开局长生，苟在下界吃土飞升", c.title)
        assertEquals("1105", c.segNo)
        assertEquals("18382", c.segTotal)
    }

    @Test
    fun parses_hupo_alnum_segment_id() {
        val raw = "〖发文〗: 〖普通〗穆斯林的葬礼-霍达-27 〖难度〗: 普 4.40\n" +
            body1 + "\n" +
            "-----第mjwb段-sha1(fac2cc18)-本段100字-虎魄至尊版v1.3.5"
        val c = ArticleTextCleaner.parseClipboard(raw)
        assertEquals(body1, c.body)
        assertEquals("〖普通〗穆斯林的葬礼-霍达-27", c.title)
        assertEquals("mjwb", c.segNo)
        assertEquals(null, c.segTotal)
    }

    @Test
    fun parses_sunny_title_and_segment() {
        val raw = "[普(3.42)]《冠军之光》作者：林海听涛 [字数 269]\n" +
            body2 + "\n" +
            "-----第777段-----晴赛文"
        val c = ArticleTextCleaner.parseClipboard(raw)
        assertEquals(body2, c.body)
        assertEquals("冠军之光", c.title)
        assertEquals("777", c.segNo)
        assertEquals(null, c.segTotal)
    }

    @Test
    fun plain_body_has_no_title_or_segment() {
        val c = ArticleTextCleaner.parseClipboard("第一段正文。\n第二段正文。")
        assertEquals("第一段正文。\n第二段正文。", c.body)
        assertEquals(null, c.title)
        assertEquals(null, c.segNo)
        assertEquals(null, c.segTotal)
    }

    @Test
    fun strips_sunny_header_and_footer() {
        val raw = "[普(3.42)]《冠军之光》作者：林海听涛 [字数 269]\n" +
            body2 + "\n" +
            "-----第777段-----晴赛文"
        assertEquals(body2, ArticleTextCleaner.cleanClipboard(raw))
    }

    @Test
    fun keeps_plain_body_untouched() {
        val raw = "第一段正文。\n第二段正文。"
        assertEquals(raw, ArticleTextCleaner.cleanClipboard(raw))
    }

    @Test
    fun handles_crlf_and_trailing_blank_lines() {
        val raw = "〖发文〗: 标题 〖难度〗: 普 1.00\r\n" + body1 + "\r\n-----第3段-----晴练单\r\n\r\n"
        assertEquals(body1, ArticleTextCleaner.cleanClipboard(raw))
    }

    @Test
    fun body_sentence_with_dou_number_is_not_dropped() {
        // 正文中出现「第1段」字样但非破折号开头的尾标记行，不应被删除
        val raw = "他翻到第1段开始读。\n继续读下去。"
        assertEquals(raw, ArticleTextCleaner.cleanClipboard(raw))
    }

    @Test
    fun blank_input_returns_as_is() {
        assertEquals("", ArticleTextCleaner.cleanClipboard(""))
    }

    @Test
    fun drops_sunny_prediction_line_after_footer() {
        val raw = "[普(1.90)]《风流女儿国》作者：九月寒风 [字数199]\n" +
            body2 + "\n" +
            "-----第476153826段-晴发文\n" +
            "预测速度156.64 个难普(2.18) 击键6.51 码长2.50 置信60%"
        val c = ArticleTextCleaner.parseClipboard(raw)
        assertEquals(body2, c.body)
        assertEquals("风流女儿国", c.title)
        assertEquals("476153826", c.segNo)
        assertEquals(null, c.segTotal)
    }

    @Test
    fun plain_title_header_with_difficulty_suffix() {
        val raw = "早安三国打工人40-普2.77\n" + body1 + "\n-----第17266段-共100字-陆仟"
        val c = ArticleTextCleaner.parseClipboard(raw)
        assertEquals(body1, c.body)
        assertEquals("早安三国打工人40", c.title)
        assertEquals("17266", c.segNo)
        assertEquals(null, c.segTotal)
    }

    @Test
    fun plain_title_group_daily_contest() {
        val raw = "092五笔正规闲聊群日赛第750期——平凡之路\n" + body1 + "\n" +
            "-----第999段-共252字-EJ3s-小极赛文管理系统"
        val c = ArticleTextCleaner.parseClipboard(raw)
        assertEquals(body1, c.body)
        assertEquals("092五笔正规闲聊群日赛第750期——平凡之路", c.title)
        assertEquals("999", c.segNo)
        assertEquals(null, c.segTotal)
    }

    @Test
    fun footer_with_nonnumeric_segment_is_dropped() {
        val raw = "092五笔正规闲聊群④-2026.10.01\n" + body1 + "\n" +
            "-----第groupmatch段-虎魄跟打器：604836383共270字"
        val c = ArticleTextCleaner.parseClipboard(raw)
        assertEquals(body1, c.body)
        assertEquals("092五笔正规闲聊群④-2026.10.01", c.title)
        assertEquals("groupmatch", c.segNo)
        assertEquals(null, c.segTotal)
    }
}
