package org.dalanben.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.dalanben.app.BuildConfig
import org.dalanben.app.data.Api
import org.dalanben.app.data.AppVersion
import org.dalanben.app.data.Post
import org.dalanben.app.data.Session
import org.dalanben.app.data.SessionManager
import org.dalanben.app.data.UnreadCount
import org.dalanben.app.data.User
import org.dalanben.app.ui.theme.ThemeMode
import org.dalanben.app.ui.theme.ThemeSettings

/** 下载进度 UI 状态：progress 为 0..100，-1 表示进度未知（无 content-length / 准备中） */
data class DownloadUiState(
    val progress: Int = 0,
    val phase: String = "",
    val etaSeconds: Int? = null,    // 预计剩余秒数；null = 未知（如服务端烧录准备阶段）
    val elapsedSeconds: Int = 0,    // 已用时（秒），准备阶段用于显示“已等待”
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val _user = MutableStateFlow<User?>(Session.user)
    val user: StateFlow<User?> = _user

    private val _unread = MutableStateFlow<UnreadCount?>(null)
    val unread: StateFlow<UnreadCount?> = _unread

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun showToast(msg: String) { _toast.value = msg }
    fun consumeToast() { _toast.value = null }

    private val _download = MutableStateFlow<DownloadUiState?>(null)
    val download: StateFlow<DownloadUiState?> = _download

    fun showDownloadProgress(
        progress: Int,
        phase: String,
        etaSeconds: Int? = null,
        elapsedSeconds: Int = 0,
    ) {
        _download.value = DownloadUiState(
            progress = progress.coerceIn(-1, 100),
            phase = phase,
            etaSeconds = etaSeconds,
            elapsedSeconds = elapsedSeconds,
        )
    }

    /** 用户点击浮层「停止」时调用：中断当前下载/烧录并隐藏浮层 */
    fun cancelDownload() {
        org.dalanben.app.util.cancelActiveDownload()
        dismissDownload()
    }

    fun dismissDownload() { _download.value = null }

    // 主题模式(跟随系统 / 浅色 / 深色), 启动时从 DataStore 读取
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    init {
        viewModelScope.launch {
            _themeMode.value = ThemeSettings.getMode(getApplication())
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        viewModelScope.launch { ThemeSettings.setMode(getApplication(), mode) }
    }

    fun setUser(u: User) {
        _user.value = u
        Session.user = u
    }

    fun loadMe() {
        viewModelScope.launch {
            try {
                val resp = Api.service.me()
                if (resp.ok && resp.data != null) {
                    _user.value = resp.data
                    Session.user = resp.data
                    SessionManager.saveUser(getApplication(), resp.data)
                }
            } catch (_: Exception) { }
        }
    }

    fun loadUnread() {
        viewModelScope.launch {
            try {
                val resp = Api.service.unreadCount()
                if (resp.ok) _unread.value = resp.data
            } catch (_: Exception) { }
        }
    }

    /** 列表滚动位置/分页缓存: 按 refreshKey 保存, 进入详情返回后恢复滑动位置 */
    data class FeedCache(
        val posts: List<Post>,
        val page: Int,
        val end: Boolean,
        val index: Int,
        val offset: Int
    )
    val feedCache = mutableMapOf<String, FeedCache>()

    fun refreshAll() {
        loadMe()
        loadUnread()
    }

    /** 远程配置版本号, HomeScreen 根据此值重新读取节日入口 / Banner */
    private val _configVersion = MutableStateFlow(0)
    val configVersion: StateFlow<Int> = _configVersion
    fun onConfigFetched() { _configVersion.value++ }

    /** 首页当前选中的 Tab: 0=推荐, 1=精选, 2=最新。存入 ViewModel 确保从详情返回时不丢位置 */
    private val _homeTabIndex = MutableStateFlow(0)
    val homeTabIndex: StateFlow<Int> = _homeTabIndex
    fun setHomeTabIndex(i: Int) { _homeTabIndex.value = i }

    /** 单列刷视频模式(TikTok 风格), 存入 ViewModel 持久化 */
    private val _singleColumnMode = MutableStateFlow(false)
    val singleColumnMode: StateFlow<Boolean> = _singleColumnMode
    fun toggleSingleColumn() { _singleColumnMode.value = !_singleColumnMode.value }
    fun exitSingleColumn() { _singleColumnMode.value = false }

    /** 检测更新: 返回比当前版本更新的最新版本, 否则返回 null */
    suspend fun checkAppUpdate(): AppVersion? {
        return try {
            val resp = Api.service.latestAppVersion()
            if (resp.ok && resp.data != null) {
                val v = resp.data.version
                if (v != null && v.versionCode > BuildConfig.VERSION_CODE) v else null
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
