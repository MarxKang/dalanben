package org.dalanben.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.dalanben.app.data.Api
import org.dalanben.app.data.Draft
import org.dalanben.app.data.Topic
import org.dalanben.app.data.uploadFile
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.ui.components.UploadProgressBarRow
import org.dalanben.app.util.compressAudio
import org.dalanben.app.util.compressImage
import org.dalanben.app.util.compressVideo
import org.dalanben.app.util.copyUriToCacheFile
import org.dalanben.app.util.fullUrl
import org.dalanben.app.util.splitLongImage
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(navController: NavController, appVm: AppViewModel, circleId: Int = 0) {
    var postType by remember { mutableStateOf("article") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var mediaUrls by remember { mutableStateOf(listOf<String>()) }
    var coverUrl by remember { mutableStateOf("") }
    var musicUrl by remember { mutableStateOf("") }
    // 话题(参考网页版: 标签 + 联想 + 创建)
    var topics by remember { mutableStateOf(listOf<String>()) }
    var topicInput by remember { mutableStateOf("") }
    var topicSuggestions by remember { mutableStateOf(listOf<Topic>()) }
    var showTopicSuggest by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf(0) }
    var allowDownload by remember { mutableStateOf(true) }
    var watermark by remember { mutableStateOf(true) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(-1f) }
    var phaseLabel by remember { mutableStateOf("") }
    // 背景音乐使用独立的上传状态, 避免与图片/视频共用 uploading 互相干扰或卡住
    var musicUploading by remember { mutableStateOf(false) }
    var musicProgress by remember { mutableStateOf(-1f) }
    var musicPhase by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var phoneRequiredDialog by remember { mutableStateOf<String?>(null) }
    var showDrafts by remember { mutableStateOf(false) }
    var drafts by remember { mutableStateOf(listOf<Draft>()) }
    var draftsLoading by remember { mutableStateOf(false) }
    var draftId by remember { mutableStateOf(0) }
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
            var okCount = 0
            var splitHappened = false
            for (uri in uris.take(9 - mediaUrls.size)) {
                // 长图自动按合适高度切片为多条, 分别压缩上传
                val slices = withContext(Dispatchers.IO) { splitLongImage(context, uri, "img") }
                val targets = if (slices.isNotEmpty()) { splitHappened = true; slices } else listOf(uri)
                try {
                    for (t in targets) {
                        if (mediaUrls.size >= 9) break
                        phaseLabel = "压缩中"
                        val res = withContext(Dispatchers.IO) { compressImage(context, t, "img") }
                        if (res.error != null) { appVm.showToast(res.error); continue }
                        val f = res.file ?: continue
                        phaseLabel = "上传中"
                        try {
                            val r = withContext(Dispatchers.IO) {
                                Api.service.uploadFile<Any>("post_image", f) { progress = it }
                            }
                            r.url?.let { mediaUrls = mediaUrls + it; okCount++ }
                        } catch (e: Exception) {
                            appVm.showToast("上传失败: ${e.message}")
                        } finally { f.delete() }
                    }
                } finally {
                    // 清理切片产生的临时文件
                    slices.forEach { u -> if (u.scheme == "file") try { File(u.path).delete() } catch (_: Exception) {} }
                }
            }
            uploading = false
            progress = -1f
            phaseLabel = ""
            if (okCount > 0) appVm.showToast(
                if (splitHappened) "已上传 $okCount 张图片（含长图自动分割）" else "已上传 $okCount 张图片"
            )
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
                compressVideo(context, uri, "video") { p -> progress = p.toFloat() }
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
                    Api.service.uploadFile<Any>("post_video", f) { progress = it }
                }
                r.url?.let {
                    mediaUrls = listOf(it)
                    coverUrl = r.coverUrl ?: ""
                    appVm.showToast("视频上传成功")
                }
            } catch (e: Exception) {
                appVm.showToast("视频上传失败: ${e.message}")
            } finally { f.delete(); uploading = false; progress = -1f; phaseLabel = "" }
        }
    }

    // 文章封面: 单张图片
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        progress = -1f
        phaseLabel = "压缩中"
        scope.launch {
            val res = withContext(Dispatchers.IO) { compressImage(context, uri, "cover") }
            if (res.error != null) { uploading = false; progress = -1f; phaseLabel = ""; appVm.showToast("读取图片失败"); return@launch }
            val f = res.file ?: run { uploading = false; progress = -1f; phaseLabel = ""; return@launch }
            phaseLabel = "上传中"
            try {
                val r = withContext(Dispatchers.IO) {
                    Api.service.uploadFile<Any>("post_image", f) { progress = it }
                }
                r.url?.let { coverUrl = it; appVm.showToast("封面已上传") }
            } catch (e: Exception) {
                appVm.showToast("封面上传失败: ${e.message}")
            } finally { f.delete(); uploading = false; progress = -1f; phaseLabel = "" }
        }
    }

    // 背景音乐: 支持 article / image 类型
    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        musicUploading = true
        musicProgress = -1f
        musicPhase = "准备中"
        scope.launch {
            val res = withContext(Dispatchers.IO) { compressAudio(context, uri, "music") }
            if (res.error != null) { musicUploading = false; musicProgress = -1f; musicPhase = ""; appVm.showToast(res.error); return@launch }
            val f = res.file ?: run { musicUploading = false; musicProgress = -1f; musicPhase = ""; return@launch }
            musicPhase = "上传中"
            try {
                val r = withTimeout(120_000) {
                    withContext(Dispatchers.IO) {
                        Api.service.uploadFile<Any>("post_music", f) { musicProgress = it }
                    }
                }
                if (r.url.isNullOrBlank()) {
                    appVm.showToast("音乐上传失败: 服务端未返回地址")
                } else {
                    musicUrl = r.url
                    appVm.showToast("背景音乐已上传")
                }
            } catch (e: TimeoutCancellationException) {
                appVm.showToast("背景音乐上传超时，请检查网络后重试")
            } catch (e: Exception) {
                appVm.showToast("音乐上传失败: ${e.message}")
            } finally { f.delete(); musicUploading = false; musicProgress = -1f; musicPhase = "" }
        }
    }

    // 背景音乐(视频): 选取视频, 服务端自动提取其中音频作为背景音乐
    val videoMusicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        musicUploading = true
        musicProgress = -1f
        musicPhase = "准备中"
        scope.launch {
            var f: File? = null
            try {
                f = withContext(Dispatchers.IO) { copyUriToCacheFile(context, uri, "music", "mp4") }
                    ?: run { musicUploading = false; musicProgress = -1f; musicPhase = ""; appVm.showToast("读取视频失败"); return@launch }
                musicPhase = "上传中"
                val r = withTimeout(180_000) {
                    withContext(Dispatchers.IO) {
                        Api.service.uploadFile<Any>("post_music_video", f!!) { musicProgress = it }
                    }
                }
                if (r.url.isNullOrBlank()) {
                    appVm.showToast("背景音乐提取失败: 服务端未返回地址")
                } else {
                    musicUrl = r.url
                    appVm.showToast("已从视频提取背景音乐")
                }
            } catch (e: TimeoutCancellationException) {
                appVm.showToast("处理超时，请使用较短的视频")
            } catch (e: Exception) {
                appVm.showToast("背景音乐提取失败: ${e.message}")
            } finally { f?.delete(); musicUploading = false; musicProgress = -1f; musicPhase = "" }
        }
    }

    fun selectType(v: String) {
        if (postType == v) return
        postType = v
        // 切换类型时清理不相关的媒体
        when (v) {
            "article" -> { mediaUrls = emptyList() } // 文章用独立封面, 保留 coverUrl
            "image" -> { coverUrl = "" }             // 图文以首图作封面
            "video" -> { mediaUrls = emptyList() }   // 视频需重新上传
        }
    }

    fun loadDrafts() = scope.launch {
        draftsLoading = true
        drafts = try {
            val resp = Api.service.draftList()
            resp.data?.list ?: emptyList()
        } catch (e: Exception) {
            appVm.showToast("草稿加载失败: ${e.message}")
            emptyList()
        }
        draftsLoading = false
    }

    fun buildBody(): MutableMap<String, Any> {
        return mutableMapOf(
            "post_type" to postType,
            "title" to title,
            "content" to content,
            "media_urls" to mediaUrls,
            "cover_url" to coverUrl,
            "music_url" to musicUrl,
            "topic_names" to topics.toList(),
            "visibility" to visibility,
            "allow_download" to allowDownload,
            "watermark" to watermark,
            "circle_id" to 0
        )
    }

    fun submit() {
        if (title.isBlank() && content.isBlank()) { appVm.showToast("请输入标题或内容"); return }
        if (postType != "article" && mediaUrls.isEmpty()) { appVm.showToast("请先上传媒体文件"); return }
        submitting = true
        scope.launch {
            try {
                val r = Api.service.createPost(buildBody())
                if (r.ok) {
                    appVm.showToast(if (r.data?.status == "approved") "发布成功" else "已提交审核, 通过后展示")
                    if (draftId > 0) {
                        try { Api.service.deleteDraft(mapOf("draft_id" to draftId)) } catch (_: Exception) {}
                    }
                    navController.popBackStack()
                } else {
                    if (r.code == 403 && (r.msg ?: "").contains("手机号")) phoneRequiredDialog = r.msg ?: "请先验证手机号才能发布内容"
                    appVm.showToast(r.msg ?: "发布失败")
                }
            } catch (e: Exception) { appVm.showToast("网络错误: ${e.message}") }
            submitting = false
        }
    }

    fun saveDraft() = scope.launch {
        try {
            val body = buildBody()
            if (draftId > 0) body["draft_id"] = draftId
            val r = Api.service.saveDraft(body)
            if (r.ok) appVm.showToast("草稿已保存") else appVm.showToast(r.msg ?: "保存失败")
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    // 话题: 联想搜索(防抖)
    LaunchedEffect(topicInput) {
        val v = topicInput.trim()
        if (v.isBlank()) { topicSuggestions = emptyList(); showTopicSuggest = false; return@LaunchedEffect }
        delay(300)
        try {
            val r = Api.service.topicSearch(v)
            val list = r.data?.list ?: emptyList()
            val existing = topics.map { it.lowercase() }
            topicSuggestions = list.filter { it.name?.lowercase() !in existing }
            showTopicSuggest = topicSuggestions.isNotEmpty()
        } catch (_: Exception) { topicSuggestions = emptyList() }
    }

    fun addTopic(name: String) {
        val n = name.trim().removePrefix("#")
        if (n.isBlank() || n.length > 30) return
        if (topics.any { it.equals(n, ignoreCase = true) }) return
        topics = topics + n
        topicInput = ""
        topicSuggestions = emptyList()
        showTopicSuggest = false
    }

    fun createTopic(name: String) {
        val n = name.trim().removePrefix("#")
        if (n.isBlank()) return
        scope.launch {
            try {
                val r = Api.service.topicCreate(mapOf("name" to n))
                if (r.ok || r.msg == "话题已存在") addTopic(n)
                else appVm.showToast(r.msg ?: "创建失败")
            } catch (_: Exception) { appVm.showToast("网络错误") }
        }
    }

    fun onTopicInputChange(s: String) {
        topicInput = s
        showTopicSuggest = true
    }

    val exactExists = topicSuggestions.any { it.name.equals(topicInput.trim(), true) } ||
            topics.any { it.equals(topicInput.trim(), true) }

    Column(Modifier.fillMaxSize()) {
        TopBar("发布作品", onBack = { navController.popBackStack() }, actions = {
            TextButton({ showDrafts = true; loadDrafts() }) { Text("草稿箱") }
            TextButton({ saveDraft() }) { Text("存草稿") }
        })

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp).imePadding()
        ) {
            // 类型选择
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("article" to "文章", "image" to "图文", "video" to "视频").forEach { (v, label) ->
                    FilterChip(
                        selected = postType == v,
                        onClick = { selectType(v) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("标题(选填, 最多100字)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(content, { content = it },
                label = { Text("正文内容, 支持 @用户") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), minLines = 5)
            Spacer(Modifier.height(12.dp))

            // 话题标签(参考网页版)
            Text("话题标签", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            if (topics.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    topics.forEach { name ->
                        InputChip(
                            selected = true,
                            onClick = { },
                            label = { Text("#$name", fontSize = 13.sp) },
                            trailingIcon = {
                                Icon(Icons.Filled.Cancel, null,
                                    Modifier.size(14.dp).clickable { topics = topics - name })
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            OutlinedTextField(
                topicInput, { onTopicInputChange(it) },
                placeholder = { Text("输入话题名称, 回车添加", fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTopic(topicInput) }),
                modifier = Modifier.fillMaxWidth()
            )
            if (showTopicSuggest) {
                Card(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(4.dp)) {
                        topicSuggestions.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth().clickable { addTopic(t.name ?: "") }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#${t.name}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                Text("${t.postCount} 作品", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (topicInput.isNotBlank() && !exactExists) {
                            Row(
                                Modifier.fillMaxWidth().clickable { createTopic(topicInput) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text("+ 创建话题 \"#${topicInput.trim()}\"", fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 媒体上传(按类型区分)
            when (postType) {
                "article" -> {
                    // 文章: 仅允许上传封面, 不允许图片/视频
                    OutlinedButton(
                        onClick = { coverPicker.launch("image/*") },
                        enabled = !uploading && coverUrl.isBlank()
                    ) {
                        Icon(Icons.Filled.Image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (coverUrl.isBlank()) "上传封面" else "更换封面")
                    }
                    if (uploading) {
                        Spacer(Modifier.width(10.dp))
                        UploadProgressBarRow(uploading = uploading, progress = progress, barWidth = 90.dp, label = phaseLabel)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (coverUrl.isNotBlank()) {
                        Box {
                            AsyncImage(
                                fullUrl(coverUrl), null,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { coverUrl = "" },
                                modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                            ) {
                                Icon(Icons.Filled.Cancel, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    // 背景音乐
                    Spacer(Modifier.height(10.dp))
                    MusicUploadRow(
                        uploading = musicUploading, progress = musicProgress, phaseLabel = musicPhase,
                        musicUrl = musicUrl,
                        onPick = { musicPicker.launch("audio/*") },
                        onPickVideo = { videoMusicPicker.launch("video/*") },
                        onRemove = { musicUrl = "" }
                    )
                }
                "image" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !uploading && mediaUrls.size < 9
                        ) {
                            Icon(Icons.Filled.Image, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp));                             Text("添加图片")
                        }
                        if (uploading) UploadProgressBarRow(uploading = uploading, progress = progress, barWidth = 90.dp, label = phaseLabel)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (mediaUrls.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mediaUrls) { url ->
                                Box {
                                    AsyncImage(
                                        fullUrl(url), null,
                                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { mediaUrls = mediaUrls - url },
                                        modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                                    ) {
                                        Icon(Icons.Filled.Cancel, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    // 背景音乐
                    Spacer(Modifier.height(10.dp))
                    MusicUploadRow(
                        uploading = musicUploading, progress = musicProgress, phaseLabel = musicPhase,
                        musicUrl = musicUrl,
                        onPick = { musicPicker.launch("audio/*") },
                        onPickVideo = { videoMusicPicker.launch("video/*") },
                        onRemove = { musicUrl = "" }
                    )
                }
                "video" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { videoPicker.launch("video/*") },
                            enabled = !uploading
                        ) {
                            Icon(Icons.Filled.Videocam, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp));                             Text("上传视频")
                        }
                        if (uploading) UploadProgressBarRow(uploading = uploading, progress = progress, barWidth = 90.dp, label = phaseLabel)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (mediaUrls.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mediaUrls) { url ->
                                Box {
                                    AsyncImage(
                                        fullUrl(if (postType == "video") coverUrl.ifBlank { url } else url), null,
                                        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { mediaUrls = mediaUrls - url; if (postType == "video") coverUrl = "" },
                                        modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                                    ) {
                                        Icon(Icons.Filled.Cancel, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 可见性
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("仅自己可见", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Switch(visibility == 2, { visibility = if (it) 2 else 0 })
            }
            Spacer(Modifier.height(8.dp))

            // 允许下载
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许他人下载", fontSize = 14.sp)
                    Text("开启后他人可保存图片/视频", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(allowDownload, { allowDownload = it })
            }
            Spacer(Modifier.height(8.dp))

            // 水印（仅在允许下载时显示）
            if (allowDownload) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("添加水印", fontSize = 14.sp)
                        Text("下载时叠加平台logo+蓝本号", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(watermark, { watermark = it })
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { submit() },
                enabled = !submitting && !uploading && !musicUploading,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color.White)
                else Text("发布(先审后发)")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 草稿箱
    if (showDrafts) {
        BasicAlertDialog(
            onDismissRequest = { showDrafts = false }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("草稿箱", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    when {
                        draftsLoading -> Box(
                            Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                        drafts.isEmpty() -> Text(
                            "暂无草稿",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Column(
                            Modifier.fillMaxWidth().height(240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            drafts.forEach { d ->
                                                            Row(
                                        Modifier.fillMaxWidth().clickable {
                                            postType = d.postType
                                            title = d.title ?: ""
                                            content = d.content ?: ""
                                            mediaUrls = d.mediaUrls
                                            coverUrl = d.coverUrl ?: ""
                                            musicUrl = d.musicUrl ?: ""
                                            topics = d.topicNames
                                            draftId = d.id
                                            showDrafts = false
                                        }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                d.title?.ifBlank { null }
                                                    ?: (d.content ?: "无标题").take(20),
                                                fontSize = 14.sp, maxLines = 1
                                            )
                                            Text(
                                                org.dalanben.app.util.formatTime(d.updatedAt),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton({
                                            scope.launch {
                                                try {
                                                    Api.service.deleteDraft(mapOf("draft_id" to d.id))
                                                    drafts = drafts.filter { it.id != d.id }
                                                } catch (_: Exception) {}
                                            }
                                        }, Modifier.size(28.dp)) {
                                            Icon(
                                                Icons.Filled.Delete, null, Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = { showDrafts = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    if (phoneRequiredDialog != null) {
        AlertDialog(
            onDismissRequest = { phoneRequiredDialog = null },
            title = { Text("需要验证手机号", fontWeight = FontWeight.Bold) },
            text = { Text(phoneRequiredDialog!!) },
            confirmButton = {
                TextButton(onClick = {
                    phoneRequiredDialog = null
                    navController.navigate(Routes.SETTINGS)
                }) { Text("去绑定") }
            },
            dismissButton = {
                TextButton(onClick = { phoneRequiredDialog = null }) { Text("稍后") }
            }
        )
    }
}

@Composable
private fun MusicUploadRow(
    uploading: Boolean,
    progress: Float,
    phaseLabel: String,
    musicUrl: String,
    onPick: () -> Unit,
    onPickVideo: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (musicUrl.isBlank()) {
            OutlinedButton(onClick = onPick, enabled = !uploading) {
                Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("上传背景音乐")
            }
            OutlinedButton(onClick = onPickVideo, enabled = !uploading) {
                Icon(Icons.Filled.Videocam, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("用视频当背景音乐")
            }
        } else {
            // 已添加: 清晰展示 + 明确的移除按钮
            val fileName = musicUrl.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "背景音乐"
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MusicNote, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("已添加背景音乐", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(fileName, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(2.dp))
                        Text("移除", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        }
        if (uploading) {
            org.dalanben.app.ui.components.UploadProgressBarRow(
                uploading = uploading, progress = progress, barWidth = 160.dp, label = phaseLabel
            )
        }
    }
}
