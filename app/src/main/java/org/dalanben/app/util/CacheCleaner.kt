package org.dalanben.app.util

import android.content.Context
import coil.Coil
import java.io.File

/**
 * 存储占用分析与垃圾清理
 *
 * 可安全清理（可再生缓存）：
 *  - cacheDir 下的 Coil 图片缓存(image_cache)、DownloadUtil/MediaCompressor 临时文件等
 * 不可清理（用户数据，仅统计展示）：
 *  - files/ 下的 DataStore(登录态)、SharedPreferences、数据库
 */
data class CacheBreakdown(
    val imageCache: Long = 0,   // 图片缓存
    val tempFiles: Long = 0,    // 临时文件
    val otherCache: Long = 0,   // 其它缓存
    val userData: Long = 0,     // 用户数据(不可清理)
) {
    val totalCache: Long get() = imageCache + tempFiles + otherCache
    val totalAll: Long get() = totalCache + userData
}

object CacheCleaner {

    /** 递归统计目录大小 */
    private fun dirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var size = 0L
        dir.listFiles()?.forEach { f ->
            size += if (f.isDirectory) dirSize(f) else runCatching { f.length() }.getOrDefault(0L)
        }
        return size
    }

    /** 深度分析: 分类统计缓存与用户数据占用 */
    fun analyze(context: Context): CacheBreakdown {
        val cacheDir = context.cacheDir
        var image = 0L; var temp = 0L; var other = 0L
        if (cacheDir.exists() && cacheDir.isDirectory) {
            cacheDir.listFiles()?.forEach { f ->
                val n = f.name.lowercase()
                val s = if (f.isDirectory) dirSize(f) else runCatching { f.length() }.getOrDefault(0L)
                when {
                    n.contains("image_cache") || n.contains("coil") -> image += s
                    n.startsWith("dl_") || n.contains("compress") || n.endsWith(".mp4")
                    || n.endsWith(".jpg") || n.endsWith(".webp") || n.endsWith(".tmp") -> temp += s
                    else -> other += s
                }
            }
        }
        val userData = dirSize(File(context.filesDir, "datastore"))
            + dirSize(File(context.filesDir, "shared_prefs"))
            + dirSize(File(context.filesDir, "databases"))
        return CacheBreakdown(image, temp, other, userData)
    }

    /** 一键清理缓存, 返回释放的字节数 */
    fun clean(context: Context): Long {
        var freed = 0L
        val cacheDir = context.cacheDir
        if (cacheDir.exists() && cacheDir.isDirectory) {
            val before = dirSize(cacheDir)
            cacheDir.listFiles()?.forEach { f ->
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }
            val after = dirSize(cacheDir)
            freed += (before - after)
        }
        // 清空 Coil 内存/磁盘缓存
        runCatching {
            val img = Coil.imageLoader(context)
            img.diskCache?.clear()
            img.memoryCache?.clear()
        }
        return freed
    }

    /** 人类可读大小 */
    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
