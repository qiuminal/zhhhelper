package com.qiuminal.zhhhelper;

/**
 * 单字数据模型
 * 对应 assets/huoma_data.txt 中一行记录
 */
public class CharData {
    public String charText;      // 字头，如 "国"
    public String codes;         // 编码列表，空格分隔，如 "rni rn rnid"
    public String components;    // 拆分部件，空格分隔，如 "囗 王 丶"
    public String pinyin;        // 拼音，如 "guó"
    public String unicode;       // Unicode，如 "U+56FD"
    public String rootCodes;     // 字根编码，可选，如 "Rk Nw Id"

    public CharData() {
    }

    /**
     * 从一行文本解析，制表符分隔
     * 格式：字头\t编码\t拆分\t拼音\tUnicode\t字根编码(可选)
     */
    public static CharData parse(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        String[] parts = line.split("\t", -1);
        if (parts.length < 2) return null;

        CharData d = new CharData();
        d.charText = parts[0].trim();
        d.codes = parts.length > 1 ? parts[1].trim() : "";
        d.components = parts.length > 2 ? parts[2].trim() : "";
        d.pinyin = parts.length > 3 ? parts[3].trim() : "";
        d.unicode = parts.length > 4 ? parts[4].trim() : "";
        d.rootCodes = parts.length > 5 ? parts[5].trim() : "";
        return d;
    }
}
