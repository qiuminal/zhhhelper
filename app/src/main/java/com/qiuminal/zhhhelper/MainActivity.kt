package com.qiuminal.zhhhelper

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.io.File
import java.io.FileOutputStream

/**
 * 虎助手 - 形码编码与拆字查询主页面
 * UI：顶部标题栏 + 渐变搜索栏 + 搜索框 + 结果卡片
 */
class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var btnFontMinus: ImageButton
    private lateinit var btnFontPlus: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

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
    private lateinit var btnHadian: TextView        // 汉典链接

    private var currentData: CharData? = null       // 当前查询结果，用于字统/汉典跳转

    private var currentFontSp = 18f                 // 结果卡片正文字号

    companion object {
        private const val MIN_FONT = 14f
        private const val MAX_FONT = 28f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()

        // 码表与字体在后台线程加载，避免启动白屏（约 59MB 字体 + 6MB 码表）；
        // 加载完成后回到主线程应用全局字体，并重跑当前查询。
        Thread {
            DataLoader.load(applicationContext)
            AppFonts.load(applicationContext)
            runOnUiThread {
                AppFonts.applyToHierarchy(findViewById(android.R.id.content))
                val current = etSearch.text?.toString().orEmpty().trim()
                if (current.isNotEmpty()) {
                    doQuery(current)
                }
            }
        }.start()
    }

    private fun initViews() {
        etSearch = findViewById(R.id.et_search)
        btnClear = findViewById(R.id.btn_clear)
        btnFontMinus = findViewById(R.id.btn_font_minus)
        btnFontPlus = findViewById(R.id.btn_font_plus)
        btnShare = findViewById(R.id.btn_share)
        btnMenu = findViewById(R.id.btn_menu)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.navigation_view)

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
        btnHadian = findViewById(R.id.btn_hadian)

        // 初始隐藏结果区
        resultContainer.visibility = View.GONE
        btnClear.visibility = View.GONE
    }

    private fun setupListeners() {
        // 三横杠：打开左侧菜单
        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        // 侧滑菜单：首页 / 关于
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_about -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                else -> false
            }
        }

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

            override fun afterTextChanged(s: Editable?) {
                // 输入实时套用内置字体（生僻字/扩展区字在搜索框也能正常显示）
                if (AppFonts.isLoaded() && s != null && s.isNotEmpty()) {
                    AppFonts.styleInPlace(s)
                }
            }
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

        // 分享：生成结果卡片图片并调起系统分享
        btnShare.setOnClickListener { shareResultCard() }

        // 字统：https://zi.tools/zi/<字>
        btnZitong.setOnClickListener { openExternalLink("https://zi.tools/zi/") }

        // 汉典：https://zdic.net/hans/<字>
        btnHadian.setOnClickListener { openExternalLink("https://zdic.net/hans/") }
    }

    /**
     * 用系统浏览器打开字统/汉典查询当前字
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
        tvPinyin.setText(formatPinyin(d.pinyin))
        tvUnicode.setText(formatUnicode(d.unicodeBlock, d.unicodeCode))

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
     * 拼音：没有则显示「无」，有则带括号展示
     */
    private fun formatPinyin(pinyin: String?): String =
        if (pinyin.isNullOrEmpty()) "无" else "($pinyin)"

    /**
     * U码：〔〕只括码点字段，再与区块拼接，如「CJK 〔U+7684〕」
     */
    private fun formatUnicode(block: String?, code: String?): String {
        val b = block.orEmpty()
        val c = code.orEmpty()
        return when {
            b.isEmpty() && c.isEmpty() -> "无"
            b.isEmpty() -> "〔$c〕"
            c.isEmpty() -> b
            else -> "$b 〔$c〕"
        }
    }

    /**
     * 生成查询结果卡片图片并调起系统分享。
     * 图片与 App 内渲染一致：复用同款卡片样式与内置字体，
     * 不含字统/汉典链接行，也不含分享按钮；右下角加虎助手水印。
     */
    private fun shareResultCard() {
        val d = currentData ?: return
        // 兜底：字体尚未加载完成时补加载（正常情况启动时已后台加载完）
        if (!AppFonts.isLoaded()) {
            AppFonts.load(applicationContext)
        }
        val card = layoutInflater.inflate(R.layout.layout_share_card, null) as LinearLayout

        card.findViewById<TextView>(R.id.tv_share_char).text = d.charText
        card.findViewById<TextView>(R.id.tv_share_codes).text = d.codes ?: ""
        val shareRootCodes = card.findViewById<TextView>(R.id.tv_share_root_codes)
        if (!d.rootCodes.isNullOrEmpty()) {
            shareRootCodes.visibility = View.VISIBLE
            shareRootCodes.text = d.rootCodes
        }
        card.findViewById<TextView>(R.id.tv_share_components).text = d.components ?: ""
        card.findViewById<TextView>(R.id.tv_share_pinyin).text = formatPinyin(d.pinyin)
        card.findViewById<TextView>(R.id.tv_share_unicode).text = formatUnicode(d.unicodeBlock, d.unicodeCode)

        val shareRowZheng = card.findViewById<View>(R.id.row_share_zheng)
        val shareZheng = card.findViewById<TextView>(R.id.tv_share_zheng)
        if (!d.zhengCode.isNullOrEmpty()) {
            shareRowZheng.visibility = View.VISIBLE
            shareZheng.text = d.zhengCode
        }

        // 与主界面一致：按当前字号与字符级 fallback 字体渲染
        applyShareFontSize(card)
        AppFonts.applyToHierarchy(card)

        // 宽度与主界面卡片一致（屏幕宽 - 左右各 20dp），高度自适应
        val targetWidth = resources.displayMetrics.widthPixels - dp(40)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)

        val bitmap = Bitmap.createBitmap(card.measuredWidth, card.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        card.draw(canvas)

        try {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "zhh_share_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.share_title)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 分享卡片按主界面当前字号渲染
     */
    private fun applyShareFontSize(card: LinearLayout) {
        val sp = currentFontSp
        card.findViewById<TextView>(R.id.tv_share_char).setTextSize(sp + 4f)
        card.findViewById<TextView>(R.id.tv_share_codes).setTextSize(sp)
        card.findViewById<TextView>(R.id.tv_share_root_codes).setTextSize(sp - 4f)
        card.findViewById<TextView>(R.id.tv_share_components).setTextSize(sp)
        card.findViewById<TextView>(R.id.tv_share_pinyin).setTextSize(sp)
        card.findViewById<TextView>(R.id.tv_share_unicode).setTextSize(sp - 2f)
        card.findViewById<TextView>(R.id.tv_share_zheng).setTextSize(sp)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
