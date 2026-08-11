package org.dalanben.app.ui.screens

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import org.dalanben.app.ui.components.TopBar

/**
 * 隐私政策 / 用户协议 / 社区规范 / 儿童政策：直接加载网页版对应页面，保证与网页版内容 100% 一致。
 * type = "privacy" -> 隐私政策；type = "terms" -> 用户协议；
 * type = "rules" -> 社区规范；type = "children" -> 儿童（未成年人）隐私保护政策。
 */
private val LEGAL_PAGES = mapOf(
    "privacy" to ("隐私政策" to "https://dalanben.org/privacy"),
    "terms" to ("用户协议" to "https://dalanben.org/terms"),
    "rules" to ("社区规范" to "https://dalanben.org/rules"),
    "children" to ("儿童政策" to "https://dalanben.org/children")
)

@Composable
fun LegalScreen(navController: NavController, type: String) {
    val (title, url) = LEGAL_PAGES[type] ?: LEGAL_PAGES.getValue("privacy")
    var isLoading by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack = { navController.popBackStack() })
        if (isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?
                        ) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                    }
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.loadsImagesAutomatically = true
                    loadUrl(url)
                }
            }
        )
    }
}
