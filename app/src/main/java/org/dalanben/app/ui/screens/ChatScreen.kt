package org.dalanben.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dalanben.app.data.Api
import org.dalanben.app.data.Message
import org.dalanben.app.data.ShareContent
import org.dalanben.app.data.shareContent
import org.dalanben.app.data.Session
import org.dalanben.app.data.User
import org.dalanben.app.data.uploadFile
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.Avatar
import org.dalanben.app.ui.components.EmojiPanel
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.util.compressImage
import org.dalanben.app.util.fullUrl
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(navController: NavController, appVm: AppViewModel, peerId: Int) {
    var peer by remember { mutableStateOf<User?>(null) }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showChatEmoji by remember { mutableStateOf(false) }
    var imgUploading by remember { mutableStateOf(false) }
    var imgProgress by remember { mutableStateOf(-1f) }
    var imgPhaseLabel by remember { mutableStateOf("") }
    var actionMsg by remember { mutableStateOf<Message?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chatKeyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val myId = Session.user?.id ?: 0

    suspend fun load() {
        try {
            val r = Api.service.chatHistory(peerId, 1, 50)
            val list = (r.data?.list ?: emptyList()).sortedBy { it.createdAt }
            if (list.size != messages.size || list.lastOrNull()?.id != messages.lastOrNull()?.id) {
                messages = list
            }
        } catch (_: Exception) { }
    }

    LaunchedEffect(peerId) {
        try {
            val r = Api.service.profile(peerId)
            if (r.ok) peer = r.data
        } catch (_: Exception) { }
        load()
        // 后端已在 chat_history 中将该会话标记已读，进入聊天立即刷新底部红点
        appVm.loadUnread()
        while (true) { load(); delay(5000) }
    }

    // 以最后一条消息 id 作为滚动触发键：发送/对方来信/自己撤回后均会变化，确保自动滚到底
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    // 退出聊天页时再刷新一次红点，确保已读状态立即反映到底部导航
    DisposableEffect(Unit) {
        onDispose { appVm.loadUnread() }
    }

    fun sendText() {
        if (input.isBlank()) return
        sending = true
        scope.launch {
            try {
                val r = Api.service.sendMsg(mapOf(
                    "to_user_id" to peerId, "msg_type" to "text", "content" to input))
                if (r.ok) { input = ""; load() }
                else appVm.showToast(r.msg ?: "发送失败")
            } catch (_: Exception) { appVm.showToast("网络错误") }
            sending = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            imgUploading = true
            imgProgress = -1f
            imgPhaseLabel = "压缩中"
            try {
                // 先压缩到 600KB 以内再上传
                val res = withContext(Dispatchers.IO) { compressImage(context, uri, "chat") }
                if (res.error != null) { appVm.showToast(res.error); return@launch }
                val f = res.file ?: return@launch
                imgPhaseLabel = "上传中"
                val up = withContext(Dispatchers.IO) {
                    Api.service.uploadFile<Any>("msg_img", f) { imgProgress = it }
                }
                f.delete()
                up.url?.let {
                    val r = Api.service.sendMsg(mapOf(
                        "to_user_id" to peerId, "msg_type" to "image", "image_url" to it))
                    if (r.ok) load() else appVm.showToast(r.msg ?: "发送失败")
                }
            } catch (e: Exception) { appVm.showToast("发送失败: ${e.message}") }
            imgUploading = false
            imgProgress = -1f
            imgPhaseLabel = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(peer?.nickname ?: "私信", onBack = { navController.popBackStack() })

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 10.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { m ->
                val mine = m.senderId == myId
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    if (!mine) {
                        Avatar(peer?.avatarUrl, 34) { navController.navigate(Routes.profile(peerId)) }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (m.msgType == "share") {
                        val sc = m.shareContent()
                        if (sc != null) {
                            ShareCardBubble(sc, mine) {
                                val route = when (sc.shareType) {
                                    "topic" -> Routes.topicDetail(sc.targetId)
                                    "user" -> Routes.profile(sc.targetId)
                                    else -> Routes.postDetail(sc.targetId)
                                }
                                navController.navigate(route)
                            }
                        } else {
                            Text("[分享]", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp))
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 12.dp, topEnd = 12.dp,
                                bottomStart = if (mine) 12.dp else 2.dp,
                                bottomEnd = if (mine) 2.dp else 12.dp
                            ),
                            color = if (mine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (m.msgType == "image" && !m.imageUrl.isNullOrBlank())
                                            navController.navigate(Routes.image(fullUrl(m.imageUrl) ?: ""))
                                    },
                                    onLongClick = { actionMsg = m }
                                )
                        ) {
                            when {
                                m.recalled == 1 -> Text("[已撤回]", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp))
                                m.msgType == "image" -> AsyncImage(
                                    fullUrl(m.imageUrl), null,
                                    modifier = Modifier.size(160.dp).clip(RoundedCornerShape(8.dp))
                                )
                                else -> Text(
                                    m.content ?: "", fontSize = 14.sp,
                                    color = if (mine) androidx.compose.ui.graphics.Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    if (mine) {
                        Spacer(Modifier.width(6.dp))
                        Avatar(Session.user?.avatarUrl, 34)
                    }
                }
            }
        }

        if (imgUploading) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                if (imgProgress >= 0f) LinearProgressIndicator(
                    progress = (imgProgress / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                ) else LinearProgressIndicator(Modifier.fillMaxWidth())
                if (imgPhaseLabel.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (imgProgress >= 0f) "$imgPhaseLabel ${imgProgress.toInt()}%" else imgPhaseLabel,
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Surface(shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).imePadding().navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (!imgUploading) imagePicker.launch("image/*") }) {
                    if (imgUploading) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Image, "发图片", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = {
                    showChatEmoji = !showChatEmoji
                    if (showChatEmoji) chatKeyboard?.hide()
                }, Modifier.size(38.dp)) {
                    Icon(Icons.Filled.SentimentSatisfied, "表情包",
                        tint = if (showChatEmoji) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
                OutlinedTextField(
                    input, { input = it },
                    placeholder = { Text("发消息...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    maxLines = 3
                )
                IconButton(onClick = { sendText() }, enabled = !sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, "发送",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (showChatEmoji) {
                EmojiPanel(
                    onPick = { e ->
                        input += e
                        showChatEmoji = false
                    },
                    onDismiss = { showChatEmoji = false }
                )
            }
        }
    }

    // 消息长按操作
    actionMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { actionMsg = null },
            title = { Text("消息操作") },
            text = {
                Column {
                    if (m.senderId == myId && m.recalled == 0) {
                        TextButton({
                            actionMsg = null
                            scope.launch {
                                try {
                                    val r = Api.service.recallMsg(mapOf("msg_id" to m.id))
                                    if (r.ok) {
                                        // 撤回成功立即本地更新，避免整列表重新拉取造成的延迟与闪烁
                                        messages = messages.map {
                                            if (it.id == m.id) it.copy(recalled = 1, content = null, imageUrl = null)
                                            else it
                                        }
                                    } else appVm.showToast(r.msg ?: "撤回失败(超过2分钟不可撤回)")
                                } catch (_: Exception) { appVm.showToast("网络错误") }
                            }
                        }, Modifier.fillMaxWidth()) { Text("撤回") }
                    }
                    TextButton({
                        actionMsg = null
                        scope.launch {
                            try {
                                val r = Api.service.deleteOneMsg(mapOf("msg_id" to m.id))
                                if (r.ok) load() else appVm.showToast(r.msg ?: "删除失败")
                            } catch (_: Exception) { appVm.showToast("网络错误") }
                        }
                    }, Modifier.fillMaxWidth()) { Text("删除") }
                    TextButton({
                        actionMsg = null
                        scope.launch {
                            try {
                                val r = Api.service.clearChat(mapOf("peer_id" to peerId))
                                if (r.ok) { messages = emptyList(); appVm.showToast("已清空") }
                            } catch (_: Exception) { appVm.showToast("网络错误") }
                        }
                    }, Modifier.fillMaxWidth()) { Text("清空聊天记录", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ actionMsg = null }) { Text("取消") } }
        )
    }
}

/** 私信中的分享卡片气泡(双方均为卡片形式) */
@Composable
fun ShareCardBubble(
    sc: ShareContent,
    mine: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.widthIn(max = 260.dp).clickable { onClick() }
    ) {
        Column {
            if (!sc.cover.isNullOrBlank()) {
                AsyncImage(
                    fullUrl(sc.cover), null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    sc.title ?: "分享内容",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (!sc.desc.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        sc.desc!!, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Share, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("分享", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
