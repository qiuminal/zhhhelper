package com.qiuminal.zhhhelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 虎助手 - 形码编码与拆字查询主页面
 * UI：顶部标题栏 + 渐变搜索栏 + 搜索框 + 结果卡片
 */
class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var btnFontMinus: ImageButton
    private lateinit var btnFontPlus: ImageButton

    private lateinit var resultContainer: View      // 结果区整体（标题+卡片）
    private lateinit var tvChar: TextView           // 字头
    private lateinit var tvCodes: TextView          // 编码
    private lateinit var tvRootCodes: TextView      // 字根编码（拆分上方小字）
    private lateinit var tvComponents: TextView     // 拆分部件
    private lateinit var tvPinyin: TextView         // 拼音
    private lateinit var tvUnicode: TextView        // U码
    private lateinit var rowZheng: View             // 整句码整行
    private lateinit var tvZhengCode: TextView      // 整句码
    private lateinit var btnZitong: TextView        // 字统链接
    private lateinit var btnYedian: TextView        // 叶典链接

    private var currentData: CharData? = null       // 当前查询结果，用于字统/叶典跳转

    private var currentFontSp = 18f                 // 结果卡片正文字号

    companion object {
        private const val MIN_FONT = 14f
        private const val MAX_FONT = 28f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化全局内置字体（TumanPUA → 霞鹜文楷 → 遍黑体 P1 → P2）
        AppFonts.init(this)

        // 加载内置数据库
        DataLoader.load(this)

        initViews()
        setupListeners()

        // 全局应用内置字体（标题、静态标签、搜索框提示等）
        AppFonts.applyToHierarchy(findViewById(android.R.id.content))
    }

    private fun initViews() {
        etSearch = findViewById(R.id.et_search)
        btnClear = findViewById(R.id.btn_clear)
        btnFontMinus = findViewById(R.id.btn_font_minus)
        btnFontPlus = findViewById(R.id.btn_font_plus)

        resultContainer = findViewById(R.id.result_container)
        tvChar = findViewById(R.id.tv_char)
        tvCodes = findViewById(R.id.tv_codes)
        tvRootCodes = findViewById(R.id.tv_root_codes)
        tvComponents = findViewById(R.id.tv_components)
        tvPinyin = findViewById(R.id.tv_pinyin)
        tvUnicode = findViewById(R.id.tv_unicode)
        rowZheng = findViewById(R.id.row_zheng)
        tvZhengCode = findViewById(R.id.tv_zheng_code)
        btnZitong = findViewById(R.id.btn_zitong)
        btnYedian = findViewById(R.id.btn_yedian)

        // 初始隐藏结果区
        resultContainer.visibility = View.GONE
        btnClear.visibility = View.GONE
    }

    private fun setupListeners() {
        // 搜索框实时查询
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString().trim()
                btnClear.visibility = if (input.isEmpty()) View.GONE else View.VISIBLE
                if (input.isEmpty()) {
                    resultContainer.visibility = View.GONE
                } else {
                    doQuery(input)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // 清除按钮
        btnClear.setOnClickListener { etSearch.setText("") }

        // 字体减小
        btnFontMinus.setOnClickListener {
            if (currentFontSp > MIN_FONT) {
                currentFontSp -= 2f
                applyFontSize()
            }
        }

        // 字体增大
        btnFontPlus.setOnClickListener {
            if (currentFontSp < MAX_FONT) {
                currentFontSp += 2f
                applyFontSize()
            }
        }

        // 字统网：https://zi.tools/?secondary=zi&word=<字>
        btnZitong.setOnClickListener { openExternalLink("https://zi.tools/?secondary=zi&word=") }

        // 叶典：https://www.yedict.com/index.asp?word=<字>
        btnYedian.setOnClickListener { openExternalLink("https://www.yedict.com/index.asp?word=") }
    }

    /**
     * 用系统浏览器打开字统/叶典查询当前字
     */
    private fun openExternalLink(baseUrl: String) {
        val charText = currentData?.charText
        if (charText.isNullOrEmpty()) {
            return
        }
        try {
            val uri = Uri.parse(baseUrl + Uri.encode(charText))
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 执行查询
     */
    private fun doQuery(input: String) {
        val data = DataLoader.query(input)
        if (data == null) {
            // 未查到，隐藏结果区
            currentData = null
            resultContainer.visibility = View.GONE
            return
        }
        currentData = data
        bindData(data)
        resultContainer.visibility = View.VISIBLE
    }

    /**
     * 绑定数据到卡片
     */
    private fun bindData(d: CharData) {
        tvChar.setText(d.charText)
        tvCodes.setText(d.codes ?: "")

        // 字根编码（拆分上方小字），没有则隐藏
        if (!d.rootCodes.isNullOrEmpty()) {
            tvRootCodes.visibility = View.VISIBLE
            tvRootCodes.setText(d.rootCodes)
        } else {
            tvRootCodes.visibility = View.GONE
        }

        tvComponents.setText(d.components ?: "")
        tvPinyin.setText(if (d.pinyin != null) "(${d.pinyin})" else "")
        tvUnicode.setText(if (d.unicode != null) "〔${d.unicode}〕" else "")

        // 整句码（zheng.txt），没有则隐藏整行
        if (!d.zhengCode.isNullOrEmpty()) {
            rowZheng.visibility = View.VISIBLE
            tvZhengCode.setText(d.zhengCode)
        } else {
            rowZheng.visibility = View.GONE
        }

        applyFontSize()

        // 结果文本设置完成后，重新应用字符级 fallback 字体
        AppFonts.applyToHierarchy(resultContainer)
    }

    /**
     * 应用字号到结果卡片正文
     */
    private fun applyFontSize() {
        tvChar.setTextSize(currentFontSp + 4f)
        tvCodes.setTextSize(currentFontSp)
        tvComponents.setTextSize(currentFontSp)
        tvPinyin.setTextSize(currentFontSp)
        tvUnicode.setTextSize(currentFontSp - 2f)
        tvZhengCode.setTextSize(currentFontSp)
        tvRootCodes.setTextSize(currentFontSp - 4f)
    }
}
