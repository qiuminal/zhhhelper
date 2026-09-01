package com.qiuminal.zhhhelper

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/**
 * 字帖跟打用的透明输入捕获框（按键计数版）。
 *
 * 安卓软键盘（输入法）是独立进程，APP 拿不到物理按键事件，只能拦截输入法
 * 通过 InputConnection 送入输入框的每一次按键产物。这里包装 InputConnection
 * 「纯监听」实际按键，不依赖任何码表估算：
 *  - setComposingText 组合区增长 → 每个新字母算 1 键（打码字母键）
 *  - commitText 直接上屏（无组合） → 每个字符算 1 键（英文逐字/独立空格）
 *  - commitText 提交组合 → 只把随上屏带入的空格算作真实空格键，候选字本身
 *    已在组合期计入，不重复计
 *  - deleteSurroundingText / 退格键事件 → 每次算 1 键
 *  - 空格/回车键事件 → 每次算 1 键
 *
 * 说明：纯监听能做到的上限如此；某些输入法把「空格选字」的按键完全吞掉
 * （不转发按键事件、也不把空格带上屏）时，该空格无法被感知，会略少计。
 */
class KeyCountingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatEditText(context, attrs) {

    /** 每检测到 n 次按键时回调（主线程）。 */
    var onKeys: ((Int) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return CountingInputConnection(base, this)
    }

    private class CountingInputConnection(
        target: InputConnection,
        private val edit: KeyCountingEditText,
    ) : InputConnectionWrapper(target, true) {

        private var composingLen = 0
        private var pendingCommit = false

        private fun addKeys(n: Int) {
            if (n > 0) edit.onKeys?.invoke(n)
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val len = text?.length ?: 0
            if (len > composingLen) {
                // 组合区增长：逐字母键（打码的每个字母）
                addKeys(len - composingLen)
            }
            if (len == 0 && composingLen > 0) {
                // 组合被清空，接下来多半是 commitText 提交候选，标记为「组合提交」
                pendingCommit = true
            }
            composingLen = len
            return super.setComposingText(text, newCursorPosition)
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val len = text?.length ?: 0
            if (pendingCommit || composingLen > 0) {
                // 组合提交：打码字母已在组合期计入；
                // 只把随候选一起上屏的空格当作真实按下的空格键（空格选字「空格上屏」场景）
                val spaces = text?.count { it == ' ' } ?: 0
                addKeys(spaces)
                pendingCommit = false
                composingLen = 0
            } else {
                // 无组合直接上屏：英文逐字、独立空格等，逐字符计 1 键
                addKeys(len)
            }
            return super.commitText(text, newCursorPosition)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            addKeys(beforeLength + afterLength)
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_DEL,
                    -> addKeys(1)
                }
            }
            return super.sendKeyEvent(event)
        }
    }
}
