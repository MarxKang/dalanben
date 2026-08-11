package org.dalanben.app.ui.components

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.Session
import org.dalanben.app.data.ShareContent
import org.dalanben.app.data.User
import org.dalanben.app.data.gson
import org.dalanben.app.ui.AppViewModel

/** 站内分享选择联系人底部弹层: 列出聊过天的人 + 我的关注(联系人), 支持搜索;
 *  选择后发送 msg_type='share' 私信卡片; 亦提供「分享到其它应用」外部分享入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    payload: ShareContent,
    appVm: AppViewModel,
    externalText: String? = null,
    copyText: String? = null,
    onDismiss: () -> Unit,
    onShared: (User) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(listOf<User>()) }
    var loaded by remember { mutableStateOf(false) }
    var sendingId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        try {
            val myId = Session.user?.id ?: 0
            val chatPeers = try {
                Api.service.chatList().data?.list?.mapNotNull { it.peer } ?: emptyList()
            } catch (_: Exception) { emptyList() }
            val following = try {
                Api.service.followList(myId, "following", 1, 100).data?.list ?: emptyList()
            } catch (_: Exception) { emptyList() }
            contacts = (chatPeers + following).distinctBy { it.id }
        } catch (_: Exception) { }
        loaded = true
    }

    val filtered = if (query.isBlank()) contacts else contacts.filter {
        (it.nickname ?: "").contains(query, ignoreCase = true) ||
        (it.blueId ?: "").contains(query, ignoreCase = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
        if (!copyText.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("invite_link", copyText))
                    appVm.showToast("已复制邀请链接")
                }.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Link, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("复制邀请链接", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text("分享给...", fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("搜索昵称 / 蓝号", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(6.dp))

        if (!externalText.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        if (payload.shareType == "post") {
                            try { Api.service.share(mapOf("post_id" to payload.targetId)) } catch (_: Exception) {}
                        }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, externalText)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享到"))
                    onDismiss()
                }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("分享到其它应用", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(filtered, key = { it.id }) { u ->
                val isSending = sendingId == u.id
                UserRow(u, onClick = {
                    if (isSending) return@UserRow
                    sendingId = u.id
                    scope.launch {
                        try {
                            val r = Api.service.sendMsg(mapOf(
                                "to_user_id" to u.id,
                                "msg_type" to "share",
                                "content" to gson.toJson(payload)
                            ))
                            if (r.ok) {
                                if (payload.shareType == "post") {
                                    try { Api.service.share(mapOf("post_id" to payload.targetId)) } catch (_: Exception) {}
                                }
                                appVm.showToast("已分享给 ${u.nickname ?: "对方"}")
                                onShared(u)
                                onDismiss()
                            } else {
                                appVm.showToast(r.msg ?: "分享失败")
                            }
                        } catch (_: Exception) {
                            appVm.showToast("网络错误")
                        }
                        sendingId = null
                    }
                }, trailing = {
                    if (isSending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                })
            }
            if (loaded && filtered.isEmpty()) item {
                EmptyState(if (contacts.isEmpty()) "暂无可分享的联系人" else "无匹配联系人")
            }
        }
        Spacer(Modifier.height(24.dp))
        }
    }
}
