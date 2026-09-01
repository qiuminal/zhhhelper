package com.qiuminal.zhhhelper

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.util.Locale
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

/**
 * 练单（字帖模式）：
 * 从 TypeSunny（fcxxxz/TypeSunny）练单器移植的固定文本跟打练习，适配移动端。
 * 默认仅提供原项目的「字帖模式」：练习文本直接铺开显示，逐字校验输入，
 * 打对变绿、打错标红，整组完成后统计 击键/速度/键准，并支持错字重打。
 *
 * 练习文本来自原项目 Resources/练单器 目录下的 txt 文件（内置 assets/practice/），
 * 分组规则与原项目一致：fixed（每行一项、每组 10 项）或 varible（每行一组）。
 */
class PracticeActivity : AppCompatActivity() {

    private lateinit var etCapture: EditText
    private lateinit var tvDisplay: TextView
    private lateinit var tvGroupInfo: TextView
    private lateinit var tvKpsValue: TextView
    private lateinit var tvSpeedValue: TextView
    private lateinit var tvAccValue: TextView
    private lateinit var tvDoneTitle: TextView
    private lateinit var tvDoneStats: TextView
    private lateinit var tvWrongLabel: TextView
    private lateinit var tvWrongList: TextView
    private lateinit var panelDone: View
    private lateinit var btnRetype: View
    private lateinit var btnNextGroup: View
    private lateinit var btnRestart: View
    private lateinit var chipRow: LinearLayout
    private lateinit var scrollPractice: ScrollView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnFontMinus: ImageButton
    private lateinit var btnFontPlus: ImageButton
    private lateinit var btnShuffle: TextView
    private lateinit var btnRestore: TextView
    private lateinit var btnStatsInfo: ImageButton

    private val files = mutableListOf<String>()
    private var currentFileIndex = -1
    private var groups: List<List<String>> = emptyList()
    private var groupIndex = 0
    private var originalGroups: List<List<String>> = emptyList()  // 原始分组（恢复顺序用）
    private var shuffled = false
    private var varibleMode = false

    // 打字会话状态
    private var target = ""
    private var statuses = IntArray(0)      // 0 未打 / 1 打对 / 2 打错
    private var committedChars = emptyArray<Char?>()  // 每个位置实际上屏的字（错字也按它计键）
    private var charIndex = 0
    private var correctCount = 0
    private var wrongCount = 0
    private var wrongChars = mutableListOf<Char>()
    private var keystrokes = 0              // 预计击键数（Rime 最短编码 + 按上屏批次计的上屏键）
    private var expectedKeys = 0             // 本组应键数（目标字最短编码 + 每字 1 次上屏键）
    private var errorKeys = 0                // 错键数（错字按编码逐键比对累计）
    private var firstCharCommitMs = 0L     // 第一个字上屏时刻（elapsedRealtime，计时起点）
    private var firstCharKeys = 0          // 第一个字键数（错字按错字编码 + 1 上屏键）
    private var infiniteStats = false        // 整组单批上屏等不可测速场景：结算显示 ∞
    private var inRetype = false
    private var pendingInput = ""           // 尚未处理的已上屏文本
    private var suppress = false            // 屏蔽自身删除引发的回调
    private var inputEnabled = false

    private var displayFontSp = 26f

    private val prefs by lazy { getSharedPreferences("practice", Context.MODE_PRIVATE) }

    companion object {
        private const val GROUP_SIZE = 10
        private const val MIN_FONT = 18f
        private const val MAX_FONT = 40f
        private const val COLOR_GREEN = 0xFF2E9E5B.toInt()
        private const val COLOR_RED = 0xFFD64545.toInt()
        private const val COLOR_CORRECT_BG = 0xFFE8F5E9.toInt()   // 已打对：浅绿阴影
        private const val COLOR_WRONG_BG = 0xFFFDECEA.toInt()     // 已打错：浅红阴影
        private const val COLOR_CURSOR = 0xFF5B7FB5.toInt()       // 待打光标：竖线
        private const val CARET_ANCHOR = '\u200B'                 // 零宽占位，画光标用
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice)
        initViews()
        loadPrefs()
        setupInput()
        loadFiles()

