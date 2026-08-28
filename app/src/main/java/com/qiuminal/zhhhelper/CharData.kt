package com.qiuminal.zhhhelper

/**
 * 单字数据模型，由三个码表按「字头」主键合并：
 *   zi.txt    -> charText, codes（编码）
 *   chai.txt  -> rootCodes（拆分第1行/第2列），components（拆分第2行/第3列），
 *                 pinyin（第4列），unicode（第5+6列拼接）
 *   zheng.txt -> zhengCode（整句码）
 */
class CharData {
    var charText: String? = null      // 字头（主键）
    var codes: String? = null         // 编码（zi.txt）
    var rootCodes: String? = null     // 拆分第1行（chai.txt 第2列）
    var components: String? = null    // 拆分第2行（chai.txt 第3列）
    var pinyin: String? = null        // 拼音（chai.txt 第4列）
    var unicode: String? = null       // U码（chai.txt 第5+6列拼接）
    var zhengCode: String? = null     // 整句码（zheng.txt）
}
