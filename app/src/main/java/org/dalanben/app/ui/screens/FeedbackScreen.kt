package org.dalanben.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dalanben.app.data.Api
import org.dalanben.app.data.Feedback
import org.dalanben.app.data.uploadFile
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.ui.components.UploadProgressBarRow
import org.dalanben.app.util.compressImage
import org.dalanben.app.util.compressVideo
import org.dalanben.app.util.formatTime
import org.dalanben.app.util.fullUrl
import java.io.File

@Composable
fun FeedbackScreen(navController: NavController, appVm: AppViewModel) {
    var fbType by remember { mutableStateOf("bug") }
    var content by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var myFeedback by remember { mutableStateOf(listOf<Feedback>()) }
    // 附件: 图片(最多9) / 视频(最多3, 每项含 url 与封面)
    var imageUrls by remember { mutableStateOf(listOf<String>()) }
    var videoItems by remember { mutableStateOf(listOf<Pair<String, String?>>()) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    var phaseLabel by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uploading = true
        progress = -1f
        phaseLabel = "压缩中"
        scope.launch {
            var ok = 0
            for (uri in uris.take(9 - imageUrls.size)) {
                // 先压缩到 600KB 以内再上传
                val res = withContext(Dispatchers.IO) { compressImage(context, uri, "fb_img") }
                if (res.error != null) { appVm.showToast(res.error); continue }
                val f = res.file ?: continue
                phaseLabel = "上传中"
                try {
                    val r = withContext(Dispatchers.IO) {
                        Api.service.uploadFile<Any>("feedback", f) { progress = it }
                    }
                    r.url?.let { imageUrls = imageUrls + it; ok++ }
                } catch (e: Exception) {
                    appVm.showToast("图片上传失败: ${e.message}")
                } finally { f.delete() }
            }
            uploading = false
            progress = -1f
            phaseLabel = ""
            if (ok > 0) appVm.showToast("已添加 $ok 张图片")
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        progress = -1f
        phaseLabel = "压缩中"
        scope.launch {
            // 压缩: 720p / 25fps / 50MB 以内; 过长则提示分段上传
            val res = withContext(Dispatchers.IO) {
                compressVideo(context, uri, "fb_video") { p -> progress = p.toFloat() }
            }
            if (res.error != null) {
                uploading = false; progress = -1f; phaseLabel = ""
                appVm.showToast(res.error)
                return@launch
            }
            val f = res.file ?: run { uploading = false; progress = -1f; phaseLabel = ""; return@launch }
            phaseLabel = "上传中"
            try {
                val r = withContext(Dispatchers.IO) {
                    Api.service.uploadFile<Any>("feedback_video", f) { progress = it }
                }
                r.url?.let {
                    videoItems = videoItems + (it to r.coverUrl)
                    appVm.showToast("视频已添加")
                }
            } catch (e: Exception) {
                appVm.showToast("视频上传失败: ${e.message}")
            } finally { f.delete(); uploading = false; progress = -1f; phaseLabel = "" }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun loadMine() {
        try {
            val r = Api.service.myFeedback(1, 20)
            myFeedback = r.data?.list ?: emptyList()
        } catch (_: Exception) { }
    }

    LaunchedEffect(Unit) { loadMine() }

    Column(Modifier.fillMaxSize()) {
        TopBar("意见反馈", onBack = { navController.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).imePadding()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("bug" to "问题反馈", "suggest" to "功能建议", "other" to "其他").forEach { (v, label) ->
                    FilterChip(fbType == v, { fbType = v }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(content, { content = it },
                label = { Text("请详细描述你的问题或建议") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 4)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(contact, { contact = it },
                label = { Text("联系方式(选填)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            // ─── 添加图片 / 视频 ───
            Spacer(Modifier.height(16.dp))
            Text("添加图片 / 视频（选填，${imageUrls.size}/9 张图片、${videoItems.size}/3 个视频，分别限制）",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(imageUrls) { url ->
                    Box(Modifier.size(72.dp)) {
                        AsyncImage(fullUrl(url), null,
                            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop)
                        IconButton({ imageUrls = imageUrls - url },
                            modifier = Modifier.align(Alignment.TopEnd).size(22.dp)) {
                            Icon(Icons.Filled.Close, "删除", tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (imageUrls.size < 9) item {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { if (!uploading) imagePicker.launch("image/*") }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Image, "添加图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(videoItems) { (url, cover) ->
                    Box(Modifier.size(72.dp)) {
                        AsyncImage(fullUrl(cover ?: url), null,
                            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop)
                        Icon(Icons.Filled.PlayArrow, "视频",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Center).size(28.dp))
                        IconButton({ videoItems = videoItems - (url to cover) },
                            modifier = Modifier.align(Alignment.TopEnd).size(22.dp)) {
                            Icon(Icons.Filled.Close, "删除",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (videoItems.size < 3) item {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { if (!uploading) videoPicker.launch("video/*") }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Videocam, "添加视频",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (uploading) {
                Spacer(Modifier.height(8.dp))
                UploadProgressBarRow(uploading = uploading, progress = progress, barWidth = 140.dp, label = phaseLabel)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (content.isBlank()) { appVm.showToast("请填写反馈内容"); return@Button }
                    submitting = true
                    scope.launch {
                        try {
                            val r = Api.service.feedback(mapOf(
                                "fb_type" to fbType, "content" to content, "contact" to contact,
                                "image_urls" to imageUrls, "video_urls" to videoItems.map { it.first }))
                            if (r.ok) {
                                appVm.showToast("反馈已提交, 感谢支持!")
                                content = ""; contact = ""; imageUrls = emptyList(); videoItems = emptyList()
                                loadMine()
                            } else appVm.showToast(r.msg ?: "提交失败")
                        } catch (_: Exception) { appVm.showToast("网络错误") }
                        submitting = false
                    }
                },
                enabled = !submitting && !uploading,
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text("提交反馈") }

            Spacer(Modifier.height(24.dp))
            Text("我的反馈记录", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            if (myFeedback.isEmpty()) {
                Text("暂无反馈记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            myFeedback.forEach { fb ->
                FeedbackCard(fb)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeedbackCard(fb: Feedback) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    when (fb.fbType) {
                        "bug" -> "问题反馈"; "suggest" -> "功能建议"; else -> "其他"
                    },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    when (fb.status) {
                        "replied" -> "已回复"; "pending" -> "待处理"; else -> fb.status
                    },
                    fontSize = 12.sp,
                    color = if (fb.status == "replied") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(fb.content, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            // 历史附件
            val imgs = fb.imageUrls
            val vids = fb.videoUrls
            if (imgs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(imgs) { u ->
                        AsyncImage(fullUrl(u), null,
                            Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop)
                    }
                }
            }
            if (vids.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vids) { u ->
                        Box(Modifier.size(64.dp)) {
                            AsyncImage(fullUrl(u), null,
                                Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop)
                            Icon(Icons.Filled.PlayArrow, "视频",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.Center).size(24.dp))
                        }
                    }
                }
            }
            val reply = fb.adminReply
            if (!reply.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small) {
                    Text("官方回复: $reply", fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(formatTime(fb.createdAt),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