        // 码表与字体在后台加载；完成后套用全局字体并重绘字帖
        Thread {
            DataLoader.load(applicationContext)
            KeystrokeTable.load(applicationContext)
            runOnUiThread {
                AppFonts.applyToHierarchy(findViewById(android.R.id.content))
                renderDisplay()
            }
        }.start()
    }

    /**
     * 外接键盘热键：仅在本组完成卡片显示期间生效——Space=下一组，F3=重打本组；
     * 打字进行中不拦截，保证不干扰输入法。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val doneVisible = panelDone.visibility == View.VISIBLE
        if (doneVisible && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE -> {
                    Log.d("ZhhPractice", "hotkey space handled")
                    nextGroup()
                    return true
                }
                KeyEvent.KEYCODE_F3 -> {
                    Log.d("ZhhPractice", "hotkey f3 handled")
                    startGroup(groupIndex)
                    return true
                }
            }
        }
        Log.d("ZhhPractice", "key code=${event.keyCode} act=${event.action} done=$doneVisible")
        return super.dispatchKeyEvent(event)
    }

    // ---------------- 初始化 ----------------

    private fun initViews() {
        etCapture = findViewById(R.id.et_capture)
        tvDisplay = findViewById(R.id.tv_display)
        tvGroupInfo = findViewById(R.id.tv_group_info)
        tvKpsValue = findViewById(R.id.tv_kps_value)
        tvSpeedValue = findViewById(R.id.tv_speed_value)
        tvAccValue = findViewById(R.id.tv_acc_value)
        tvDoneTitle = findViewById(R.id.tv_done_title)
        tvDoneStats = findViewById(R.id.tv_done_stats)
        tvWrongLabel = findViewById(R.id.tv_wrong_label)
        tvWrongList = findViewById(R.id.tv_wrong_list)
        panelDone = findViewById(R.id.panel_done)
        panelDone.isFocusable = true
        panelDone.isFocusableInTouchMode = true
        btnRetype = findViewById(R.id.btn_retype)
        btnNextGroup = findViewById(R.id.btn_next_group)
        btnRestart = findViewById(R.id.btn_restart)
        chipRow = findViewById(R.id.chip_row)
        scrollPractice = findViewById(R.id.scroll_practice)
        btnPrev = findViewById(R.id.btn_prev)
        btnNext = findViewById(R.id.btn_next)
        btnBack = findViewById(R.id.btn_back)
        btnFontMinus = findViewById(R.id.btn_font_minus)
        btnFontPlus = findViewById(R.id.btn_font_plus)
        btnShuffle = findViewById(R.id.btn_shuffle)
        btnRestore = findViewById(R.id.btn_restore)
        btnStatsInfo = findViewById(R.id.btn_stats_info)

        // 工具栏按钮仅作触摸操作：禁止硬件键盘焦点落到按钮上，避免空格误高亮/误触返回
        listOf(btnBack, btnPrev, btnNext, btnFontMinus, btnFontPlus, btnStatsInfo).forEach {
            it.isFocusable = false
            it.isFocusableInTouchMode = false
        }

        btnBack.setOnClickListener { finish() }
        btnPrev.setOnClickListener { if (groups.isNotEmpty()) startGroup(groupIndex - 1) }
        btnNext.setOnClickListener { nextGroup() }
        btnRetype.setOnClickListener { startRetype() }
        btnNextGroup.setOnClickListener { nextGroup() }
        btnRestart.setOnClickListener { if (groups.isNotEmpty()) startGroup(groupIndex) }
        btnFontMinus.setOnClickListener { adjustFontSize(-2f) }
        btnFontPlus.setOnClickListener { adjustFontSize(2f) }
        btnShuffle.setOnClickListener { shuffleAll() }
        btnRestore.setOnClickListener { restoreOrder() }
        btnStatsInfo.setOnClickListener {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog_stats_info)
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            dialog.findViewById<TextView>(R.id.btn_dialog_ok)?.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    private fun loadPrefs() {
        displayFontSp = prefs.getFloat("display_font_sp", 26f).coerceIn(MIN_FONT, MAX_FONT)
        applyDisplayFontSize()
    }

    private fun adjustFontSize(delta: Float) {
        val next = (displayFontSp + delta).coerceIn(MIN_FONT, MAX_FONT)
        if (next != displayFontSp) {
            displayFontSp = next
            applyDisplayFontSize()
        }
    }

    private fun applyDisplayFontSize() {
        tvDisplay.setTextSize(displayFontSp)
        prefs.edit().putFloat("display_font_sp", displayFontSp).apply()
    }

    private fun loadFiles() {
        Thread {
            val names = try {
                assets.list("practice")
                    ?.filter { it.endsWith(".txt") }
                    ?.sortedWith(compareBy { it.substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE })
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                files.clear()
                files.addAll(names)
                buildChips()
                if (files.isEmpty()) {
                    tvGroupInfo.text = "未找到练习文本"
                    return@runOnUiThread
                }
                val saved = prefs.getString("last_file", null)
                val idx = files.indexOf(saved).takeIf { it >= 0 } ?: 0
                selectFile(idx)
            }
        }.start()
    }

    // ---------------- 文本选择与解析 ----------------

    private fun selectFile(idx: Int) {
        if (idx !in files.indices) return
        currentFileIndex = idx
        prefs.edit().putString("last_file", files[idx]).apply()
        buildChips()
        groups = parseFile(files[idx])
        originalGroups = groups
        // 冷启动恢复乱序：保持与退出时一致的载文顺序与跟打进度
        if (prefs.getBoolean("shuffled_$idx", false)) {
            groups = applyShuffle(groups, prefs.getLong("shuffle_seed_$idx", 0L))
            shuffled = true
        } else {
            shuffled = false
        }
        if (groups.isEmpty()) {
            tvDisplay.text = "练习文本为空"
            return
        }
        val savedGroup = prefs.getInt("last_group_$idx", 0)
        groupIndex = savedGroup.coerceIn(0, groups.size - 1)
        startGroup(groupIndex)
    }

    /**
     * 与原项目 ReadTxt 一致：
     * 首行首字非字母且最长行 >4 字 → varible（每行一组）；
     * 否则 fixed（每行一项，每组 GROUP_SIZE 项）。
     */
    private fun parseFile(assetName: String): List<List<String>> {
        val text = try {
            assets.open("practice/$assetName").use { it.bufferedReader(Charsets.UTF_8).readText() }
        } catch (e: Exception) {
            return emptyList()
        }
        var mbtxt = text.trim().replace("\r", "")
        while (mbtxt.contains("\n\n")) mbtxt = mbtxt.replace("\n\n", "\n")
        while (mbtxt.contains("  ")) mbtxt = mbtxt.replace("  ", " ")
        val lines = mbtxt.split('\n').filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val maxLen = lines.maxOf { it.length }
        val first = lines[0].firstOrNull() ?: return emptyList()
        val firstIsLetter = first in 'A'..'Z' || first in 'a'..'z'

        return if (!firstIsLetter && maxLen > 4) {
            varibleMode = true
            lines.map { line -> codePoints(line) }
        } else {
            varibleMode = false
            // fixed：每行一项，按 GROUP_SIZE 分组
            val result = mutableListOf<List<String>>()
            var i = 0
            while (i < lines.size) {
                result.add(lines.subList(i, minOf(i + GROUP_SIZE, lines.size)))
                i += GROUP_SIZE
            }
            result
        }
    }

    private fun codePoints(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            out.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return out
    }

    private fun buildChips() {
        chipRow.removeAllViews()
        files.forEachIndexed { idx, name ->
            val chip = TextView(this)
            chip.text = displayName(name)
            chip.textSize = 13f
            chip.gravity = Gravity.CENTER
            chip.setPadding(dp(14), dp(7), dp(14), dp(7))
            chip.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            val selected = idx == currentFileIndex
            chip.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            chip.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF5B7FB5.toInt())
            chip.setOnClickListener { selectFile(idx) }
            chipRow.addView(chip)
        }
    }

    private fun displayName(name: String): String =
        name.removeSuffix(".txt").replaceFirst(Regex("^\\d+\\."), "")

    // ---------------- 会话控制 ----------------

    private fun startGroup(index: Int) {
        groupIndex = index.coerceIn(0, groups.size - 1)
        prefs.edit().putInt("last_group_$currentFileIndex", groupIndex).apply()
        beginSession(groups[groupIndex].joinToString(""), inRetype = false)
    }

    private fun startRetype() {
        if (wrongChars.isEmpty()) return
        beginSession(wrongChars.joinToString(""), inRetype = true)
    }

    private fun nextGroup() {
        if (groups.isEmpty()) return
        if (groupIndex < groups.size - 1) {
            startGroup(groupIndex + 1)
        } else {
            Toast.makeText(this, "已是最后一组", Toast.LENGTH_SHORT).show()
        }
    }
    /** 全体乱序：fixed 打散全部条目后按 GROUP_SIZE 重新分组；varible 打乱行（组）顺序。保存乱序状态与种子，冷启动可恢复。 */
    private fun shuffleAll() {
        if (groups.isEmpty() || currentFileIndex < 0) return
        val seed = System.currentTimeMillis()
        groups = applyShuffle(groups, seed)
        shuffled = true
        groupIndex = 0
        prefs.edit()
            .putBoolean("shuffled_$currentFileIndex", true)
            .putLong("shuffle_seed_$currentFileIndex", seed)
            .putInt("last_group_$currentFileIndex", 0)
            .apply()
        startGroup(0)
    }

    /** 乱序（固定种子可复现）：fixed 打散全部条目后按 GROUP_SIZE 重新分组；varible 打乱行（组）顺序。 */
    private fun applyShuffle(source: List<List<String>>, seed: Long): List<List<String>> {
        val rnd = Random(seed)
        if (varibleMode) {
            return source.toMutableList().also { it.shuffle(rnd) }
        }
        val flat = mutableListOf<String>()
        for (g in source) flat.addAll(g)
        if (flat.isEmpty()) return source
        flat.shuffle(rnd)
        val list = mutableListOf<List<String>>()
        var i = 0
        while (i < flat.size) {
            list.add(flat.subList(i, minOf(i + GROUP_SIZE, flat.size)))
            i += GROUP_SIZE
        }
        return list
    }

    /** 恢复顺序：回到该练习文本的原始分组。 */
    private fun restoreOrder() {
        if (!shuffled || currentFileIndex < 0) return
        groups = originalGroups
        shuffled = false
        groupIndex = 0
        prefs.edit()
            .putBoolean("shuffled_$currentFileIndex", false)
            .remove("shuffle_seed_$currentFileIndex")
            .putInt("last_group_$currentFileIndex", 0)
            .apply()
        startGroup(0)
    }


    private fun beginSession(newTarget: String, inRetype: Boolean) {
        target = newTarget
        statuses = IntArray(target.length)
        committedChars = arrayOfNulls(target.length)
        charIndex = 0
        correctCount = 0
        wrongCount = 0
        keystrokes = 0
        expectedKeys = computeExpectedKeys(newTarget)
        errorKeys = 0
        firstCharCommitMs = 0L
        firstCharKeys = 0
        infiniteStats = false
        this.inRetype = inRetype
        if (!inRetype) wrongChars.clear()
        pendingInput = ""
        panelDone.visibility = View.GONE
        inputEnabled = true
        etCapture.isEnabled = true
        etCapture.visibility = View.VISIBLE
        etCapture.setText("")
        updateGroupInfo()
        updateStats()
        renderDisplay()
        etCapture.requestFocus()
        showKeyboard()
    }

    // ---------------- 打字处理 ----------------

    private fun setupInput() {
        etCapture.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (suppress) {
                    suppress = false
                    return
                }
                if (s == null) return
                if (!inputEnabled) {
                    if (s.isNotEmpty()) {
                        suppress = true
                        s.clear()
                    }
                    return
                }
                processCommitted(s)
            }
        })
    }

    private fun processCommitted(s: Editable) {
        val committed = committedText(s)
        // 本批上屏是否包含组内第一个字（整组单批上屏时剩余字无可测速度）
        val batchStartsAtFirstChar = charIndex == 0
        // 用户删除（回退）：撤销上屏的字符
        if (committed.length < pendingInput.length) {
            repeat(pendingInput.length - committed.length) { undoOne() }
        }
        // 上屏键（空格/数字选重）按“上屏批次”记 1 次：一次上屏多字只加一次，
        // 不能按字直接 +1，否则整词/整句上屏会多算空格。
        val newChars = if (committed.length >= pendingInput.length) {
            committed.substring(pendingInput.length)
        } else {
            ""
        }
        if (newChars.isNotEmpty() && newChars.any { it != ' ' }) {
            keystrokes++
        }
        pendingInput = committed

        var consumed = 0
        while (consumed < pendingInput.length && charIndex < target.length) {
            val c = pendingInput[consumed]
            val expected = target[charIndex]
            // 输入法候选上屏时可能带入空格（空格选字/联想起始），目标不是空格时忽略，不计入键数与错字
            if (c == ' ' && expected != ' ') {
                consumed++
                continue
            }
            if (c == expected) {
                statuses[charIndex] = 1
                correctCount++
            } else {
                statuses[charIndex] = 2
                wrongCount++
                if (!inRetype) wrongChars.add(expected)
                errorKeys += estimateErrorKeys(expected, c)
            }
            keystrokes += keystrokeCodeLen(c)
            committedChars[charIndex] = c
            if (charIndex == 0 && firstCharCommitMs == 0L) {
                firstCharCommitMs = SystemClock.elapsedRealtime()
                firstCharKeys = keystrokeCodeLen(c) + 1
            }
            charIndex++
            consumed++
        }

        if (consumed > 0) {
            pendingInput = pendingInput.substring(consumed)
            suppress = true
            s.delete(0, consumed)
            updateStats()
            renderDisplay()
        }
        if (charIndex >= target.length) {
            if (pendingInput.isNotEmpty()) {
                suppress = true
                s.clear()
                pendingInput = ""
            }
            onGroupComplete(singleBatch = batchStartsAtFirstChar)
        }
    }

    /** 提取 EditText 中已上屏（非输入法组合区）的文本。 */
    private fun committedText(s: Editable): String {
        var composingStart = s.length
        for (span in s.getSpans(0, s.length, Any::class.java)) {
            if ((s.getSpanFlags(span) and Spannable.SPAN_COMPOSING) != 0) {
                val start = s.getSpanStart(span)
                if (start < composingStart) composingStart = start
            }
        }
        return s.subSequence(0, composingStart).toString()
    }

    private fun undoOne() {
        if (charIndex <= 0) return
        charIndex--
        when (statuses[charIndex]) {
            1 -> correctCount--
            2 -> {
                wrongCount--
                if (!inRetype) {
                    wrongChars.removeAt(wrongChars.lastIndexOf(target[charIndex]))
                }
                errorKeys -= estimateErrorKeys(target[charIndex], committedChars[charIndex] ?: target[charIndex])
            }
        }
        statuses[charIndex] = 0
        keystrokes -= keystrokeCodeLen(committedChars[charIndex] ?: target[charIndex])
        committedChars[charIndex] = null
        updateStats()
    }

    /**
     * 单个字符的预计击键数：取 Rime 虎码词典（tiger.dict.yaml）中该字的最短编码
     * 长度（简码）；码表中查不到的字直接按 1 键计，不回退其它码表。
     * 错字按“实际打出的那个字”的编码计（见 processCommitted）。
     * 上屏键（空格/数字选重）不在这里加，由 processCommitted 按“上屏批次”加 1。
     */
    private fun keystrokeCodeLen(ch: Char): Int {
        return KeystrokeTable.minCodeLen(ch) ?: 1
    }
    /** 本组应键数：每个目标字最短编码长度之和 + 每字一次上屏键（空格/数字选重）。 */
    private fun computeExpectedKeys(text: String): Int {
        var total = 0
        for (ch in text) total += keystrokeCodeLen(ch)
        return total + text.length
    }

    /** 剩余字（第 2 个字起）键数：总键数减去第一个字的键数。 */
    private fun restKeys(): Int = (keystrokes - firstCharKeys).coerceAtLeast(0)

    /** 以第一个字上屏时刻为起点，到 now 的剩余字用时（毫秒）。 */
    private fun restElapsedMs(nowMs: Long): Long =
        if (firstCharCommitMs == 0L) 0L else (nowMs - firstCharCommitMs).coerceAtLeast(0L)

    /**
     * 结算/展示用击键速度与字速：
     *  - 击键（键/秒）：只统计第一个字之后的剩余字，首字只作计时起点；
     *  - 速度（字/分）：首字计入正确字数，其用时按剩余字平均击键速度推算，
     *    即 速度 = 总正确字数 × 剩余键数 / (剩余用时 × (剩余键数 + 首字键数)) × 60，
     *    避免“首字打对、其余全错”时速度被算成 0；
     * 无法测算（剪贴板一次性上屏全部字、整组单批上屏、剩余字用时≈0 或剩余键数为 0）时，结算态返回 ∞。
     */
    private fun computeKpsSpeed(nowMs: Long, allowInfinite: Boolean, singleBatch: Boolean = false): Triple<Double, Double, Boolean> {
        val elapsed = restElapsedMs(nowMs)
        val keys = restKeys()
        if (allowInfinite && (singleBatch || elapsed <= 0 || keys <= 0)) {
            return Triple(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true)
        }
        val sec = elapsed.coerceAtLeast(1L) / 1000.0
        val kps = keys / sec
        // 方案A：首字用时 = 首字键数 / 剩余字平均击键速度，首字正确也计入字/分
        val speed = if (keys > 0) {
            correctCount * keys.toDouble() / (sec * (keys + firstCharKeys)) * 60.0
        } else {
            0.0
        }
        return Triple(kps, speed, false)
    }

    /**
     * 键准（键级）：全组（含首字）(应键数 - 错键数) / 应键数，取值 0~100。
     * 首字计入键准：首个字上屏后即可按实际错字结算（速度按方案A将首字推算计入，击键仍按剩余字口径）。
     */
    private fun keyAccuracy(): Double {
        val expected = expectedKeys
        if (expected <= 0) return 100.0
        val correct = (expected - errorKeys).coerceAtLeast(0)
        return correct * 100.0 / expected
    }

    /**
     * 单个错字的错键数结算（主流的编辑距离思路，逐键比对）：
     *  - 目标字与上屏错字的最短编码做 Levenshtein 比对，
     *    替换/多打/漏打一个字母都计 1 个错键；
     *  - 编码相同但字不同（选重选错）计 1 个错键；
     *  - 编码查不到：按实际打出的编码长度整串计错（查不到按 1）。
     */
    private fun estimateErrorKeys(expected: Char, committed: Char): Int {
        val expectedCode = KeystrokeTable.shortestCode(expected)
        val actualCode = KeystrokeTable.shortestCode(committed)
        return when {
            expectedCode == null -> actualCode?.length ?: 1
            actualCode == null -> 1
            expectedCode == actualCode -> 1
            else -> editDistance(expectedCode, actualCode)
        }
    }

    /** Levenshtein 编辑距离：两个编码串逐字母比对的差异数。 */
    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    // ---------------- 完成与统计 ----------------

    private fun onGroupComplete(singleBatch: Boolean = false) {
        inputEnabled = false
        etCapture.isEnabled = false
        etCapture.visibility = View.GONE
        etCapture.clearFocus()
        hideKeyboard()

        if (inRetype) {
            val remaining = mutableListOf<Char>()
            var i = 0
            while (i < target.length) {
                if (statuses[i] == 2) remaining.add(target[i])
                i++
            }
            wrongChars = remaining
            tvDoneTitle.text = if (wrongChars.isEmpty()) "错字重打完成 🎉" else "重打完成，仍有 ${wrongChars.size} 个错字"
            btnRetype.visibility = if (wrongChars.isEmpty()) View.GONE else View.VISIBLE
        } else {
            tvDoneTitle.text = if (wrongChars.isEmpty()) "本组完成 🎉" else "本组完成，有 ${wrongChars.size} 个错字"
            btnRetype.visibility = if (wrongChars.isEmpty()) View.GONE else View.VISIBLE
        }

        val acc = keyAccuracy()
        val (kps, speed, infinite) = computeKpsSpeed(SystemClock.elapsedRealtime(), allowInfinite = true, singleBatch = singleBatch)
        infiniteStats = infinite
        val kpsStr = if (infinite) "∞" else "%.2f".format(Locale.US, kps)
        val speedStr = if (infinite) "∞" else "%.2f".format(Locale.US, speed)
        tvDoneStats.text = "击键 $kpsStr 键/秒 ｜ 速度 $speedStr 字/分 ｜ 键准 %.1f%%".format(Locale.US, acc)
        copyResultToClipboard(buildShareText(speedStr, kpsStr, acc))

        tvWrongLabel.visibility = if (wrongChars.isEmpty()) View.GONE else View.VISIBLE
        tvWrongList.text = wrongChars.joinToString("  ")
        panelDone.visibility = View.VISIBLE
        val panelFocused = panelDone.requestFocus()
        Log.d("ZhhPractice", "complete panelFocus=$panelFocused imeActive=${(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).isActive()}")
        updateStats(allowInfinite = true)

        scrollPractice.post {
            val targetTop = panelDone.top
            scrollPractice.smoothScrollTo(0, targetTop)
        }
    }

    /** 生成可分享的纯文本成绩单。 */
    private fun buildShareText(speedStr: String, kpsStr: String, acc: Double): String {
        val name = if (currentFileIndex in files.indices) displayName(files[currentFileIndex]) else "练单"
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
        return "${name}第${groupIndex + 1}/${groups.size}组 速度$speedStr 击键$kpsStr " +
            "键准${"%.2f".format(Locale.US, acc)}% 字数${target.length} 错字${wrongChars.size} " +
            "虎助手·练单v$version"
    }

    /** 每完成一组自动把成绩写入系统剪贴板（延续 PC 端主流跟打器体验）。 */
    private fun copyResultToClipboard(text: String) {
        if (text.isEmpty()) return
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("练单成绩", text))
        } catch (e: Exception) {
            // 剪贴板异常不影响跟打
        }
    }

    private fun updateGroupInfo() {
        if (groups.isEmpty()) {
            tvGroupInfo.text = ""
            return
        }
        val label = if (inRetype) "重打错字" else "第 ${groupIndex + 1} / ${groups.size} 组"
        tvGroupInfo.text = "$label · ${target.length} 字"
    }

    /** 击键按“剩余字”实测（首字只作计时起点）；速度按方案A将首字按剩余平均击键推算计入；键准含首字。 */
    private fun updateStats(allowInfinite: Boolean = false) {
        val (kps, speed, infinite) = if (infiniteStats) {
            Triple(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true)
        } else {
            computeKpsSpeed(SystemClock.elapsedRealtime(), allowInfinite)
        }
        tvKpsValue.text = if (infinite) "∞" else "%.2f".format(Locale.US, kps)
        tvSpeedValue.text = if (infinite) "∞" else "%.2f".format(Locale.US, speed)
        tvAccValue.text = "%.1f".format(Locale.US, keyAccuracy())
    }

    // ---------------- 渲染 ----------------

    /**
     * 字帖渲染：
     *  - 待打字符：不加任何底色，只在它前面画一条竖线光标；
     *  - 已打对的字：绿色 + 浅绿阴影；已打错的字：红色 + 浅红阴影；
     *  - 未打的后续字：普通颜色，无阴影。
     */
    private fun renderDisplay() {
        if (target.isEmpty()) {
            if (groups.isNotEmpty()) {
                tvDisplay.text = AppFonts.style(SpannableString("（空）")) ?: "（空）"
            }
            return
        }
        // 在待打字符前插入零宽占位符，用于承载竖线光标（不占宽度、不挤动文字）
        val hasCaret = charIndex < target.length
        val sb = StringBuilder(target)
        if (hasCaret) sb.insert(charIndex, CARET_ANCHOR)
        val spannable = SpannableString(sb.toString())

        var i = 0
        while (i < target.length) {
            val count = Character.charCount(target.codePointAt(i))
            // 光标占位符插入后，光标及其后的字整体右移 1 位
            val pos = if (i < charIndex) i else i + 1
            val end = pos + count
            when (statuses[i]) {
                1 -> {
                    spannable.setSpan(ForegroundColorSpan(COLOR_GREEN), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(BackgroundColorSpan(COLOR_CORRECT_BG), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                2 -> {
                    spannable.setSpan(ForegroundColorSpan(COLOR_RED), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(BackgroundColorSpan(COLOR_WRONG_BG), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            i += count
        }
        if (hasCaret) {
            spannable.setSpan(
                CaretSpan(COLOR_CURSOR, resources.displayMetrics.density),
                charIndex, charIndex + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvDisplay.text = AppFonts.style(spannable) ?: spannable
    }

    /** 零宽竖线光标：不占排版宽度，绘制在待打字符前。 */
    private class CaretSpan(
        private val color: Int,
        private val density: Float,
    ) : ReplacementSpan() {

        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = 0

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            baseline: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val oldColor = paint.color
            paint.color = color
            val width = 2.5f * density
            val y1 = top + (bottom - top) * 0.18f
            val y2 = bottom - (bottom - top) * 0.18f
            canvas.drawRect(x, y1, x + width, y2, paint)
            paint.color = oldColor
        }
    }

    // ---------------- 其它 ----------------

    private fun showKeyboard() {
        etCapture.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etCapture, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}











