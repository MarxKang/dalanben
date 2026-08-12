package org.dalanben.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.AppVersion
import org.dalanben.app.ui.components.TopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 更新日志页: 展示全部历史版本(版本号/发布时间/大小/更新说明)
 */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<AppVersion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val r = Api.service.appVersions()
            if (r.ok && r.data != null) versions = r.data!!.list
            else error = r.msg ?: "加载失败"
        } catch (e: Exception) {
            error = "网络错误: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("更新日志", onBack = onBack)

        when {
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        TextButton({ error = null; versions = null; scope.launch {
                            try {
                                val r = Api.service.appVersions()
                                if (r.ok && r.data != null) versions = r.data!!.list
                                else error = r.msg ?: "加载失败"
                            } catch (e: Exception) { error = "网络错误: ${e.message}" }
                        } }) { Text("重试") }
                    }
                }
            }
            versions == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            versions!!.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无更新记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    item {
                        Text("共 ${versions!!.size} 个版本，按发布时间倒序",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                    }
                    items(versions!!) { v ->
                        VersionCard(v)
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionCard(v: AppVersion) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("v${v.versionName}", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (v.forceUpdate == 1) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text("强制更新", fontSize = 10.sp, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp)) {
                Text(if (v.createdAt > 0)
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(v.createdAt * 1000L))
                     else "发布时间未知",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (v.sizeText.isNotBlank()) {
                    Text("  ·  安装包 ${v.sizeText}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                v.changelog.ifBlank { "修复若干已知问题，提升使用体验。" },
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
