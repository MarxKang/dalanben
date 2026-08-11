package org.dalanben.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.LevelTier
import org.dalanben.app.data.PointsData
import org.dalanben.app.data.PointsHistoryItem
import org.dalanben.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsCenterScreen(navController: NavController, appVm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var pointsData by remember { mutableStateOf<PointsData?>(null) }
    var history by remember { mutableStateOf(listOf<PointsHistoryItem>()) }
    var levels by remember { mutableStateOf(listOf<LevelTier>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val pr = Api.service.points()
            if (pr.ok) pointsData = pr.data
            val hr = Api.service.pointsHistory(1, 20)
            if (hr.ok) {
                val histType = object : TypeToken<List<PointsHistoryItem>>() {}.type
                val list: List<PointsHistoryItem> = Gson().fromJson(
                    Gson().toJson(hr.data?.get("list")), histType
                )
                history = list
            }
            val lr = Api.service.pointsLevels()
            if (lr.ok) {
                val type = object : TypeToken<List<LevelTier>>() {}.type
                val list: List<LevelTier> = Gson().fromJson(
                    Gson().toJson(lr.data?.get("levels")), type
                )
                levels = list
            }
        } catch (_: Exception) {}
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("积分中心") },
                navigationIcon = {
                    IconButton({ navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                val pd = pointsData

                // ── 当前积分 ──
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("当前积分", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("${pd?.points ?: 0}", fontSize = 36.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("今日获得", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("+${pd?.todayEarned ?: 0}", fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("连续签到", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${pd?.checkinStreak ?: 0}天", fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("已邀请", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${pd?.inviteCount ?: 0}人", fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── 邀请统计 ──
                if (pd != null) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("邀请统计", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("当前邀请了 ${pd.inviteCount} 名用户并注册，获得了 ${pd.inviteEarnedPoints} 积分",
                                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("+${pd.inviteEarnedPoints}", fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // ── 等级进度 ──
                if (pd != null) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Lv.${pd.level} ${pd.levelTitle}", fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    if (pd.nextLevelTitle != null) {
                                        Text("下一级: ${pd.nextLevelTitle}", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                val progress = if (pd.nextLevelPoints != null && pd.nextLevelPoints > 0) {
                                    val cur = pd.nextLevelPoints - pd.pointsToNext
                                    cur.toFloat() / pd.nextLevelPoints.toFloat()
                                } else 1f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(10.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                if (pd.nextLevelPoints != null) {
                                    Text("还需 ${pd.pointsToNext} 积分升级", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("已达最高等级 🎉", fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // ── 积分记录 ──
                item {
                    Text("积分记录", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                }
                if (history.isEmpty()) {
                    item {
                        Text("暂无记录", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(history) { h ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.reason, fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text(formatTimestamp(h.createdAt), fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("+${h.pointsChange}", fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                            }
                        }
                    }
                }

                // ── 全部等级段位 ──
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("全部等级段位", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                }
                items(levels) { lv ->
                    val isCurrent = pd?.level == lv.level
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = if (isCurrent) CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else CardDefaults.cardColors()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Lv.${lv.level}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(50.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface)
                            Text(lv.title, fontSize = 14.sp, modifier = Modifier.weight(1f),
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface)
                            Text("${lv.threshold}积分", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isCurrent) {
                                Spacer(Modifier.width(6.dp))
                                Text("当前", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    if (ts <= 0) return ""
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts * 1000))
}
