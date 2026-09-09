package com.qiuminal.zhhhelper

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 关于页：联系方式 + 更新日志。
 * 通过主界面抽屉菜单“关于”进入。
 */
class AboutActivity : AppCompatActivity() {

    companion object {
        /** 应晚于默认 Activity 入场动画，避免更新检查的线程启动影响动画帧。 */
        private const val UPDATE_CHECK_DELAY_MS = 350L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        val currentVersion = version ?: "0.0.0"
        findViewById<TextView>(R.id.tv_version).text =
            getString(R.string.about_version, currentVersion)

        // 先完成首帧与 Activity 入场动画，再启动非关键的网络更新检查。
        // 使用 decorView.postDelayed 不阻塞主线程；短暂延后可避免网络线程创建与入场动画争抢资源。
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                checkForUpdate(currentVersion)
            }
        }, UPDATE_CHECK_DELAY_MS)

        // 更新日志：版本号+日期首行加粗，与正文内容区分
        val changelogText = getString(R.string.changelog_content)
        val changelogSpannable = SpannableString(changelogText)
        val headingRegex = Regex("""^\d+\.\d+\.\d+（\d{4}-\d{2}-\d{2}）""", RegexOption.MULTILINE)
        for (match in headingRegex.findAll(changelogText)) {
            changelogSpannable.setSpan(
                StyleSpan(Typeface.BOLD),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val changelogView = findViewById<JustifyTextView>(R.id.tv_changelog)
        changelogView.setTextSizeSp(15f)
        changelogView.setLineSpacingExtraDp(6f)
        changelogView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        changelogView.setText(changelogSpannable)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btn_github).setOnClickListener {
            openUrl("https://github.com/qiuminal")
        }

        // QQ 号：点击复制到剪贴板
        findViewById<TextView>(R.id.btn_qq).setOnClickListener {
            val qq = "871334822"
            try {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("QQ", qq))
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // 非关键路径失败：静默降级，避免打扰用户
            }
        }

        // 字体已由主页面后台加载。已加载时在 About 首次绘制前一次性应用，
        // 避免先显示系统字体、随后整页替换字体并重排造成明显闪烁。
        // About 也可能被系统单独恢复；此时先用系统字体稳定显示，不在进页后整页二次刷新。
        if (AppFonts.isLoaded()) {
            AppFonts.applyToHierarchy(findViewById(android.R.id.content))
            changelogView.rebuild()
        }
    }

    /** 后台检查更新；结果只在 Activity 仍存活时更新徽标。 */
    private fun checkForUpdate(currentVersion: String) {
        AppUpdater.checkLatest { info ->
            if (isFinishing || isDestroyed) return@checkLatest
            if (info != null && AppUpdater.isNewer(info.versionName, currentVersion)) {
                findViewById<TextView>(R.id.tv_update_badge).apply {
                    visibility = View.VISIBLE
                    setOnClickListener { confirmUpdate(info) }
                }
            }
        }
    }

    private fun confirmUpdate(info: ReleaseInfo) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_update_confirm)
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        dialog.findViewById<TextView>(R.id.tv_update_title)?.text = "发现新版本 v${info.versionName}"
        dialog.findViewById<TextView>(R.id.btn_update_cancel)?.setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.btn_update_ok)?.setOnClickListener {
            dialog.dismiss()
            startDownload(info.apkUrl)
        }
        dialog.show()
    }

    private fun startDownload(url: String) {
        val progressDialog = Dialog(this)
        progressDialog.setContentView(R.layout.dialog_update_progress)
        progressDialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        progressDialog.setCancelable(false)
        progressDialog.show()
        val progressBar = progressDialog.findViewById<ProgressBar>(R.id.pb_update)
        val percentText = progressDialog.findViewById<TextView>(R.id.tv_update_percent)
        AppUpdater.downloadAndInstall(
            this,
            url,
            onProgress = { p ->
                progressBar?.progress = p
                percentText?.text = "$p%"
            },
            onDone = { ok, err ->
                progressDialog.dismiss()
                if (!ok) {
                    // 下载/安装启动失败才提示；成功时已拉起安装界面，不再弹 Toast 遮挡
                    Toast.makeText(this, "更新失败：${err ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // 非关键路径失败：静默降级，避免打扰用户
        }
    }
}
