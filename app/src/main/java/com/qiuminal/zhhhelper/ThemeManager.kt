package com.qiuminal.zhhhelper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * 暗黑模式管理：三档「白天 / 黑夜 / 跟随系统」，用 AppCompatDelegate 全局生效并持久化。
 *
 * - 存储：SharedPreferences("settings") 的 "night_mode"（0=白天,1=黑夜,2=跟随系统）。
 * - 应用：映射到 AppCompatDelegate.MODE_NIGHT_NO / YES / FOLLOW_SYSTEM，
 *   setDefaultNightMode 在夜间模式实际变化时会自动重建当前 Activity。
 * - 首次未设置默认「跟随系统」。
 */
object ThemeManager {

    const val MODE_DAY = 0
    const val MODE_NIGHT = 1
    const val MODE_SYSTEM = 2

    private const val PREFS = "settings"
    private const val KEY = "night_mode"

    /** 读取已保存的档位（默认跟随系统）。 */
    fun currentMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, MODE_SYSTEM)

    /** 把档位映射为 AppCompatDelegate 的夜间模式常量。 */
    private fun toDelegateMode(mode: Int): Int = when (mode) {
        MODE_DAY -> AppCompatDelegate.MODE_NIGHT_NO
        MODE_NIGHT -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** 启动时按存储应用（Application.onCreate 调用），全局生效。 */
    fun applyStored(context: Context) {
        AppCompatDelegate.setDefaultNightMode(toDelegateMode(currentMode(context)))
    }

    /** 保存并应用指定档位（夜间模式变化时框架会自动重建 Activity）。 */
    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
        AppCompatDelegate.setDefaultNightMode(toDelegateMode(mode))
    }

    /** 循环切换：白天 → 黑夜 → 跟随系统 → 白天，返回切换后的档位。 */
    fun cycle(context: Context): Int {
        val next = when (currentMode(context)) {
            MODE_DAY -> MODE_NIGHT
            MODE_NIGHT -> MODE_SYSTEM
            else -> MODE_DAY
        }
        setMode(context, next)
        return next
    }

    /** 当前档位对应的头部图标资源。 */
    fun iconRes(mode: Int): Int = when (mode) {
        MODE_DAY -> R.drawable.ic_theme_day
        MODE_NIGHT -> R.drawable.ic_theme_night
        else -> R.drawable.ic_theme_system
    }

    /** 当前档位的无障碍描述。 */
    fun contentDescription(mode: Int): String = when (mode) {
        MODE_DAY -> "主题：白天，点击切换"
        MODE_NIGHT -> "主题：黑夜，点击切换"
        else -> "主题：跟随系统，点击切换"
    }
}
