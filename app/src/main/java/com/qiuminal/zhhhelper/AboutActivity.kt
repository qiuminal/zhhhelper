package com.qiuminal.zhhhelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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
        findViewById<TextView>(R.id.tv_version).text =
            getString(R.string.about_version, version ?: "0.1.0")

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
            }
        }.start()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
