package org.dalanben.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

private val LEGAL_URLS = mapOf(
    "privacy" to ("隐私政策" to "https://dalanben.org/privacy"),
    "terms" to ("用户协议" to "https://dalanben.org/terms"),
    "rules" to ("社区规范" to "https://dalanben.org/rules"),
    "children" to ("儿童政策" to "https://dalanben.org/children")
)

/**
 * 首次启动：隐私协议同意 + 必要权限请求。
 * 不同意则退出 App；同意后请求必要权限，再进入 AppRoot。
 */
@Composable
fun ConsentScreen(onAgreed: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity

    // 当前查看的协议类型（null = 主界面）
    var viewingLegal by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // 必要权限列表（需在多处使用）
    val neededPerms = remember {
        mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else if (Build.VERSION.SDK_INT >= 26) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    // 已授权/拒绝结果（全部视为“已处理”，不阻塞进入）
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onAgreed()
    }

    fun handleAgree() {
        // 持久化同意
        context.getSharedPreferences("dalanben_consent", Context.MODE_PRIVATE)
            .edit().putBoolean("privacy_agreed", true).apply()
        // 请求必要权限（勾选后进入）
        if (neededPerms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            permLauncher.launch(neededPerms.toTypedArray())
        } else {
            onAgreed()
        }
    }

    if (viewingLegal != null) {
        // 内联查看隐私政策 / 用户协议
        val (title, url) = LEGAL_URLS[viewingLegal] ?: LEGAL_URLS.getValue("privacy")
        Column(Modifier.fillMaxSize()) {
            // 简易 TopBar
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton({ viewingLegal = null }) { Text("← 返回") }
                Spacer(Modifier.weight(1f))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center)
                Spacer(Modifier.weight(1f))
            }
            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }
                        }
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        loadUrl(url)
                    }
                }
            )
        }
    } else {
        // 同意主界面
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("大蓝本", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("散帅男性成长社区", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))

            Text("欢迎使用大蓝本！", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text(
                "在继续之前，请仔细阅读并同意以下协议",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // 协议链接
            TextButton({ viewingLegal = "privacy" }) {
                Text("《隐私政策》", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            }
            TextButton({ viewingLegal = "terms" }) {
                Text("《用户协议》", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            }
            TextButton({ viewingLegal = "rules" }) {
                Text("《社区规范》", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            }
            TextButton({ viewingLegal = "children" }) {
                Text("《儿童政策》", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("点击上方链接可查看详细内容", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))

            // 同意按钮
            Button(
                onClick = ::handleAgree,
                Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("同意并继续", fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))

            // 不同意按钮
            TextButton(
                onClick = { activity.finish() },
                Modifier.fillMaxWidth()
            ) {
                Text("不同意并退出", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))

            Text(
                "继续即表示您已阅读并同意《隐私政策》《用户协议》《社区规范》和《儿童政策》",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
