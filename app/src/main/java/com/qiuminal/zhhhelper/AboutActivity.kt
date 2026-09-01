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

        // 有新版本：后台查询 GitHub Releases，发现新版本时显示徽标并可点击更新
        val updateBadge = findViewById<TextView>(R.id.tv_update_badge)
        AppUpdater.checkLatest { info ->
            runOnUiThread {
                if (info != null && AppUpdater.isNewer(info.versionName, currentVersion)) {
                    updateBadge.visibility = View.VISIBLE
                    updateBadge.setOnClickListener { confirmUpdate(info) }
                }
            }
        }

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
                e.printStackTrace()
            }
        }

        // 全局字体：主界面已加载时立即生效；未加载则在后台补加载一次
        Thread {
            AppFonts.load(applicationContext)
            runOnUiThread {
                AppFonts.applyToHierarchy(findViewById(android.R.id.content))
                findViewById<JustifyTextView>(R.id.tv_changelog)?.rebuild()
            }
        }.start()
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
            e.printStackTrace()
        }
    }
}
