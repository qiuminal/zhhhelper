package com.qiuminal.zhhhelper

/** 语义化版本比较（纯逻辑，供 JVM 单元测试覆盖）。 */
object Version {
    fun isNewer(latest: String?, current: String?): Boolean {
        if (latest.isNullOrBlank() || current.isNullOrBlank()) return false
        val a = latest.trim().split('.').mapNotNull { it.toIntOrNull() }
        val b = current.trim().split('.').mapNotNull { it.toIntOrNull() }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
