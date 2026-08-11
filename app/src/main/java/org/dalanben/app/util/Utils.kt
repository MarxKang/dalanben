package org.dalanben.app.util

import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import org.dalanben.app.data.BASE_URL

/** 把后端返回的相对/绝对 URL 补全为可加载的完整地址 */
fun fullUrl(u: String?): String? {
    if (u.isNullOrEmpty()) return null
    if (u.startsWith("http://") || u.startsWith("https://")) return u
    if (u.startsWith("/")) return BASE_URL + u
    return u
}

/**
 * 安全提取媒体 URL 列表：
 * - 列表本身为 null 时返回空列表
 * - 过滤掉数组中的 null 元素与空白字符串
 * 后端 media_urls 在异常情况下可能存成 ["null"] 或含 null 元素，直接调用
 * it.isNotBlank() / firstOrNull { } 会触发 NPE，统一走本方法消除该崩溃。
 */
fun List<String>?.safeMedia(): List<String> =
    (this ?: emptyList()).filterNotNull().filter { it.isNotBlank() }

/** 相对时间(中文) */
fun formatTime(ts: Long?): String {
    if (ts == null || ts == 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - ts
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86400 -> "${diff / 3600}小时前"
        diff < 86400 * 7 -> "${diff / 86400}天前"
        diff < 86400 * 30 -> "${diff / (86400 * 7)}周前"
        diff < 86400 * 365 -> "${diff / (86400 * 30)}个月前"
        else -> "${diff / (86400 * 365)}年前"
    }
}

/** 数字缩写: 12345 -> 1.2万 */
fun formatCount(n: Int): String {
    return when {
        n < 10000 -> n.toString()
        n < 100000000 -> String.format("%.1f万", n / 10000.0)
        else -> String.format("%.1f亿", n / 100000000.0)
    }
}

/** 话题热度: 原始 hot_score 为小数(<1), 放大 100 倍取整展示, 避免恒为 0 */
fun formatHeat(score: Double): String = formatCount((score * 100).roundToInt())

fun postTypeLabel(type: String?): String = when (type) {
    "image" -> "图文"
    "video" -> "视频"
    else -> "文章"
}

/** 解析 #RRGGBB / #AARRGGBB / RRGGBB 为 Compose Color，失败返回 null */
fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val s = hex.trim()
    return try {
        val value = when {
            s.startsWith("#") -> s.drop(1)
            s.startsWith("0x", ignoreCase = true) -> s.drop(2)
            else -> s
        }.toLong(16)
        when (s.length) {
            7 -> Color(value or 0xFF000000)
            9 -> Color(value)
            6 -> Color(value or 0xFF000000)
            8 -> Color(value)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
