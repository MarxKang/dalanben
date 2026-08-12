package org.dalanben.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.util.CacheBreakdown
import org.dalanben.app.util.CacheCleaner

/**
 * 存储清理页: 深度分析 App 占用(图片缓存/临时文件/用户数据) + 一键清理可再生缓存
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheCleanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var breakdown by remember { mutableStateOf<CacheBreakdown?>(null) }
    var analyzing by remember { mutableStateOf(true) }
    var cleaning by remember { mutableStateOf(false) }
    var cleanedMsg by remember { mutableStateOf<String?>(null) }

    fun runAnalyze() {
        analyzing = true
        scope.launch {
            val b = withContext(Dispatchers.IO) { CacheCleaner.analyze(context) }
            breakdown = b
            analyzing = false
        }
    }

    LaunchedEffect(Unit) { runAnalyze() }

    Column(Modifier.fillMaxSize()) {
        TopBar("存储清理", onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 总占用卡片 ──
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (analyzing || breakdown == null) {
                        CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("正在深度扫描…", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val b = breakdown!!
                        Text(CacheCleaner.format(b.totalAll), fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("应用总占用", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { if (b.totalAll > 0) b.totalCache.toFloat() / b.totalAll else 0f },
                            modifier = Modifier.fillMaxWidth(0.7f).height(8.dp),
                            color = Color(0xFFF59E0B),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("其中 ${CacheCleaner.format(b.totalCache)} 为可清理缓存",
                            fontSize = 12.sp, color = Color(0xFFF59E0B))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 分类明细 ──
            Text("占用分析", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(6.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    val b = breakdown
                    StatRow("🖼  图片缓存", b?.imageCache ?: 0L, "浏览作品产生的图片缓存", Color(0xFF3B82F6))
                    Divider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    StatRow("📄  临时文件", b?.tempFiles ?: 0L, "下载/压缩产生的临时文件", Color(0xFF8B5CF6))
                    Divider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    StatRow("🗂  其它缓存", b?.otherCache ?: 0L, "系统运行时缓存", Color(0xFF10B981))
                    Divider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    StatRow("🔒  用户数据", b?.userData ?: 0L, "登录信息/数据库(不可清理)", Color(0xFF6B7280))
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 清理按钮 ──
            Button(
                onClick = {
                    cleaning = true
                    cleanedMsg = null
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) { CacheCleaner.clean(context) }
                        cleanedMsg = "已释放 ${CacheCleaner.format(freed)}"
                        cleaning = false
                        runAnalyze()
                    }
                },
                enabled = !analyzing && !cleaning,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
                    contentColor = Color.White
                )
            ) {
                if (cleaning) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("清理中…")
                } else {
                    Text("一键清理", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            cleanedMsg?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 13.sp, color = Color(0xFF10B981))
            }
            Spacer(Modifier.height(10.dp))
            Text("仅清理缓存与临时文件，不会删除登录状态、聊天记录或您的作品数据",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatRow(label: String, bytes: Long, hint: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(CacheCleaner.format(bytes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
