package org.dalanben.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.dalanben.app.BuildConfig

private val Context.remoteConfigDataStore by preferencesDataStore(name = "dalanben_remote_config")

/**
 * 热更新配置管理器。
 * 启动时从服务端拉取，缓存到 DataStore；提供统一的读取接口。
 */
object RemoteConfigManager {
    private val CONFIG_JSON = stringPreferencesKey("remote_config_json")
    private var memoryConfig: RemoteConfig? = null

    private fun gson(): Gson = Gson()

    /** 从缓存恢复（内存 → DataStore） */
    fun getCached(context: Context): RemoteConfig? {
        memoryConfig?.let { return it }
        return runBlocking {
            val json = context.remoteConfigDataStore.data.first()[CONFIG_JSON]
            if (!json.isNullOrBlank()) {
                try {
                    gson().fromJson(json, RemoteConfig::class.java).also { memoryConfig = it }
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    }

    /** 保存到内存 + DataStore */
    suspend fun save(context: Context, config: RemoteConfig) {
        memoryConfig = config
        context.remoteConfigDataStore.edit {
            it[CONFIG_JSON] = gson().toJson(config)
        }
    }

    /** 清空缓存 */
    suspend fun clear(context: Context) {
        memoryConfig = null
        context.remoteConfigDataStore.edit { it.remove(CONFIG_JSON) }
    }

    /** 拉取最新配置 */
    suspend fun fetch(context: Context): RemoteConfig? {
        return try {
            val resp = Api.service.remoteConfigActive(
                platform = "android",
                versionCode = BuildConfig.VERSION_CODE
            )
            if (resp.ok) {
                val data = resp.data
                if (data != null) {
                    val cfg = RemoteConfig(
                        config = data.config,
                        announcement = data.announcement,
                        festiveEntries = data.festiveEntries,
                        banners = data.banners,
                        theme = data.theme,
                        fetchedAt = data.fetchedAt
                    )
                    save(context, cfg)
                    cfg
                } else {
                    getCached(context)
                }
            } else {
                getCached(context)
            }
        } catch (_: Exception) {
            getCached(context)
        }
    }

    /** 读取 KV 配置（字符串） */
    fun getString(context: Context, key: String, default: String = ""): String {
        val cfg = getCached(context)?.config ?: return default
        return when (val v = cfg[key]) {
            is String -> v
            null -> default
            else -> v.toString()
        }
    }

    /** 读取 KV 配置（布尔） */
    fun getBool(context: Context, key: String, default: Boolean = false): Boolean {
        val cfg = getCached(context)?.config ?: return default
        return when (val v = cfg[key]) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.lowercase() in setOf("1", "true", "yes", "on")
            else -> default
        }
    }

    /** 读取 KV 配置（整数） */
    fun getInt(context: Context, key: String, default: Int = 0): Int {
        val cfg = getCached(context)?.config ?: return default
        return when (val v = cfg[key]) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }

    /** 当前生效公告 */
    fun getAnnouncement(context: Context): Announcement? = getCached(context)?.announcement

    /** 指定位置的节日入口 */
    fun getFestiveEntries(context: Context, location: String): List<FestiveEntry> {
        return getCached(context)?.festiveEntries?.filter { it.location == location } ?: emptyList()
    }

    /** 指定位置的 Banner */
    fun getBanners(context: Context, location: String): List<Banner> {
        return getCached(context)?.banners?.filter { it.location == location } ?: emptyList()
    }

    /** 当前生效主题 */
    fun getTheme(context: Context): AppTheme? = getCached(context)?.theme
}
