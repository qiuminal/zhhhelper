package com.qiuminal.zhhhelper;

/**
 * 单字数据模型，由三个码表按「字头」主键合并：
 *   zi.txt    -> charText, codes（编码）
 *   chai.txt  -> rootCodes（拆分第1行/第2列），components（拆分第2行/第3列），
 *                 pinyin（第4列），unicode（第5+6列拼接）
 *   zheng.txt -> zhengCode（整句码）
 */
public class CharData {
    public String charText;      // 字头（主键）
    public String codes;         // 编码（zi.txt），多个用空格分隔，如 "zhh zh"
    public String rootCodes;     // 拆分第1行（chai.txt 第2列）
    public String components;    // 拆分第2行（chai.txt 第3列）
    public String pinyin;        // 拼音（chai.txt 第4列）
    public String unicode;       // U码（chai.txt 第5+6列拼接），如 "CJK U+864E"
    public String zhengCode;     // 整句码（zheng.txt）
}
