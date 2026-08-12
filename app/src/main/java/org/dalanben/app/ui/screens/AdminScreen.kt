package org.dalanben.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.AppVersion
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.components.EmptyState
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.util.formatTime

private fun Any?.asMapList(): List<Map<String, Any?>> {
    val map = this as? Map<*, *> ?: return emptyList()
    val list = map["list"] as? List<*> ?: return emptyList()
    return list.mapNotNull { @Suppress("UNCHECKED_CAST") (it as? Map<String, Any?>) }
}

private fun Map<String, Any?>.num(key: String): Long =
    (this[key] as? Double)?.toLong() ?: (this[key] as? Long) ?: 0L

private fun penLabel(t: String?): String = mapOf(
    "mute_temp" to "临时禁言", "mute_perm" to "永久禁言",
    "limit_temp" to "临时限流", "limit_perm" to "永久限流",
    "ban_temp" to "临时封禁", "ban_perm" to "永久封禁"
)[t] ?: (t ?: "")

@Composable
fun AdminScreen(navController: NavController, appVm: AppViewModel) {
    val tabs = listOf("统计", "帖子审核", "评论审核", "举报", "申诉", "反馈", "精选审核", "版本发布")
    var tabIndex by remember { mutableStateOf(0) }
    var stats by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var appealTarget by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var appealActionName by remember { mutableStateOf("approve") }

    // 版本发布
    var vcInput by remember { mutableStateOf("") }
    var vnInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var logInput by remember { mutableStateOf("") }
    var forceUpdate by remember { mutableStateOf(false) }
    var curLatestVer by remember { mutableStateOf<AppVersion?>(null) }
    fun loadLatest() = scope.launch {
        try { curLatestVer = Api.service.latestAppVersion().data?.version } catch (_: Exception) { }
    }

    suspend fun load() {
        loading = true
        try {
            when (tabIndex) {
                0 -> {
                    val r = Api.service.adminStats()
                    @Suppress("UNCHECKED_CAST")
                    stats = r.data as? Map<String, Any?>
                }
                1 -> items = Api.service.adminReviewPosts("pending", 1, 30).data.asMapList()
                2 -> items = Api.service.adminReviewComments("pending", 1, 30).data.asMapList()
                3 -> items = Api.service.adminReports("pending").data.asMapList()
                4 -> items = Api.service.adminAppeals("pending").data.asMapList()
                5 -> items = Api.service.adminFeedback("pending").data.asMapList()
                6 -> items = Api.service.adminPostFeatureList("pending").data.asMapList()
            }
        } catch (e: Exception) { appVm.showToast("加载失败: ${e.message}") }
        loading = false
    }

    LaunchedEffect(tabIndex) { load() }

    fun act(action: suspend () -> Unit) = scope.launch {
        try { action(); load() } catch (_: Exception) { appVm.showToast("操作失败") }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("后台管理", onBack = { navController.popBackStack() })
        ScrollableTabRow(tabIndex, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, label ->
                Tab(tabIndex == i, { tabIndex = i }, text = { Text(label, fontSize = 13.sp) })
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        when (tabIndex) {
            0 -> stats?.let { s ->
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = listOf(
                        "总用户" to s.num("user_total"), "今日新增用户" to s.num("user_today"),
                        "今日活跃" to s.num("user_active_today"), "月活跃" to s.num("user_active_month"),
                        "总帖子" to s.num("post_total"), "今日新帖" to s.num("post_today"),
                        "待审帖子" to s.num("post_pending"), "总评论" to s.num("comment_total"),
                        "待审评论" to s.num("comment_pending"), "话题数" to s.num("topic_total"),
                        "待处理举报" to s.num("report_pending"), "待处理申诉" to s.num("appeal_pending"),
                        "待处理反馈" to s.num("feedback_pending")
                    )
                    items(rows.chunked(2)) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (label, n) ->
                                Card(Modifier.weight(1f)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(n.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary)
                                        Text(label, fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            1 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { p ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${p.num("id")} ${p["nickname"] ?: ""} · ${formatTime(p.num("created_at"))}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${p["title"] ?: "(无标题)"}", fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text("${p["content"] ?: ""}", fontSize = 13.sp, maxLines = 3,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val tags = (p["ai_tags"] as? List<*>)?.joinToString(",") ?: ""
                            if (tags.isNotBlank()) Text("AI标签: $tags", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button({ act { Api.service.adminPostAction(mapOf(
                                    "post_id" to p.num("id"), "action" to "approve")) } },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("通过") }
                                OutlinedButton({ act { Api.service.adminPostAction(mapOf(
                                    "post_id" to p.num("id"), "action" to "reject")) } },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("驳回") }
                                OutlinedButton({ act { Api.service.adminPostAction(mapOf(
                                    "post_id" to p.num("id"), "action" to "delete")) } },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) {
                                    Text("删除", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                if (items.isEmpty() && !loading) item { EmptyState("暂无待审帖子") }
            }
            2 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { c ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${c.num("id")} ${c["nickname"] ?: ""} · ${formatTime(c.num("created_at"))}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${c["content"] ?: ""}", fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button({ act { Api.service.adminCommentAction(mapOf(
                                    "comment_id" to c.num("id"), "action" to "approve")) } },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("通过") }
                                OutlinedButton({ act { Api.service.adminCommentAction(mapOf(
                                    "comment_id" to c.num("id"), "action" to "reject")) } },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("驳回") }
                            }
                        }
                    }
                }
                if (items.isEmpty() && !loading) item { EmptyState("暂无待审评论") }
            }
            3 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { rp ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("举报 #${rp.num("id")} · 类型: ${rp["target_type"]} · ${formatTime(rp.num("created_at"))}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("原因: ${rp["reason"] ?: ""}", fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button({ act { Api.service.adminReportAction(mapOf(
                                    "report_id" to rp.num("id"), "action" to "reviewed")) } },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("已处理") }
                                OutlinedButton({ act { Api.service.adminReportAction(mapOf(
                                    "report_id" to rp.num("id"), "action" to "dismissed")) } },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("驳回") }
                            }
                        }
                    }
                }
                if (items.isEmpty() && !loading) item { EmptyState("暂无待处理举报") }
            }
            4 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { ap ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            val typ = ap["appeal_type"] as? String ?: ""
                            val typLabel = mapOf("post" to "作品", "account" to "账号")[typ] ?: typ
                            Text("申诉 #${ap.num("id")} · $typLabel · ${formatTime(ap.num("created_at"))}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (typ == "account") {
                                val pt = (ap["pen_type"] as? String)?.takeIf { it.isNotBlank() }
                                val pr = (ap["pen_reason"] as? String)?.takeIf { it.isNotBlank() }
                                if (pt != null) Text("处罚类型: ${penLabel(pt)}", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error)
                                if (pr != null) Text("处罚原因: $pr", fontSize = 13.sp)
                            }
                            Text("${ap["content"] ?: ""}", fontSize = 14.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button({ appealTarget = ap; appealActionName = "approve" },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("通过") }
                                OutlinedButton({ appealTarget = ap; appealActionName = "reject" },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("驳回") }
                            }
                        }
                    }
                }
                if (items.isEmpty() && !loading) item { EmptyState("暂无待处理申诉") }
            }
            5 -> {
                var replyTarget by remember { mutableStateOf<Map<String, Any?>?>(null) }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { fb ->
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Text("反馈 #${fb.num("id")} · ${fb["fb_type"]} · ${formatTime(fb.num("created_at"))}",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${fb["content"] ?: ""}", fontSize = 14.sp)
                                if (!(fb["contact"] as? String).isNullOrBlank()) {
                                    Text("联系方式: ${fb["contact"]}", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(6.dp))
                                Button({ replyTarget = fb },
                                    contentPadding = PaddingValues(horizontal = 14.dp)) { Text("回复") }
                            }
                        }
                    }
                    if (items.isEmpty() && !loading) item { EmptyState("暂无待处理反馈") }
                }
                replyTarget?.let { fb ->
                    var replyText by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { replyTarget = null },
                        title = { Text("回复反馈") },
                        text = {
                            Column(Modifier.imePadding()) {
                                OutlinedTextField(replyText, { replyText = it },
                                    label = { Text("回复内容") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                            }
                        },
                        confirmButton = {
                            TextButton({
                                if (replyText.isBlank()) return@TextButton
                                act { Api.service.adminFeedbackReply(mapOf(
                                    "feedback_id" to fb.num("id"), "reply" to replyText)) }
                                replyTarget = null
                            }) { Text("发送") }
                        },
                        dismissButton = { TextButton({ replyTarget = null }) { Text("取消") } }
                    )
                }
            }
            6 -> {
                var rejectTarget by remember { mutableStateOf<Map<String, Any?>?>(null) }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { r ->
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Text("申请 #${r.num("id")} · ${r["nickname"] ?: ""} · ${formatTime(r.num("created_at"))}",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${r["title"] ?: "(无标题)"}", fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text("${r["content"] ?: ""}", fontSize = 13.sp, maxLines = 3,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val reason = (r["reason"] as? String)?.takeIf { it.isNotBlank() }
                                if (reason != null) Text("申请理由: $reason", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button({ act { Api.service.adminPostFeatureAction(mapOf(
                                        "id" to r.num("id"), "action" to "approve")) } },
                                        contentPadding = PaddingValues(horizontal = 14.dp)) { Text("设为精选") }
                                    OutlinedButton({ rejectTarget = r },
                                        contentPadding = PaddingValues(horizontal = 14.dp)) {
                                        Text("驳回", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                    if (items.isEmpty() && !loading) item { EmptyState("暂无待审精选申请") }
                }
                rejectTarget?.let { r ->
                    var note by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { rejectTarget = null },
                        title = { Text("驳回精选申请") },
                        text = {
                            Column(Modifier.imePadding()) {
                                Text("驳回 #${r.num("id")} · ${r["title"] ?: ""}", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(note, { note = it },
                                    label = { Text("驳回理由(可选)") },
                                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                            }
                        },
                        confirmButton = {
                            TextButton({
                                act { Api.service.adminPostFeatureAction(mapOf(
                                    "id" to r.num("id"), "action" to "reject", "note" to note)) }
                                rejectTarget = null
                            }) { Text("确认驳回") }
                        },
                        dismissButton = { TextButton({ rejectTarget = null }) { Text("取消") } }
                    )
                }
            }
            7 -> {
                LaunchedEffect(Unit) { loadLatest() }
                LazyColumn(
                    Modifier.weight(1f).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Card {
                            Column(Modifier.padding(14.dp)) {
                                Text("当前线上版本", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                if (curLatestVer != null) {
                                    Text("v${curLatestVer!!.versionName} (code ${curLatestVer!!.versionCode})",
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    if (!curLatestVer!!.changelog.isBlank())
                                        Text(curLatestVer!!.changelog, fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (curLatestVer!!.forceUpdate == 1)
                                        Text("强制更新已开启", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("暂无已发布版本", fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item {
                        Card {
                            Column(Modifier.padding(14.dp)) {
                                Text("发布新版本", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(vcInput, { vcInput = it },
                                    label = { Text("版本号(versionCode, 整数)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(vnInput, { vnInput = it },
                                    label = { Text("版本名称(如 26.7.31)") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(urlInput, { urlInput = it },
                                    label = { Text("下载地址(浏览器打开链接)") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(logInput, { logInput = it },
                                    label = { Text("更新说明(选填)") },
                                    modifier = Modifier.fillMaxWidth(), minLines = 3)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text("强制更新", fontSize = 14.sp)
                                        Text("开启后旧版本必须更新才能继续使用", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(forceUpdate, { forceUpdate = it })
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val vc = vcInput.toIntOrNull() ?: 0
                                        scope.launch {
                                            val r = Api.service.adminPublishVersion(mapOf(
                                                "version_code" to vc,
                                                "version_name" to vnInput.trim(),
                                                "download_url" to urlInput.trim(),
                                                "changelog" to logInput.trim(),
                                                "force_update" to forceUpdate
                                            ))
                                            if (r.ok) {
                                                appVm.showToast("已发布新版本")
                                                vcInput = ""; vnInput = ""; urlInput = ""; logInput = ""; forceUpdate = false
                                                loadLatest()
                                            } else appVm.showToast(r.msg ?: "发布失败")
                                        }
                                    },
                                    enabled = vcInput.toIntOrNull() != null && vnInput.isNotBlank() && urlInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("发布新版本") }
                            }
                        }
                    }
                }
            }
        }
        appealTarget?.let { ap ->
            var note by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { appealTarget = null },
                title = { Text(if (appealActionName == "approve") "通过申诉" else "驳回申诉") },
                text = {
                    Column(Modifier.imePadding()) {
                        Text("可填写处理意见（可选），将通知申诉人。", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(note, { note = it },
                            label = { Text("处理意见") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                },
                confirmButton = {
                    TextButton({
                        act { Api.service.adminAppealAction(mapOf(
                            "appeal_id" to ap.num("id"), "action" to appealActionName, "note" to note)) }
                        appealTarget = null
                    }) { Text("确定") }
                },
                dismissButton = { TextButton({ appealTarget = null }) { Text("取消") } }
            )
        }
    }
}
