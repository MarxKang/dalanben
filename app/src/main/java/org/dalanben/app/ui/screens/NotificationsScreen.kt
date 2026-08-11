package org.dalanben.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.Notification
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.Avatar
import org.dalanben.app.ui.components.EmptyState
import org.dalanben.app.ui.components.LoadingMore
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.util.formatTime

private fun notifLabel(t: String?): String = when (t) {
    "like" -> "赞了你的作品"
    "comment" -> "评论了你的作品"
    "reply" -> "回复了你的评论"
    "at" -> "@了你"
    "follow" -> "关注了你"
    "collect" -> "收藏了你的作品"
    "share" -> "分享了你的作品"
    "comment_like" -> "赞了你的评论"
    "circle_join" -> "加入了你的圈子"
    "system" -> "系统通知"
    "report_result" -> "举报反馈"
    else -> "通知"
}

@Composable
fun NotificationsScreen(navController: NavController, appVm: AppViewModel) {
    val tabs = listOf("全部" to "all", "互动" to "interact", "系统" to "system")
    var tabIndex by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf(listOf<Notification>()) }
    var page by remember { mutableStateOf(1) }
    var end by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean = false) {
        if (reset) { items = emptyList(); page = 1; end = false }
        if (loading || end) return
        loading = true
        try {
            val r = Api.service.notifications(tabs[tabIndex].second, page, 20)
            val list = r.data?.list ?: emptyList()
            if (list.isEmpty()) end = true else {
                val existed = items.map { it.id }.toSet()
                items = items + list.filter { it.id !in existed }
                page += 1
            }
        } catch (_: Exception) { }
        loading = false
    }

    LaunchedEffect(tabIndex) {
        load(reset = true)
        // 进入即标记通知已读
        try { Api.service.readAll(mapOf("type" to "notif")); appVm.loadUnread() } catch (_: Exception) {}
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("互动通知", onBack = { navController.popBackStack() })
        TabRow(tabIndex) {
            tabs.forEachIndexed { i, (label, _) ->
                Tab(tabIndex == i, { tabIndex = i }, text = { Text(label) })
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(items, key = { _, n -> n.id }) { idx, n ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            when {
                                n.postId > 0 -> navController.navigate(Routes.postDetail(n.postId))
                                n.notifType == "follow" && n.fromUserId > 0 ->
                                    navController.navigate(Routes.profile(n.fromUserId))
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(n.avatarUrl, 42) {
                        if (n.fromUserId > 0) navController.navigate(Routes.profile(n.fromUserId))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.nickname ?: "系统", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(6.dp))
                            Text(notifLabel(n.notifType), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary)
                            if (n.readed == 0) {
                                Spacer(Modifier.width(6.dp))
                                Badge()
                            }
                        }
                        if (!n.content.isNullOrBlank()) {
                            Text(n.content!!, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Text(formatTime(n.createdAt), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
                if (idx >= items.lastIndex && !end && !loading) {
                    LaunchedEffect(items.size) { load() }
                }
            }
            if (loading) item { LoadingMore() }
            if (items.isEmpty() && !loading) item { EmptyState("暂无通知") }
        }
    }
}
