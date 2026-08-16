package org.dalanben.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import org.dalanben.app.ui.AppRoot
import org.dalanben.app.data.Api
import org.dalanben.app.data.Session
import org.dalanben.app.ui.screens.ConsentScreen
import org.dalanben.app.ui.theme.DalanbenTheme
import org.dalanben.app.util.Notify

class MainActivity : ComponentActivity() {
    /** Compose 可观察的深度链接 URI */
    private val _deepLinkUri = mutableStateOf<Uri?>(null)
    private val _deepLinkNav = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.restore(this)
        Api.init(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(Api.okHttpClient)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25) // 内存缓存占可用内存 25%
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(512L * 1024 * 1024) // 磁盘缓存 512MB
                        .build()
                }
                .logger(DebugLogger())
                .build()
        )
        _deepLinkNav.value = intent?.getStringExtra("navigate_to")
        _deepLinkUri.value = intent?.data
        setContent {
            var agreed by remember {
                mutableStateOf(getSharedPreferences("dalanben_consent", Context.MODE_PRIVATE)
                    .getBoolean("privacy_agreed", false))
            }
            DalanbenTheme(darkTheme = isSystemInDarkTheme()) {
                if (agreed) {
                    Notify.init(this@MainActivity)
                    AppRoot(initialNav = _deepLinkNav.value?.also { _deepLinkNav.value = null }, initialUri = _deepLinkUri.value?.also { _deepLinkUri.value = null })
                } else {
                    ConsentScreen(onAgreed = { agreed = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        _deepLinkUri.value = intent.data
        _deepLinkNav.value = intent.getStringExtra("navigate_to")
    }
}
