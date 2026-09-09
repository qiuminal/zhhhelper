package com.qiuminal.zhhhelper

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 原生文章跟打页；功能迁移自“沐塵跟打器 v1.01 手机版”HTML Demo。 */
class ArticleActivity : AppCompatActivity() {
    companion object {
        private const val HISTORY_KEY = "result_history"
        private const val HISTORY_FILE = "article_result_history.json"
        private const val MAX_HISTORY = 100
    }

    private lateinit var titleView: TextView
    private lateinit var countView: TextView
    private lateinit var referenceView: ArticleCopybookView
    private lateinit var inputView: EditText
    private lateinit var timerView: TextView
    private lateinit var speedView: TextView
    private lateinit var progressView: TextView
    private lateinit var referenceScroll: ScrollView
    private lateinit var resultRows: LinearLayout
    private lateinit var resultPageView: TextView
    private lateinit var resultPrevButton: TextView
    private lateinit var resultNextButton: TextView

    private var referenceText = ""
    private var reference = IntArray(0)
    private var elapsedSeconds = 0
    private var timerRunning = false
    private var completed = false
    private var fontSp = 26f
    private val results = mutableListOf<ArticleResult>()
    private var resultPage = 0
    private var resultPageSize = ARTICLE_RESULT_FALLBACK_PAGE_SIZE
    private val handler = Handler(Looper.getMainLooper())
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35) }

    private val timerTick = object : Runnable {
        override fun run() {
            if (!timerRunning) return
            elapsedSeconds++
            updateStatsLine(currentInput())
            handler.postDelayed(this, 1000L)
        }
    }

    private val textPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importText(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)
        bindViews()
        loadResultHistory()
        setupListeners()
        renderResultHistory()
        // 首帧布局后按「扣除固定开销」的公式重算每页行数并重渲染一次，
        // 修正直接用行区自身高度计算导致的溢出（按钮被顶出卡片底边）
        resultRows.post {
            val density = resources.displayMetrics.density
            val rowHeight = (26f * density).roundToInt().coerceAtLeast(1)
            val card = resultRows.parent?.parent as? android.view.ViewGroup
            // 固定开销（实测校准）：卡片上下padding(20) + 表头(24) + 翻页行margin(10) + 按钮行(28)
            // + divider/圆角/舍入累计偏差 —— 实测约 112dp，取 112 保证任何机型不溢出
            val fixedOverhead = (density * 112f).roundToInt()
            val cardHeight = card?.height ?: 0
            if (cardHeight > fixedOverhead) {
                val measuredPageSize = ((cardHeight - fixedOverhead) / rowHeight).coerceAtLeast(1)
                if (measuredPageSize != resultPageSize) {
                    resultPageSize = measuredPageSize
                    renderResultHistory()
                }
            }
            // 关键：把行区高度固定为「行数×行高」，阻断 weight 撑高导致的溢出链
            val rowsHeight = resultPageSize * rowHeight
            if (resultRows.layoutParams is android.widget.LinearLayout.LayoutParams) {
                val tableLp = resultRows.layoutParams as android.widget.LinearLayout.LayoutParams
                tableLp.height = rowsHeight
                tableLp.weight = 0f
                resultRows.layoutParams = tableLp
            } else {
                resultRows.layoutParams.height = rowsHeight
                resultRows.layoutParams = resultRows.layoutParams
            }
            // 表格容器同步改为 wrap，让卡片布局按内容精确结算
            (resultRows.parent as? android.view.ViewGroup)?.let { table ->
                if (table.layoutParams is android.widget.LinearLayout.LayoutParams) {
                    val tLp = table.layoutParams as android.widget.LinearLayout.LayoutParams
                    tLp.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    tLp.weight = 0f
                    table.layoutParams = tLp
                }
            }
            // 上下等距：卡片高 - 内容高 = 剩余空间，将其一半作为按钮行的 marginTop，
            // 另一半自然落在卡片底部 padding 之后，实现「末行底→按钮顶 == 按钮底→卡片底」
            if (cardHeight > 0) {
                val cardView = resultRows.parent?.parent as? android.view.ViewGroup
                val buttonRow = cardView?.let { cv ->
                    val tableIdx = cv.indexOfChild(resultRows.parent as android.view.View)
                    if (tableIdx >= 0 && tableIdx + 1 < cv.childCount) cv.getChildAt(tableIdx + 1) else null
                }
                val contentHeight = (density * (10f + 24f)).roundToInt() + rowsHeight + (density * 28f).roundToInt()
                val remaining = cardHeight - (density * 10f).roundToInt() - contentHeight
                if (remaining > 0 && buttonRow != null) {
                    // 视觉校准：按钮文字底边略高于行底边，减 5px 补偿使上下视觉对称
                    val symMargin = (remaining / 2f).toInt() - 5
                    if (buttonRow.layoutParams is android.widget.LinearLayout.LayoutParams) {
                        val bLp = buttonRow.layoutParams as android.widget.LinearLayout.LayoutParams
                        bLp.topMargin = symMargin
                        buttonRow.layoutParams = bLp
                    }
                }
            }
        }
        resultRows.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val density = resources.displayMetrics.density
            val rowHeight = (26f * density).roundToInt().coerceAtLeast(1)
            // 与 onCreate 的首帧重算保持同一公式：卡片高 - 固定开销(112dp 实测校准) 再除行高
            val card = resultRows.parent?.parent as? android.view.ViewGroup
            val fixedOverhead = (density * 112f).roundToInt()
            val cardHeight = card?.height ?: 0
            if (cardHeight > fixedOverhead) {
                val measuredPageSize = ((cardHeight - fixedOverhead) / rowHeight).coerceAtLeast(1)
                if (measuredPageSize != resultPageSize) {
                    resultPageSize = measuredPageSize
                    // 关键：不能在 onLayoutChange 回调内直接增删子 View——
                    // post 到下一帧消息队列再重建，保证完整的 measure/layout 流程
                    resultRows.post { renderResultHistory() }
                }
            }
        }
        fontSp = getSharedPreferences("article", MODE_PRIVATE).getFloat("font_sp", 26f).coerceIn(16f, 32f)
        applyFontSize()
        ArticleWhitelist.load(applicationContext)
        val welcomeText = assets.open("article_welcome.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        loadReference(welcomeText, "欢迎使用虎助手")
        if (AppFonts.isLoaded()) AppFonts.applyToHierarchy(findViewById(android.R.id.content))
        inputView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                inputView.requestFocus()
                showKeyboard()
            }
        }, 250L)
    }

    private fun bindViews() {
        titleView = findViewById(R.id.tv_article_title)
        countView = findViewById(R.id.tv_char_count)
        referenceView = findViewById(R.id.tv_reference)
        inputView = findViewById(R.id.et_article_input)
        timerView = findViewById(R.id.tv_timer)
        speedView = findViewById(R.id.tv_speed)
        progressView = findViewById(R.id.tv_progress)
        referenceScroll = findViewById(R.id.scroll_reference)
        resultRows = findViewById(R.id.result_rows)
        resultPageView = findViewById(R.id.tv_result_page)
        resultPrevButton = findViewById(R.id.btn_result_prev)
        resultNextButton = findViewById(R.id.btn_result_next)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_restart).setOnClickListener { restart() }
        findViewById<TextView>(R.id.btn_import_text).setOnClickListener {
            textPicker.launch(arrayOf("text/plain", "text/*"))
        }
        findViewById<TextView>(R.id.btn_paste_text).setOnClickListener { loadFromClipboard() }
        findViewById<ImageButton>(R.id.btn_font_minus).setOnClickListener { adjustFont(-2f) }
        findViewById<ImageButton>(R.id.btn_font_plus).setOnClickListener { adjustFont(2f) }
        resultPrevButton.setOnClickListener {
            if (resultPage > 0) {
                resultPage--
                renderResultHistory()
            }
        }
        resultNextButton.setOnClickListener {
            if (resultPage + 1 < articleResultPageCount(results.size, resultPageSize)) {
                resultPage++
                renderResultHistory()
            }
        }

        inputView.filters = arrayOf(InputFilter.LengthFilter(ArticlePracticeLogic.MAX_UTF16_LENGTH))
        inputView.setOnLongClickListener { true }
        inputView.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?) = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) = Unit
        }
        inputView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed &&
                (keyCode == KeyEvent.KEYCODE_V || keyCode == KeyEvent.KEYCODE_A)
            ) {
                true
            } else false
        }
        inputView.setOnFocusChangeListener { _, focused ->
            if (!focused) pauseTimer()
        }
        inputView.setOnClickListener {
            inputView.setSelection(inputView.text.length)
            showKeyboard()
        }
        referenceView.setOnClickListener {
            inputView.requestFocus()
            inputView.setSelection(inputView.text.length)
            showKeyboard()
        }
        inputView.addTextChangedListener(object : TextWatcher {
            private var changing = false
            private var previousSize = 0
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (changing || completed) return
                var points = currentInput()
                if (points.size > reference.size) {
                    changing = true
                    inputView.setText(String(points.copyOf(reference.size), 0, reference.size))
                    inputView.setSelection(inputView.text.length)
                    changing = false
                    points = currentInput()
                }
                if (points.size > previousSize && points.isNotEmpty()) {
                    val i = points.lastIndex
                    if (i < reference.size && points[i] != reference[i]) tone.startTone(ToneGenerator.TONE_PROP_NACK, 120)
                }
                previousSize = points.size
                if (points.isEmpty()) resetTimer() else startTimer()
                renderReference(points)
                updateStatsLine(points)
                if (points.size == reference.size && reference.isNotEmpty()) complete(points)
                inputView.setSelection(inputView.text.length)
            }
        })
    }

    private fun loadReference(raw: String, title: String): Boolean {
        val (normalized, truncated) = ArticlePracticeLogic.normalizeText(raw)
        val filtered = ArticleWhitelist.filter(normalized)
        if (filtered.isEmpty()) {
            Toast.makeText(this, "没有有效字符", Toast.LENGTH_SHORT).show()
            return false
        }
        referenceText = filtered
        reference = ArticlePracticeLogic.codePoints(referenceText)
        titleView.text = title // 文件名只作纯文本，不解析 HTML
        countView.text = "${reference.size} 字符"
        restart()
        if (truncated) Toast.makeText(this, "文本已截取前8000字符", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun restart() {
        completed = false
        inputView.isEnabled = true
        inputView.setText("")
        resetTimer()
        renderReference(IntArray(0))
        updateStatsLine(IntArray(0))
        referenceScroll.scrollTo(0, 0)
        inputView.requestFocus()
        inputView.post { showKeyboard() }
    }

    private fun renderReference(input: IntArray) {
        referenceView.setPractice(reference, input)
        scrollToCurrent()
    }

    private fun scrollToCurrent() {
        referenceView.post {
            val scrollView = referenceScroll
            val viewport = (scrollView.height - scrollView.paddingTop - scrollView.paddingBottom).coerceAtLeast(1)
            // 坐标换算：字帖视图 y=0 位于 ScrollView 内容坐标 paddingTop 处，
            // 有效可视高度 = height - paddingTop，故目标 scrollY = 字帖偏移 - paddingBottom。
            // 结果不为正说明正文顶部无需滚动，保持 0（避免载文后第一行被 paddingTop 顶出视口）。
            val target = (referenceView.scrollTarget(viewport) - scrollView.paddingBottom).coerceAtLeast(0)
            if (target > scrollView.scrollY) scrollView.scrollTo(0, target)
        }
    }

    private fun startTimer() {
        if (completed || timerRunning || !inputView.hasFocus()) return
        timerRunning = true
        handler.postDelayed(timerTick, 1000L)
    }

    private fun pauseTimer() {
        timerRunning = false
        handler.removeCallbacks(timerTick)
    }

    private fun resetTimer() {
        pauseTimer()
        elapsedSeconds = 0
    }

    private fun updateStatsLine(input: IntArray) {
        timerView.text = "计时 %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
        val correct = ArticlePracticeLogic.correctCount(reference, input)
        val speed = ArticlePracticeLogic.correctCharactersPerMinute(correct, elapsedSeconds)
        speedView.text = "$speed 字/分"
        progressView.text = "${input.size} / ${reference.size}"
        progressView.setTextColor(if (completed) Color.rgb(76, 140, 80) else ContextCompat.getColor(this, R.color.code_blue))
    }

    private fun complete(input: IntArray) {
        completed = true
        pauseTimer()
        inputView.isEnabled = false
        updateStatsLine(input)
        val stats = ArticlePracticeLogic.stats(reference, input, elapsedSeconds)
        results.add(
            0,
            ArticleResult(
                title = titleView.text.toString(),
                characters = stats.total,
                elapsedSeconds = stats.elapsedSeconds,
                speed = stats.speed,
                accuracy = stats.accuracy,
                errors = stats.errors,
                finishedAt = System.currentTimeMillis(),
            ),
        )
        if (results.size > MAX_HISTORY) results.subList(MAX_HISTORY, results.size).clear()
        resultPage = 0
        saveResultHistory()
        renderResultHistory()
    }

    private fun loadFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val raw = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        } else {
            ""
        }
        loadReference(raw, "剪贴板文本")
    }

    private fun importText(uri: Uri) {
        try {
            val projection = arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME)
            val metadata = contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use Pair(0L, "导入")
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val byteSize = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else "导入"
                Pair(byteSize, name)
            } ?: Pair(0L, "导入")
            if (metadata.first > 5L * 1024 * 1024) {
                Toast.makeText(this, "文件过大", Toast.LENGTH_SHORT).show(); return
            }
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            val text = bytes.toString(Charsets.UTF_8)
            loadReference(text, metadata.second)
        } catch (_: Exception) {
            Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentInput(): IntArray = ArticlePracticeLogic.codePoints(inputView.text?.toString().orEmpty())

    private fun adjustFont(delta: Float) {
        fontSp = (fontSp + delta).coerceIn(16f, 32f)
        applyFontSize()
        getSharedPreferences("article", MODE_PRIVATE).edit().putFloat("font_sp", fontSp).apply()
    }

    private fun applyFontSize() {
        referenceView.setTextSizeSp(fontSp)
    }

    /** 冷启动读取诊断信息：读取失败时显示在成绩表占位行，避免 Toast 被键盘遮挡 */
    private var loadDiagnostic: String? = null

    private fun loadResultHistory() {
        loadDiagnostic = null
        // 主存储：应用私有文件（写入时已 fsync），读取不依赖 SharedPreferences
        var raw: String? = null
        var fileExists = false
        var fileLength = 0L
        runCatching {
            val file = File(filesDir, HISTORY_FILE)
            fileExists = file.exists()
            fileLength = file.length()
            if (file.exists() && file.length() > 0L) raw = file.readText()
        }
        var fromPrefs = false
        // 兼容旧版本：文件不存在时回退读取 SharedPreferences 中的旧历史
        if (raw.isNullOrEmpty()) {
            raw = getSharedPreferences("article", MODE_PRIVATE).getString(HISTORY_KEY, null)
            fromPrefs = !raw.isNullOrEmpty()
        }
        if (raw.isNullOrEmpty()) {
            loadDiagnostic = "文件存在=$fileExists 大小=$fileLength prefs=$fromPrefs"
            return
        }
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until minOf(array.length(), MAX_HISTORY)) {
                val item = array.getJSONObject(i)
                results += ArticleResult(
                    title = item.optString("title"),
                    characters = item.optInt("characters"),
                    elapsedSeconds = item.optInt("elapsedSeconds"),
                    speed = item.optInt("speed"),
                    accuracy = item.optInt("accuracy"),
                    errors = item.optInt("errors"),
                    finishedAt = item.optLong("finishedAt"),
                )
            }
        }
        if (results.isEmpty()) {
            loadDiagnostic = "文件存在=$fileExists 大小=$fileLength prefs=$fromPrefs 解析失败"
        }
    }

    private fun saveResultHistory() {
        val array = JSONArray()
        results.forEach { result ->
            array.put(
                JSONObject()
                    .put("title", result.title)
                    .put("characters", result.characters)
                    .put("elapsedSeconds", result.elapsedSeconds)
                    .put("speed", result.speed)
                    .put("accuracy", result.accuracy)
                    .put("errors", result.errors)
                    .put("finishedAt", result.finishedAt),
            )
        }
        val json = array.toString()
        // 主存储：单次打开文件流写入 + flush + fsync，一次性落盘
        // （不能用 writeText 后再 outputStream()——后者会以截断模式重新打开，把刚写的内容清空）
        runCatching {
            val file = File(filesDir, HISTORY_FILE)
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(file).use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
        }
        // 兼容旧版本：同时写一份到 SharedPreferences
        getSharedPreferences("article", MODE_PRIVATE).edit().putString(HISTORY_KEY, json).commit()
    }

    private fun renderResultHistory() {
        resultRows.removeAllViews()
        val pages = articleResultPageCount(results.size, resultPageSize)
        resultPage = resultPage.coerceIn(0, pages - 1)
        resultPageView.text = articleResultPageLabel(resultPage, results.size, resultPageSize)
        resultPrevButton.isEnabled = resultPage > 0
        resultNextButton.isEnabled = resultPage + 1 < pages
        resultPrevButton.alpha = if (resultPrevButton.isEnabled) 1f else 0.4f
        resultNextButton.alpha = if (resultNextButton.isEnabled) 1f else 0.4f
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val pageResults = articleResultPage(results, resultPage, resultPageSize)
        val density = resources.displayMetrics.density
        val rowHeight = (26f * density).roundToInt().coerceAtLeast(1)
        val rowCount = resultPageSize.coerceAtLeast(1)
        val textColor = ContextCompat.getColor(this, R.color.text_primary)
        // 列权重：标题 速度 字数 错字 用时 时间（与表头一致）
        // 时间列加宽到 1.35 保证 "MM-dd HH:mm" 单行完整显示；标题收窄但仍可容纳 10+ 字符
        val weights = floatArrayOf(1.5f, 0.75f, 0.65f, 0.7f, 0.8f, 1.35f)
        for (index in 0 until rowCount) {
            // 纯代码构建行，不再依赖 XML inflate，排除布局文件层面的任何问题
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeight,
                )
            }
            if (index % 2 == 1) row.setBackgroundColor(Color.parseColor("#FAFAFA"))
            fun cell(weight: Float, text: String, ellipsize: Boolean = false): TextView {
                return TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                    gravity = android.view.Gravity.CENTER
                    setTextColor(textColor)
                    textSize = 12f
                    if (ellipsize) {
                        maxLines = 1
                        this.ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    this.text = text
                }
            }
            if (index < pageResults.size) {
                val result = pageResults[index]
                row.addView(cell(weights[0], result.title.removeSuffix(".txt").removeSuffix(".TXT"), ellipsize = true))
                row.addView(cell(weights[1], result.speed.toString()))
                row.addView(cell(weights[2], result.characters.toString()))
                row.addView(cell(weights[3], result.errors.toString()))
                row.addView(cell(weights[4], "%02d:%02d".format(result.elapsedSeconds / 60, result.elapsedSeconds % 60)))
                row.addView(cell(weights[5], format.format(Date(result.finishedAt)), ellipsize = true))
            } else {
                row.addView(
                    cell(weights[0], if (results.isEmpty()) (loadDiagnostic ?: "暂无成绩") else "", ellipsize = false),
                )
                for (w in 1 until weights.size) row.addView(cell(weights[w], ""))
            }
            resultRows.addView(row)
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onPause() {
        pauseTimer()
        super.onPause()
    }

    override fun onDestroy() {
        pauseTimer()
        tone.release()
        super.onDestroy()
    }
}
