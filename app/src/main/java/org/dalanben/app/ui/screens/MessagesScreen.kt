package org.dalanben.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.ChatSession
import org.dalanben.app.data.shareContent
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.EmptyState
import org.dalanben.app.ui.components.Avatar
import org.dalanben.app.ui.components.PullRefreshWrapper
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.util.formatTime

@Composable
fun MessagesScreen(navController: NavController, appVm: AppViewModel) {
    var sessions by remember { mutableStateOf(listOf<ChatSession>()) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val unread by appVm.unread.collectAsState()

    suspend fun load() {
        try {
            val r = Api.service.chatList()
            sessions = r.data?.list ?: emptyList()
        } catch (_: Exception) { }
        loaded = true
    }

    LaunchedEffect(Unit) {
        load()
        appVm.loadUnread()
        while (true) { load(); delay(15000); appVm.loadUnread() }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("消息", actions = {
            IconButton({
                scope.launch {
                    try {
                        Api.service.readAll(mapOf("type" to "all"))
                        appVm.showToast("已全部标记已读")
                        appVm.loadUnread(); load()
                    } catch (_: Exception) { appVm.showToast("网络错误") }
                }
            }) { Icon(Icons.Filled.DoneAll, "全部已读") }
        })

        // 通知入口
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                .clickable { navController.navigate(Routes.NOTIFICATIONS) }
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BadgedBox(badge = {
                    if ((unread?.notif ?: 0) > 0) Badge { Text((unread?.notif ?: 0).toString()) }
                }) {
                    Icon(Icons.Filled.Notifications, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("互动通知", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("赞·评论·@·关注·系统通知", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("私信", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))

        PullRefreshWrapper(isRefreshing = refreshing, onRefresh = { refreshing = true; scope.launch { load(); refreshing = false } }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
            items(sessions, key = { it.peer?.id ?: 0 }) { s ->
                val peer = s.peer ?: return@items
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { navController.navigate(Routes.chat(peer.id)) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgedBox(badge = { if (s.unread > 0) Badge { Text(s.unread.toString()) } }) {
                        Avatar(peer.avatarUrl, 46)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(peer.nickname ?: "用户", fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        val preview = when {
                            s.lastMsg?.recalled == 1 -> "[已撤回]"
                            s.lastMsg?.msgType == "image" -> "[图片]"
                            s.lastMsg?.msgType == "share" ->
                                "[分享] " + (s.lastMsg?.shareContent()?.title ?: "")
                            else -> s.lastMsg?.content ?: ""
                        }
                        Text(preview, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatTime(s.lastMsg?.createdAt), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (loaded && sessions.isEmpty()) item { EmptyState("暂无私信会话") }
        }
        } // PullRefresh
    }
}
