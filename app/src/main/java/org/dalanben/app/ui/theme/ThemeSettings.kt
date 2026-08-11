package org.dalanben.app.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.themeDataStore by preferencesDataStore(name = "dalanben_theme")

private val THEME_MODE = stringPreferencesKey("theme_mode")

/** 主题模式: 跟随系统 / 浅色 / 深色 */
enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

/** 主题偏好的持久化读写(基于 androidx.datastore) */
object ThemeSettings {
    /** 读取当前主题模式(一次性) */
    suspend fun getMode(context: Context): ThemeMode =
        context.themeDataStore.data.first()[THEME_MODE]?.let { ThemeMode.from(it) } ?: ThemeMode.SYSTEM

    /** 持久化保存主题模式 */
    suspend fun setMode(context: Context, mode: ThemeMode) {
        context.themeDataStore.edit { it[THEME_MODE] = mode.value }
    }
}
