package com.qiuminal.zhhhelper

import android.app.Application

/**
 * 应用入口：在任何 Activity 创建前应用已保存的暗黑模式档位，保证全局一致。
 */
class ZhhApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyStored(this)
    }
}
