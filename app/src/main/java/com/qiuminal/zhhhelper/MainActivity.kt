package com.qiuminal.zhhhelper

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.view.View
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray

/**
 * 虎助手 - 拆分查询与跟打练习主页面
 * UI：顶部标题栏 + 渐变搜索栏 + 搜索框 + 多字查询结果卡片列表
 * 多字查询：输入多个字时，自上而下依次输出单字卡片，每张卡片独立分享/链接。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var btnFontMinus: ImageButton
    private lateinit var btnFontPlus: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    private lateinit var resultContainer: View      // 结果区整体（标题+卡片列表）
    private lateinit var cardList: LinearLayout     // 卡片列表（每字一张卡片）

    private lateinit var historyContainer: View     // 历史搜索区（无记录时隐藏）
    private lateinit var btnHistoryEye: ImageButton // 睁眼/闭眼：隐藏或显示历史条目
    private lateinit var btnHistoryClear: ImageButton
    private lateinit var tvHistoryTitle: TextView   // “历史搜索”标题（垃圾桶对齐基准）
    private lateinit var tvNoResult: TextView        // “找不到结果”提示
    private lateinit var historyChips: ChipGroup    // 历史记录标签（自动换行）

    private var currentFontSp = 18f                 // 结果卡片正文字号

    private val historyPrefs by lazy { getSharedPreferences("search_history", Context.MODE_PRIVATE) }
    private val historyHandler by lazy { Handler(Looper.getMainLooper()) }
    private var historyDebounce: Runnable? = null   // 停止输入后延迟写入历史
    private var historySnapshot = ""                // 最近一次历史结算时的搜索框全文
    private var historyExpanded = false             // 历史区是否展开显示全部记录

    companion object {
        private const val MIN_FONT = 14f
        private const val MAX_FONT = 28f
        private const val MAX_HISTORY = 50          // 历史最多保留条数（最近 50 条）
        private const val HISTORY_DEBOUNCE_MS = 900L
        private const val HISTORY_MAX_LABEL = 10    // 单条历史标签最多展示字数（超出加 ...）
        private const val HISTORY_MAX_ROWS = 2      // 默认折叠最多展示行数
        private const val HISTORY_HIDDEN_KEY = "history_hidden"   // 是否隐藏历史条目
        private const val COLOR_HISTORY_BG = 0xFFF2F7FF.toInt()      // 标签底色
        private const val COLOR_HISTORY_STROKE = 0xFFEFEFEF.toInt()  // 标签描边
        private const val COLOR_HISTORY_TEXT = 0xFF5E687A.toInt()    // 标签文字
        private const val COLOR_HISTORY_ICON = 0xFF828A9A.toInt()    // 展开/收起箭头
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        renderHistory()

        // 码表与字体在后台线程加载，避免启动白屏（字体约 59MB + 二进制码表约 6MB）；
        // 加载完成后回到主线程应用全局字体，并重跑当前查询。
        Thread {
            DataLoader.load(applicationContext)
            CharLabels.load(applicationContext)
            AppFonts.load(applicationContext)
            runOnUiThread {
                AppFonts.applyToHierarchy(findViewById(android.R.id.content))
                renderHistory()
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
        btnMenu = findViewById(R.id.btn_menu)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.navigation_view)

        resultContainer = findViewById(R.id.result_container)
        cardList = findViewById(R.id.card_list)

        historyContainer = findViewById(R.id.history_container)
        btnHistoryEye = findViewById(R.id.btn_history_eye)
        btnHistoryClear = findViewById(R.id.btn_history_clear)
        tvHistoryTitle = findViewById(R.id.tv_history_title)
        tvNoResult = findViewById(R.id.tv_no_result)
        historyChips = findViewById(R.id.history_chips)

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
                R.id.nav_practice -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this, PracticeActivity::class.java))
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
                    cardList.removeAllViews()
                    tvNoResult.visibility = View.GONE
                    // 搜索框清空时才展示历史搜索模块
                    renderHistory()
                } else {
                    // 有输入/查询结果时隐藏历史区，避免影响结果阅读
                    historyContainer.visibility = View.GONE
                    doQuery(input)
                }
                // 停止输入约 0.9s 后结算一次历史（只记本段新上屏内容）
                historyDebounce?.let { historyHandler.removeCallbacks(it) }
                val runnable = Runnable { flushHistoryRecord() }
                historyDebounce = runnable
                historyHandler.postDelayed(runnable, HISTORY_DEBOUNCE_MS)
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

        // 垃圾桶：一键清空全部历史
        btnHistoryClear.setOnClickListener { clearHistory() }

        // 睁眼/闭眼：切换历史条目的显示与隐藏
        btnHistoryEye.setOnClickListener { toggleHistoryHidden() }

        // 字体减小
        btnFontMinus.setOnClickListener {
            if (currentFontSp > MIN_FONT) {
                currentFontSp -= 2f
                reapplyFontSize()
            }
        }

        // 字体增大
        btnFontPlus.setOnClickListener {
            if (currentFontSp < MAX_FONT) {
                currentFontSp += 2f
                reapplyFontSize()
            }
        }
    }

    // ---------------- 历史搜索 ----------------

    private fun loadHistory(): MutableList<String> {
        val list = mutableListOf<String>()
        try {
            val raw = historyPrefs.getString("history", null) ?: return list
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                // 码表已加载时，整串查不到任何收录字的旧记录不再展示
                if (s.isNotEmpty() && (!DataLoader.isLoaded() || DataLoader.queryAll(s).isNotEmpty())) {
                    list.add(s)
                }
            }
        } catch (e: Exception) {
            // 历史数据损坏时忽略，按空历史处理
        }
        return list
    }

    private fun saveHistory(list: List<String>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            historyPrefs.edit().putString("history", arr.toString()).apply()
        } catch (e: Exception) {
            // 存储异常不影响查询
        }
    }

    /** 新增一条历史（去重、最新在前、超限裁剪）。 */
    private fun addHistoryEntry(text: String) {
        val entry = text.trim()
        if (entry.isEmpty()) return
        val list = loadHistory()
        if (list.firstOrNull() == entry) return  // 已在最前，无需变动
        val filtered = list.filter { it != entry }.toMutableList()
        filtered.add(0, entry)
        while (filtered.size > MAX_HISTORY) filtered.removeAt(filtered.size - 1)
        saveHistory(filtered)
        renderHistory()
    }

    /** 清空历史并隐藏模块。 */
    private fun clearHistory() {
        historyPrefs.edit().remove("history").apply()
        historyExpanded = false
        renderHistory()
    }

    /** 睁眼/闭眼：切换历史条目的显示与隐藏（隐藏时仅保留标题与闭眼图标）。 */
    private fun toggleHistoryHidden() {
        val hidden = !historyPrefs.getBoolean(HISTORY_HIDDEN_KEY, false)
        historyPrefs.edit().putBoolean(HISTORY_HIDDEN_KEY, hidden).apply()
        if (hidden) {
            historyExpanded = false
        }
        renderHistory()
    }

    /**
     * 停止输入约 0.9s 后结算一次历史：只把「新上屏的一段内容」记为一条独立记录，
     * 而不是把先前已记过的字再拼进来累积（避免 拆/拆分/拆分子 式的冗余历史）。
     *
     * 规则：
     * - 输入法仍在组字（拼音中间态带 composing 标记）时不结算，避免把中间态字母计入基准；
     * - 新上屏的一段若查不到任何收录字则不入历史，但仍把基准推进到当前文本，
     *   保证「先输入无结果内容 x，再追加输入 白」时，白作为独立一次上屏单独记录。
     */
    private fun flushHistoryRecord() {
        historyDebounce = null
        val current = etSearch.text?.toString().orEmpty()
        if (current.isBlank()) {
            historySnapshot = ""
            return
        }
        if (hasComposingText()) {
            return   // 拼音组字中，等真正上屏后再结算
        }
        val delta = when {
            current.startsWith(historySnapshot) -> current.substring(historySnapshot.length)
            historySnapshot.startsWith(current) -> ""   // 文本被删回更早状态：只推进基准
            else -> current                              // 整体被替换/重排：整段视为一次新输入
        }
        // 整段查不到任何收录字（拉丁字母、符号、未收录字符等）不入历史；
        // “收录+未收录”混合的长串仍按现有逻辑保留整条。
        // 无论是否入史都推进基准，避免与后续上屏内容粘连成一条。
        if (delta.isEmpty() || (DataLoader.isLoaded() && DataLoader.queryAll(delta).isEmpty())) {
            historySnapshot = current
            return
        }
        historySnapshot = current
        addHistoryEntry(delta)
    }

    /** 搜索框文本当前是否存在输入法组字（拼音等中间态）。 */
    private fun hasComposingText(): Boolean {
        val editable = etSearch.text
        if (editable !is Spanned) {
            return false
        }
        for (span in editable.getSpans(0, editable.length, Any::class.java)) {
            if ((editable.getSpanFlags(span) and Spanned.SPAN_COMPOSING) != 0) {
                return true
            }
        }
        return false
    }

    /**
     * 将垃圾桶图标按“历史搜索”四个字的字形外接框对齐：
     * 按图标真实墨迹等比缩放至与字形同高，使上下可视边缘贴合字形上下边缘，
     * 并将可视右缘对准标题行右缘（即搜索框右缘）。
     * 全程以像素实测为准，适配不同字体、字号与屏幕密度。
     */
    private fun alignHistoryTrash() {
        try {
            if (historyContainer.visibility != View.VISIBLE || btnHistoryClear.visibility != View.VISIBLE) {
                return
            }
            val icon = btnHistoryClear.drawable ?: return
            val w = icon.intrinsicWidth
            val h = icon.intrinsicHeight
            if (w <= 0 || h <= 0) return

            // 1) 求垃圾桶图标真实墨迹的外接矩形
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            icon.setBounds(0, 0, w, h)
            icon.draw(canvas)
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            bmp.recycle()
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            for (i in pixels.indices) {
                if (pixels[i] ushr 24 >= 16) {
                    val x = i % w
                    val y = i / w
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < 0 || maxY < 0 || maxX <= minX || maxY <= minY) return

            // 2) 求“历史搜索”四个字的外接矩形（相对标题框顶部）
            val glyph = Rect()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.typeface = tvHistoryTitle.typeface
            paint.textSize = tvHistoryTitle.textSize
            paint.getTextBounds(tvHistoryTitle.text.toString(), 0, tvHistoryTitle.text.length, glyph)
            val glyphTop = tvHistoryTitle.top + tvHistoryTitle.baseline + glyph.top
            val glyphBottom = tvHistoryTitle.top + tvHistoryTitle.baseline + glyph.bottom
            val glyphH = glyphBottom - glyphTop
            if (glyphH <= 0) return

            // 3) 缩放到与字形同高：上缘贴合字形上缘，右缘贴合标题行右缘（=搜索框右缘）
            val inkH = (maxY - minY + 1).toFloat()
            val scale = glyphH.toFloat() / inkH
            btnHistoryClear.pivotX = 0f
            btnHistoryClear.pivotY = 0f
            btnHistoryClear.scaleX = scale
            btnHistoryClear.scaleY = scale
            val rowRight = ((btnHistoryClear.parent as? View)?.width) ?: return
            btnHistoryClear.translationX = rowRight - btnHistoryClear.left - (maxX + 1) * scale
            btnHistoryClear.translationY = glyphTop - btnHistoryClear.top - minY * scale
        } catch (e: Exception) {
            // 对齐失败时保持默认位置，避免影响正常展示
        }
    }

    /**
     * 渲染历史标签。
     * 没有记录或搜索框非空时整体隐藏；有记录时默认折叠为 2 行，
     * 超出默认行数时在行末提供圆形展开按钮，展开态在末尾提供收起按钮。
     */
    private fun renderHistory() {
        val list = loadHistory()
        historyChips.removeAllViews()
        if (list.isEmpty() || !etSearch.text.isNullOrEmpty()) {
            historyContainer.visibility = View.GONE
            return
        }
        historyContainer.visibility = View.VISIBLE

        // 睁眼/闭眼状态与垃圾桶联动：隐藏时条目与垃圾桶都不可见
        val hidden = historyPrefs.getBoolean(HISTORY_HIDDEN_KEY, false)
        btnHistoryEye.setImageResource(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
        btnHistoryEye.contentDescription = if (hidden) "显示历史" else "隐藏历史"
        btnHistoryClear.visibility = if (hidden) View.GONE else View.VISIBLE
        if (hidden) {
            historyChips.visibility = View.GONE
            return
        }
        historyChips.visibility = View.VISIBLE

        // 垃圾桶图标按“历史搜索”字形运行时对齐（缩放+平移），并让可视右缘对准搜索框
        btnHistoryClear.post { alignHistoryTrash() }

        val chips = list.map { buildHistoryChip(it) }
        val sizes = chips.map { measureChipSize(it) }
        val widths = sizes.map { it.first }
        // FlowLayout 每行高度取该行最后一个子项的高度：按钮槽位必须与胶囊同高，
        // 否则最后一行胶囊会被裁掉底部；胶囊与圆都统一为 32dp，圆垂直居中在槽位内。
        val toggleSlotHeight = (sizes.maxOf { it.second }).coerceAtLeast(dp(32))
        val available = historyChipsWidth()
        val spacing = dp(8)
        val rows = simulateRows(widths, available, spacing)

        if (rows.size <= HISTORY_MAX_ROWS) {
            // 不足默认行数：不提供展开/收起按钮
            chips.forEach { historyChips.addView(it) }
            return
        }
        if (!historyExpanded) {
            // 折叠：取前 HISTORY_MAX_ROWS 行，行末放不下展开按钮时依次让位
            val keepCount = collapsedChipCount(widths, available, spacing, HISTORY_MAX_ROWS, dp(32))
            for (i in 0 until keepCount) {
                historyChips.addView(chips[i])
            }
            historyChips.addView(buildToggle(expand = true, slotHeightPx = toggleSlotHeight))
        } else {
            // 展开：全部记录 + 末尾收起按钮
            chips.forEach { historyChips.addView(it) }
            historyChips.addView(buildToggle(expand = false, slotHeightPx = toggleSlotHeight))
        }
    }

    /**
     * 构建单条历史胶囊（普通 TextView，不走 Material Chip 的自绘文本管线）；
     * 展示文本超 10 字截断加 ...，点击按完整内容回填查询。
     * 与查询卡片同走 TextView 渲染路径，避免 Cjk 扩展区字回退到遍黑体时
     * ChipDrawable 以单一基础字体度量导致整行下移/裁底。
     */
    private fun buildHistoryChip(word: String): TextView {
        val tv = TextView(this)
        tv.layoutParams = ChipGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(32)
        )
        tv.text = AppFonts.style(historyLabel(word)) ?: historyLabel(word)
        tv.setTextSize(14f)
        tv.setTextColor(COLOR_HISTORY_TEXT)
        tv.includeFontPadding = false
        tv.gravity = Gravity.CENTER
        tv.isClickable = true
        tv.setBackgroundResource(R.drawable.bg_history_capsule)
        tv.setPadding(dp(12), 0, dp(12), 0)
        tv.setOnClickListener {
            etSearch.setText(word)
            etSearch.setSelection(etSearch.text?.length ?: 0)
            doQuery(word)
            addHistoryEntry(word)
        }
        return tv
    }

    /** 单条历史标签文案：超过 10 个字只显示前 10 个字并追加三个英文句点。 */
    private fun historyLabel(word: String): String {
        val sb = StringBuilder()
        var count = 0
        var i = 0
        while (i < word.length && count < HISTORY_MAX_LABEL) {
            val cp = word.codePointAt(i)
            sb.appendCodePoint(cp)
            i += Character.charCount(cp)
            count++
        }
        if (i < word.length) {
            sb.append("...")
        }
        return sb.toString()
    }

    /** 预测量胶囊宽高（未挂载到父布局时也成立）。 */
    private fun measureChipSize(view: View): Pair<Int, Int> = try {
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(spec, spec)
        val w = if (view.measuredWidth > 0) view.measuredWidth else dp(48)
        val h = if (view.measuredHeight > 0) view.measuredHeight else dp(32)
        w to h
    } catch (e: Exception) {
        dp(48) to dp(32)
    }

    /**
     * 正圆形展开/收起按钮（箭头朝下=展开更多，朝上=收起）。
     * 外框槽位与胶囊实测高度同高（保证 FlowLayout 行高不裁剪胶囊），
     * 圆直径为 32dp 并垂直居中，上下缘与胶囊一致。
     */
    private fun buildToggle(expand: Boolean, slotHeightPx: Int): View {
        val slot = FrameLayout(this)
        slot.layoutParams = ChipGroup.LayoutParams(dp(32), slotHeightPx)
        val circle = ImageButton(this)
        circle.layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER)
        circle.background = ContextCompat.getDrawable(this, R.drawable.bg_circle_history_arrow)
        circle.setImageResource(if (expand) R.drawable.ic_expand_more else R.drawable.ic_expand_less)
        circle.setColorFilter(COLOR_HISTORY_ICON)
        circle.scaleType = ImageView.ScaleType.CENTER
        circle.isClickable = false
        circle.isFocusable = false
        slot.addView(circle)
        slot.contentDescription = if (expand) "展开更多历史" else "收起历史"
        slot.setOnClickListener {
            if (expand) {
                historyExpanded = true
                // 展开时让光标离开搜索框并收起输入法，方便查看完整历史
                etSearch.clearFocus()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(etSearch.windowToken, 0)
            } else {
                historyExpanded = false
            }
            renderHistory()
        }
        return slot
    }

    /** 按行宽贪心模拟换行，返回每行可容纳的胶囊个数。 */
    private fun simulateRows(widths: List<Int>, available: Int, spacing: Int): List<Int> {
        val rows = ArrayList<Int>()
        var count = 0
        var used = 0
        for (w in widths) {
            if (count == 0) {
                used = w
                count = 1
            } else if (used + spacing + w <= available) {
                used += spacing + w
                count++
            } else {
                rows.add(count)
                used = w
                count = 1
            }
        }
        if (count > 0) rows.add(count)
        return rows
    }

    /** 前 count 个胶囊贪心排布后，最后一行已占用的宽度。 */
    private fun lastRowUsedWidth(widths: List<Int>, count: Int, available: Int, spacing: Int): Int {
        var used = 0
        var inRow = 0
        for (i in 0 until count) {
            val w = widths[i]
            if (inRow == 0) {
                used = w
                inRow = 1
            } else if (used + spacing + w <= available) {
                used += spacing + w
                inRow++
            } else {
                used = w
                inRow = 1
            }
        }
        return used
    }

    /** 折叠态可见条数：取前 maxRows 行，行末放不下展开按钮时依次让位。 */
    private fun collapsedChipCount(
        widths: List<Int>,
        available: Int,
        spacing: Int,
        maxRows: Int,
        toggleWidth: Int
    ): Int {
        // 留 1dp 余量，避免与 ChipGroup 实测换行差 1px 导致按钮落到第 4 行
        val effective = available - dp(1)
        var count = simulateRows(widths, effective, spacing).take(maxRows).sum()
        while (count > 0) {
            val used = lastRowUsedWidth(widths, count, effective, spacing)
            if (used + spacing + toggleWidth <= effective) break
            count--
        }
        return count
    }

    /** 历史区可用宽度：优先取布局后的实际宽度，未布局时按屏幕宽减去左右边距估算。 */
    private fun historyChipsWidth(): Int {
        val actual = historyChips.width
        return if (actual > 0) actual else resources.displayMetrics.widthPixels - dp(40)
    }

    /**
     * 用系统浏览器打开字统/汉典查询指定字
     */
    private fun openExternalLink(d: CharData, baseUrl: String) {
        val charText = d.charText
        if (charText.isNullOrEmpty()) {
            return
        }
        try {
            val uri = Uri.parse(baseUrl + Uri.encode(charText))
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            // 非关键路径失败：静默降级，避免打扰用户
        }
    }

    /**
     * 执行多字查询：每个查到的字生成一张卡片，自上而下排列。
     */
    private fun doQuery(input: String) {
        val results = DataLoader.queryAll(input)
        cardList.removeAllViews()
        if (results.isEmpty()) {
            // 未查到：隐藏结果区，在搜索框下方提示“找不到结果”
            resultContainer.visibility = View.GONE
            tvNoResult.visibility = View.VISIBLE
            return
        }
        tvNoResult.visibility = View.GONE
        for (d in results) {
            val card = layoutInflater.inflate(R.layout.layout_result_card, cardList, false)
            bindCard(card, d)
            cardList.addView(card)
        }
        resultContainer.visibility = View.VISIBLE
    }

    /**
     * 绑定单张卡片数据与事件
     */
    private fun bindCard(card: View, d: CharData) {
        val tvChar = card.findViewById<TextView>(R.id.tv_char)
        tvChar.setText(d.charText)
        val tvCodes = card.findViewById<TextView>(R.id.tv_codes)
        tvCodes.setText(CharLabels.styleCodes(this, d.codes, d.charText))

        // 字根编码（拆分上方小字），没有则隐藏
        val tvRootCodes = card.findViewById<TextView>(R.id.tv_root_codes)
        if (!d.rootCodes.isNullOrEmpty()) {
            tvRootCodes.visibility = View.VISIBLE
            tvRootCodes.setText(d.rootCodes)
        } else {
            tvRootCodes.visibility = View.GONE
        }

        card.findViewById<TextView>(R.id.tv_components).setText(d.components ?: "")
        card.findViewById<TextView>(R.id.tv_pinyin).setText(formatPinyin(d.pinyin))
        card.findViewById<TextView>(R.id.tv_unicode).setText(formatUnicode(d.unicodeBlock, d.unicodeCode))

        // 整句码（zheng.txt），没有则隐藏整行
        val rowZheng = card.findViewById<View>(R.id.row_zheng)
        val tvZhengCode = card.findViewById<TextView>(R.id.tv_zheng_code)
        if (!d.zhengCode.isNullOrEmpty()) {
            rowZheng.visibility = View.VISIBLE
            tvZhengCode.setText(d.zhengCode)
        } else {
            rowZheng.visibility = View.GONE
        }

        // 每张卡片独立的分享/链接
        card.findViewById<ImageButton>(R.id.btn_share).setOnClickListener { shareResultCard(d) }
        card.findViewById<TextView>(R.id.btn_zitong).setOnClickListener { openExternalLink(d, "https://zi.tools/zi/") }
        card.findViewById<TextView>(R.id.btn_hadian).setOnClickListener { openExternalLink(d, "https://zdic.net/hans/") }

        applyFontSize(card)

        // 字头右侧标签胶囊（追加在 tv_char 之后；字号已调整，胶囊据此与汉字腰部对齐）
        val charRow = tvChar.parent as? LinearLayout
        if (charRow != null) {
            CharLabels.addLabelChips(this, charRow, d.charText, tvChar, tvCodes)
        }

        // 结果文本设置完成后，应用字符级 fallback 字体
        AppFonts.applyToHierarchy(card)
    }

    /**
     * 拼音：没有则显示「无」，有则带括号展示
     */

    /**
     * 生成单字查询结果卡片图片并调起系统分享。
     * 图片与 App 内渲染一致：复用同款卡片样式与内置字体，
     * 不含字统/汉典链接行，也不含分享按钮；右下角加虎助手水印。
     */
    private fun shareResultCard(d: CharData) {
        // 兜底：字体尚未加载完成时补加载（正常情况启动时已后台加载完）
        if (!AppFonts.isLoaded()) {
            AppFonts.load(applicationContext)
        }
        if (!CharLabels.isLoaded()) {
            CharLabels.load(applicationContext)
        }
        val card = layoutInflater.inflate(R.layout.layout_share_card, null) as LinearLayout

        val shareChar = card.findViewById<TextView>(R.id.tv_share_char)
        shareChar.text = d.charText
        val tvShareCodes = card.findViewById<TextView>(R.id.tv_share_codes)
        tvShareCodes.text = CharLabels.styleCodes(this, d.codes, d.charText)
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
        val shareCharRow = shareChar.parent as? LinearLayout
        if (shareCharRow != null) {
            CharLabels.addLabelChips(this, shareCharRow, d.charText, shareChar, tvShareCodes)
        }
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
            // 非关键路径失败：静默降级，避免打扰用户
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

    /**
     * 对所有结果卡片应用当前字号
     */
    private fun reapplyFontSize() {
        for (i in 0 until cardList.childCount) {
            applyFontSize(cardList.getChildAt(i))
        }
    }

    /**
     * 应用字号到单张结果卡片正文
     */
    private fun applyFontSize(card: View) {
        val sp = currentFontSp
        card.findViewById<TextView>(R.id.tv_char).setTextSize(sp + 4f)
        card.findViewById<TextView>(R.id.tv_codes).setTextSize(sp)
        card.findViewById<TextView>(R.id.tv_root_codes).setTextSize(sp - 4f)
        card.findViewById<TextView>(R.id.tv_components).setTextSize(sp)
        card.findViewById<TextView>(R.id.tv_pinyin).setTextSize(sp)
        card.findViewById<TextView>(R.id.tv_unicode).setTextSize(sp - 2f)
        card.findViewById<TextView>(R.id.tv_zheng_code).setTextSize(sp)
    }

}
