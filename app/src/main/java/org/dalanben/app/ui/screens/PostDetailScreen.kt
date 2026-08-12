package org.dalanben.app.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dalanben.app.data.*
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.*
import org.dalanben.app.util.downloadPost
import org.dalanben.app.util.formatCount
import org.dalanben.app.util.formatTime
import org.dalanben.app.util.fullUrl
import org.dalanben.app.util.safeMedia

/** 网络偶发抖动时单次重试: 第一次抛异常(非取消)则再试一次, 仍失败返回 null */
private suspend fun <T> retryOnce(block: suspend () -> T): T? {
    return try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        try { block() } catch (_: Exception) { null }
    }
}

@Composable
fun PostDetailScreen(navController: NavController, appVm: AppViewModel, postId: Int) {
    var post by remember { mutableStateOf<Post?>(null) }
    var comments by remember { mutableStateOf(listOf<Comment>()) }
    var cPage by remember { mutableStateOf(1) }
    var cEnd by remember { mutableStateOf(false) }
    var cLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // 评论输入
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var sending by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var pickEmoji by remember { mutableStateOf<String?>(null) }
    var phoneRequiredDialog by remember { mutableStateOf<String?>(null) }
    var reportPost by remember { mutableStateOf(false) }
    var reportComment by remember { mutableStateOf<Comment?>(null) }
    var shareSheetPost by remember { mutableStateOf<Post?>(null) }
    var deletePostConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myId = Session.user?.id ?: 0

    suspend fun loadComments() {
        if (cLoading || cEnd) return
        cLoading = true
        try {
            val r = Api.service.commentList(postId, cPage, 20)
            val list = r.data?.list ?: emptyList()
            if (list.isEmpty()) cEnd = true else {
                val existed = comments.map { it.id }.toSet()
                comments = comments + list.filter { it.id !in existed }
                cPage += 1
            }
        } catch (_: Exception) { }
        cLoading = false
    }

    LaunchedEffect(postId) {
        try {
            val r = Api.service.postDetail(postId)
            if (r.ok && r.data != null) {
                post = r.data
                // 上报浏览
                try { Api.service.browse(mapOf("post_id" to postId)) } catch (_: Exception) {}
            } else loadError = r.msg ?: "作品不存在或已下架"
        } catch (e: Exception) { loadError = "网络错误" }
        loadComments()
    }

    fun toggleLike() = scope.launch {
        val p = post ?: return@launch
        val r = retryOnce { Api.service.like(mapOf("post_id" to p.id)) }
        if (r == null) { appVm.showToast("网络错误"); return@launch }
        val d = r.data
        if (r.ok && d != null) post = p.copy(liked = d.liked, likeCount = d.likeCount)
        else appVm.showToast(r.msg ?: "操作失败")
    }

    fun toggleCollect() = scope.launch {
        val p = post ?: return@launch
        try {
            val r = Api.service.collect(mapOf("post_id" to p.id))
            val d = r.data
            if (r.ok && d != null) post = p.copy(
                collected = d.collected,
                collectCount = p.collectCount + if (d.collected) 1 else -1
            )
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    fun doShare() {
        val p = post ?: return
        shareSheetPost = p
    }

    fun toggleFollow() = scope.launch {
        val p = post ?: return@launch
        val authorId = p.author?.id ?: p.userId
        try {
            val r = Api.service.follow(mapOf("user_id" to authorId))
            val d = r.data
            if (r.ok && d != null) post = p.copy(followedAuthor = d.followed)
            else appVm.showToast(r.msg ?: "操作失败")
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    fun applyFeatured() = scope.launch {
        try {
            val r = Api.service.applyFeature(mapOf("post_id" to postId))
            if (r.ok) {
                appVm.showToast("已提交精选申请, 等待审核")
                post = post?.copy(featuredApplyStatus = "pending")
            } else appVm.showToast(r.msg ?: "申请失败")
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    fun sendComment() {
        if (input.isBlank() && pickEmoji == null) { appVm.showToast("请输入评论内容"); return }
        sending = true
        scope.launch {
            try {
                val body = mutableMapOf<String, Any>("post_id" to postId)
                if (input.isNotBlank()) body["content"] = input
                pickEmoji?.let { body["emoji_url"] = "emoji:$it" }
                replyTo?.let {
                    body["parent_id"] = if (it.parentId > 0) it.parentId else it.id
                    body["reply_to_user_id"] = it.userId
                }
                val r = Api.service.createComment(body)
                if (r.ok) {
                    appVm.showToast(if (r.data?.status == "approved") "评论成功" else "评论已提交")
                    input = ""; replyTo = null; pickEmoji = null; showEmoji = false
                    comments = emptyList(); cPage = 1; cEnd = false; loadComments()
                    post = post?.copy(commentCount = (post?.commentCount ?: 0) + 1)
                } else {
                    if (r.code == 403 && (r.msg ?: "").contains("手机号")) phoneRequiredDialog = r.msg ?: "请先验证手机号才能评论"
                    appVm.showToast(r.msg ?: "评论失败")
                }
            } catch (_: Exception) { appVm.showToast("网络错误") }
            sending = false
        }
    }

    fun likeComment(c: Comment) = scope.launch {
        val r = retryOnce { Api.service.likeComment(mapOf("comment_id" to c.id)) }
        if (r == null) { appVm.showToast("网络错误"); return@launch }
        val d = r.data
        if (r.ok && d != null) {
            comments = comments.map {
                if (it.id == c.id) it.copy(isLiked = d.liked, likeCount = d.likeCount) else it
            }
        } else appVm.showToast(r.msg ?: "操作失败")
    }

    fun deleteComment(c: Comment) = scope.launch {
        try {
            val r = Api.service.deleteComment(mapOf("comment_id" to c.id))
            if (r.ok) {
                comments = comments.filter { it.id != c.id && it.parentId != c.id }
                appVm.showToast("已删除")
            } else appVm.showToast(r.msg ?: "删除失败")
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    fun deletePost() = scope.launch {
        try {
            val r = Api.service.deletePost(mapOf("post_id" to postId))
            if (r.ok) {
                appVm.showToast("已删除")
                appVm.feedCache.clear()   // 让各信息流重新加载, 移除已删除作品
                navController.popBackStack()
            } else appVm.showToast(r.msg ?: "删除失败")
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("作品详情", onBack = { navController.popBackStack() }, actions = {
            if (post?.userId == myId) {
                IconButton({ deletePostConfirm = true }) {
                    Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            IconButton({ reportPost = true }) { Icon(Icons.Filled.Flag, "举报") }
        })

        when {
            loadError != null -> EmptyState(loadError!!)
            post == null -> FullScreenLoading()
            else -> {
                val p = post!!
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                    // 作者栏
                    item {
                        val author = p.author
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(author?.avatarUrl ?: p.avatarUrl, 44) {
                                navController.navigate(Routes.profile(author?.id ?: p.userId))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(author?.nickname ?: p.nickname ?: "用户", fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    if ((author?.isBlueV ?: p.isBlueV) == 1) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Filled.Verified, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    LevelBadge(author?.level ?: 0, author?.levelTitle)
                                    Spacer(Modifier.width(6.dp))
                                    VerifyBadge(author?.verifyTitle, author?.verifyStyle)
                                }
                                // IP 属地 (作者)
                                val reg = author?.ipRegion ?: p.authorIpRegion
                                if (!reg.isNullOrBlank()) {
                                    Text("IP属地: $reg", fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                                Text("${formatTime(p.createdAt)} · ${formatCount(p.viewCount)}次浏览",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if ((author?.id ?: p.userId) != myId) {
                                PillButton(if (p.followedAuthor) "已关注" else "关注") { toggleFollow() }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    // 精选申请(仅作者本人, 已通过审核的作品)
                    item {
                        val p2 = post!!
                        if (p2.userId == myId && p2.status == "approved") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    p2.isFeatured == 1 -> FeaturedTag("已精选", MaterialTheme.colorScheme.primary)
                                    p2.featuredApplyStatus == "pending" -> FeaturedTag("精选审核中", Color(0xFFF59E0B))
                                    else -> Button(
                                        onClick = { applyFeatured() },
                                        contentPadding = PaddingValues(horizontal = 14.dp)
                                    ) {
                                        Icon(Icons.Filled.Star, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (p2.featuredApplyStatus == "rejected") "重新申请精选" else "申请精选")
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    // 标题/内容 + 右侧操作
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                if (!p.title.isNullOrBlank()) {
                                    SelectionContainer(Modifier.fillMaxWidth()) {
                                        Text(p.title!!, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth())
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                if (!p.content.isNullOrBlank()) {
                                    SelectionContainer(Modifier.fillMaxWidth()) {
                                        Text(p.content!!, fontSize = 15.sp, lineHeight = 24.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            // 右侧操作栏(点赞/收藏/分享)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { toggleLike() }) {
                                    Icon(
                                        if (p.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        null,
                                        tint = if (p.liked) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(formatCount(p.likeCount), fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { toggleCollect() }) {
                                    Icon(
                                        if (p.collected) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        null,
                                        tint = if (p.collected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(formatCount(p.collectCount), fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { doShare() }) {
                                    Icon(Icons.Filled.Share, "分享",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(24.dp))
                                }
                                // 下载按钮(仅允许下载的作品显示)
                                if (p.allowDownload == 1) {
                                    val ctx = LocalContext.current
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable { downloadPost(ctx, p, appVm) }) {
                                        Icon(Icons.Filled.Download, "下载",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    // 媒体
                    item {
                        val media = p.mediaUrls.safeMedia()
                        if (p.postType == "video" && media.isNotEmpty()) {
                            val videoUrl = fullUrl(media[0]) ?: media[0]
                            val coverUrl = fullUrl(p.coverUrl) ?: p.coverUrl ?: videoUrl
                            // 探测视频真实宽高比, 实现自适应布局
                            var videoAspectRatio by remember { mutableStateOf(16f / 9f) }
                            LaunchedEffect(videoUrl) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val retriever = MediaMetadataRetriever()
                                        retriever.setDataSource(videoUrl, HashMap())
                                        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                                        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                                        retriever.release()
                                        val ew = if (rotation == 90 || rotation == 270) h else w
                                        val eh = if (rotation == 90 || rotation == 270) w else h
                                        if (ew > 0 && eh > 0) videoAspectRatio = ew.toFloat() / eh.toFloat()
                                    } catch (_: Exception) { /* 探测失败保持默认 16:9 */ }
                                }
                            }
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                InlineVideoPlayer(
                                    url = videoUrl, cover = coverUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    onFullscreen = {
                                        val encUrl = java.net.URLEncoder.encode(videoUrl, "UTF-8")
                                        val encCover = java.net.URLEncoder.encode(coverUrl, "UTF-8")
                                        navController.navigate("video/$encUrl/$encCover")
                                    }
                                )
                            }
                        } else if (media.isNotEmpty()) {
                            ImageGrid(media) { url ->
                                navController.navigate(Routes.image(fullUrl(url) ?: url))
                            }
                        }
                        // 背景音乐控制条(如有)
                        if (!p.musicUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            MusicControlBar(fullUrl(p.musicUrl) ?: p.musicUrl!!)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    // 话题
                    item {
                        if (p.topics.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                p.topics.take(4).forEach { t ->
                                    SuggestionChip(
                                        onClick = { navController.navigate(Routes.topicDetail(t.id)) },
                                        label = { Text("#${t.name}", fontSize = 12.sp) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        // 宝贝回家公益广告
                        MissingChildCard()
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text("评论 ${formatCount(comments.size)}", fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                    }
                    // 评论列表 (一级 + 楼中楼缩进)
                    itemsIndexed(comments, key = { _, c -> c.id }) { idx, c ->
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = if (c.parentId > 0) 36.dp else 0.dp, top = 8.dp)
                        ) {
                            Avatar(c.avatarUrl, if (c.parentId > 0) 28 else 36) {
                                navController.navigate(Routes.profile(c.userId))
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.nickname ?: "用户", fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (c.isBlueV == 1) {
                                        Spacer(Modifier.width(3.dp))
                                        Icon(Icons.Filled.Verified, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp))
                                    }
                                    Spacer(Modifier.width(5.dp))
                                    LevelBadge(c.level, c.levelTitle)
                                    Spacer(Modifier.width(5.dp))
                                    VerifyBadge(c.verifyTitle, c.verifyStyle)
                                    if (c.status == "pending") {
                                        Spacer(Modifier.width(6.dp))
                                        Text("审核中", fontSize = 10.sp, color = Color(0xFFF59E0B))
                                    }
                                }
                                if (c.emojiUrl != null && c.emojiUrl.startsWith("emoji:")) {
                                    Text(c.emojiUrl.removePrefix("emoji:"), fontSize = 34.sp,
                                        color = MaterialTheme.colorScheme.onSurface)
                                } else if (c.emojiUrl != null && c.emojiUrl.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    AsyncImage(model = fullUrl(c.emojiUrl) ?: c.emojiUrl,
                                        contentDescription = "表情",
                                        modifier = Modifier.size(64.dp))
                                }
                                SelectionContainer(Modifier.fillMaxWidth()) {
                                    Text(c.content ?: "", fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (c.imageUrls.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    ImageGrid(c.imageUrls) { url ->
                                        navController.navigate(Routes.image(fullUrl(url) ?: url))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatTime(c.createdAt), fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!c.ipRegion.isNullOrBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("IP属地: ${c.ipRegion}", fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text("回复", fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { replyTo = c })
                                    if (c.userId == myId || Session.isAdmin) {
                                        Spacer(Modifier.width(14.dp))
                                        Text("删除", fontSize = 12.sp, color = Color(0xFFEF4444),
                                            modifier = Modifier.clickable { deleteComment(c) })
                                    }
                                    if (c.userId != myId) {
                                        Spacer(Modifier.width(14.dp))
                                        Text("举报", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.clickable { reportComment = c })
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { likeComment(c) }) {
                                Icon(
                                    if (c.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    null,
                                    tint = if (c.isLiked) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(formatCount(c.likeCount), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // 楼中楼回复
                        c.replies.forEach { rep ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(start = 36.dp, top = 6.dp)
                            ) {
                                Avatar(rep.avatarUrl, 28) {
                                    navController.navigate(Routes.profile(rep.userId))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(rep.nickname ?: "用户", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (rep.isBlueV == 1) {
                                            Spacer(Modifier.width(3.dp))
                                            Icon(Icons.Filled.Verified, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(11.dp))
                                        }
                                    }
                                    if (!rep.content.isNullOrBlank()) {
                                        Text(
                                            buildString {
                                                if (rep.replyToUserId > 0 && rep.replyToUserId != rep.userId) {
                                                    append("回复 ")
                                                    append("@${c.nickname ?: "用户"}")
                                                    append("：")
                                                }
                                                append(rep.content)
                                            },
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(formatTime(rep.createdAt), fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(12.dp))
                                        Text("回复", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { replyTo = rep })
                                        if (rep.userId == myId || Session.isAdmin) {
                                            Spacer(Modifier.width(12.dp))
                                            Text("删除", fontSize = 11.sp, color = Color(0xFFEF4444),
                                                modifier = Modifier.clickable { deleteComment(rep) })
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { likeComment(rep) }) {
                                    Icon(
                                        if (rep.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        null,
                                        tint = if (rep.isLiked) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(formatCount(rep.likeCount), fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (idx >= comments.lastIndex && !cEnd && !cLoading) {
                            LaunchedEffect(comments.size) { loadComments() }
                        }
                    }
                    if (cLoading) item { LoadingMore() }
                    if (comments.isEmpty() && !cLoading) item { EmptyState("暂无评论, 快来抢沙发") }
                    item { Spacer(Modifier.height(60.dp)) }
                }

                // 底部操作 + 评论输入栏
                Surface(Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                    Column {
                        replyTo?.let {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("回复 @${it.nickname ?: "用户"}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                IconButton({ replyTo = null }, Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).imePadding().navigationBarsPadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showEmoji = !showEmoji }, Modifier.size(38.dp)) {
                                Icon(Icons.Filled.SentimentSatisfied, "表情包",
                                    tint = if (showEmoji) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.width(2.dp))
                            OutlinedTextField(
                                input, { input = it },
                                placeholder = { Text("说点什么...", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                maxLines = 3
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { sendComment() }, enabled = !sending) {
                                Icon(Icons.AutoMirrored.Filled.Send, "发送",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (showEmoji) {
                            EmojiPanel(
                                onPick = { e ->
                                    if (input.isBlank()) {
                                        pickEmoji = e
                                        sendComment()
                                    } else {
                                        input += e
                                    }
                                },
                                onDismiss = { showEmoji = false }
                            )
                        }
                    }
                }
            }
        }
    }

    if (reportPost) {
        ReportDialog("post", postId, appVm) { reportPost = false }
    }

    if (deletePostConfirm) {
        AlertDialog(
            onDismissRequest = { deletePostConfirm = false },
            title = { Text("删除作品") },
            text = { Text("确定删除该作品吗? 删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = { deletePostConfirm = false; deletePost() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ deletePostConfirm = false }) { Text("取消") } }
        )
    }
    reportComment?.let { c ->
        ReportDialog("comment", c.id, appVm) { reportComment = null }
    }

    shareSheetPost?.let { p ->
        ShareSheet(
            payload = ShareContent(
                shareType = "post",
                targetId = p.id,
                title = p.title ?: "帖子",
                cover = p.coverUrl ?: p.cover,
                desc = shareSummary(p.content)
            ),
            appVm = appVm,
            externalText = "${p.title ?: ""}\n${shareUrl("post", p.id)}",
            onDismiss = { shareSheetPost = null }
        )
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
private fun FeaturedTag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Star, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

/** 背景音乐控制条: 进入作品详情自动播放, 仅允许暂停/继续(不允许关闭), 单曲循环 */
@Composable
private fun MusicControlBar(musicUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }

    val player = remember(musicUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(musicUrl)))
            prepare()
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { try { player.release() } catch (_: Exception) {} }
    }
    // 真实播放状态驱动图标, 避免自动停播/缓冲时图标错乱
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("背景音乐", fontSize = 13.sp, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(
                onClick = {
                    if (isPlaying) player.pause() else player.play()
                    isPlaying = !isPlaying
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

