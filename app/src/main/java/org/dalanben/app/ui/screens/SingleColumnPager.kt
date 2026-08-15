package org.dalanben.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.dalanben.app.data.*
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.*
import org.dalanben.app.util.formatCount
import org.dalanben.app.util.formatTime
import org.dalanben.app.util.fullUrl
import org.dalanben.app.util.safeMedia

/** 单列刷视频模式 — 类似抖音的纵向翻页体验 */
@Composable
fun SingleColumnPager(
    navController: NavController,
    appVm: AppViewModel,
    channel: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf(listOf<Post>()) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { posts.size.coerceAtLeast(1) })

    var commentSheetPostId by remember { mutableStateOf(0) }
    val showCommentSheet = commentSheetPostId > 0

    var sharePayload by remember { mutableStateOf<ShareContent?>(null) }
    val ctx = LocalContext.current

    suspend fun loadMore() {
        if (loading || end) return
        loading = true
        try {
            val resp = when (channel) {
                "featured" -> Api.service.featured(page, 12)
                "latest" -> Api.service.latest(page, 12)
                else -> Api.service.recommend(page, 12)
            }
            if (resp.ok) {
                val list = resp.data?.list ?: emptyList()
                if (list.isEmpty()) end = true
                else { posts = posts + list; page++ }
            }
        } catch (_: Exception) {}
        loading = false
    }

    LaunchedEffect(channel) { posts = emptyList(); page = 1; end = false; loadMore() }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage >= posts.size - 3 && posts.isNotEmpty()) loadMore()
    }

    fun updatePost(updated: Post) {
        posts = posts.map { if (it.id == updated.id) updated else it }
    }

    fun toggleLike(post: Post) = scope.launch {
        try {
            val r = Api.service.like(mapOf("post_id" to post.id))
            val d = r.data
            if (r.ok && d != null) {
                updatePost(post.copy(liked = d.liked, likeCount = d.likeCount))
            }
        } catch (_: Exception) {}
    }

    fun toggleCollect(post: Post) = scope.launch {
        try {
            val r = Api.service.collect(mapOf("post_id" to post.id))
            val d = r.data
            if (r.ok && d != null) {
                updatePost(post.copy(
                    collected = d.collected,
                    collectCount = if (d.collected) post.collectCount + 1 else maxOf(0, post.collectCount - 1)
                ))
            }
        } catch (_: Exception) {}
    }

    fun toggleFollow(post: Post) = scope.launch {
        try {
            val r = Api.service.follow(mapOf("user_id" to post.userId))
            val d = r.data
            if (r.ok && d != null) {
                updatePost(post.copy(followedAuthor = d.followed))
                appVm.showToast(if (d.followed) "已关注" else "已取消关注")
            }
        } catch (_: Exception) {}
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (posts.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无内容", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { scope.launch { loadMore() } }) {
                        Text("点击刷新", color = Color.White)
                    }
                }
            }
        } else {
            VerticalPager(
                state = pagerState, modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { idx ->
                val post = posts.getOrNull(idx)
                if (post != null) {
                    PagerPostItem(
                        post = post, isCurrentPage = idx == pagerState.currentPage,
                        navController = navController, appVm = appVm,
                        onLike = { toggleLike(post) },
                        onComment = { commentSheetPostId = post.id },
                        onCollect = { toggleCollect(post) },
                        onFollow = { toggleFollow(post) },
                        onShare = {
                            sharePayload = ShareContent(
                                shareType = "post", targetId = post.id,
                                cover = post.coverUrl ?: post.mediaUrls.safeMedia().firstOrNull() ?: "",
                                title = post.title ?: "", desc = post.content?.take(60) ?: ""
                            )
                        }
                    )
                } else if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }

        // 顶栏
        Surface(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 36.dp),
            color = Color.Transparent) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, "退出", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(when (channel) { "featured" -> "精选"; "latest" -> "最新"; else -> "推荐" },
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.weight(1f))
            }
        }
        if (loading && posts.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }

    // 评论区底部弹窗
    if (showCommentSheet) {
        CommentBottomSheet(postId = commentSheetPostId, appVm = appVm,
            navController = navController,
            onDismiss = { commentSheetPostId = 0 })
    }

    // 站内分享弹窗 (复用 ShareSheet)
    sharePayload?.let { sp ->
        val extText = "${sp.title}\nhttps://dalanben.org/post/${sp.targetId}"
        ShareSheet(
            payload = sp, appVm = appVm,
            externalText = extText, copyText = null,
            onDismiss = { sharePayload = null },
            onShared = {
                posts = posts.map { p ->
                    if (p.id == sp.targetId) p.copy(shareCount = p.shareCount + 1) else p
                }
            }
        )
    }
}

/** 单列模式下一页帖子 */
@Composable
private fun PagerPostItem(
    post: Post, isCurrentPage: Boolean,
    navController: NavController, appVm: AppViewModel,
    onLike: () -> Unit, onComment: () -> Unit,
    onCollect: () -> Unit, onFollow: () -> Unit,
    onShare: () -> Unit,
) {
    val media = post.mediaUrls.safeMedia()
    val isVideo = post.postType == "video" && media.isNotEmpty()
    val avatarUrl = fullUrl(post.avatarUrl) ?: post.avatarUrl
    val myId = Session.user?.id ?: 0
    // 自己发布的作品不显示关注按钮
    val showFollow = post.userId != myId && !post.followedAuthor

    Box(Modifier.fillMaxSize()) {
        when {
            isVideo -> {
                val videoUrl = fullUrl(media[0]) ?: media[0]
                val coverUrl = fullUrl(post.coverUrl) ?: post.coverUrl ?: videoUrl
                if (isCurrentPage) VideoPlayerAuto(videoUrl, Modifier.fillMaxSize())
                else AsyncImage(coverUrl, null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit)
            }
            media.isNotEmpty() -> {
                if (media.size > 1) {
                    // 多图轮播
                    val imgPagerState = rememberPagerState(pageCount = { media.size })
                    Box(Modifier.fillMaxSize()) {
                        HorizontalPager(state = imgPagerState, modifier = Modifier.fillMaxSize()) { page ->
                            val imgUrl = fullUrl(media[page]) ?: media[page]
                            AsyncImage(imgUrl, null, modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit)
                        }
                        // 翻页指示器（底部居中）
                        if (media.size > 1) {
                            Row(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                repeat(media.size) { idx ->
                                    Box(Modifier.size(if (idx == imgPagerState.currentPage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (idx == imgPagerState.currentPage)
                                            Color.White else Color.White.copy(alpha = 0.4f)))
                                }
                            }
                        }
                        // 左右箭头提示（仅第一张/最后一张时隐藏）
                        if (imgPagerState.currentPage > 0) {
                            Icon(Icons.Filled.ChevronLeft, null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(28.dp))
                        }
                        if (imgPagerState.currentPage < media.size - 1) {
                            Icon(Icons.Filled.ChevronRight, null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(28.dp))
                        }
                    }
                } else {
                    val imgUrl = fullUrl(media[0]) ?: media[0]
                    AsyncImage(imgUrl, null, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit)
                }
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)) {
                        Column(Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if (!post.title.isNullOrBlank()) {
                                Text(post.title!!, color = Color.White, fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(12.dp))
                            }
                            if (!post.content.isNullOrBlank()) {
                                Text(post.content!!, color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp, lineHeight = 22.sp,
                                    maxLines = 8, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // 右侧互动栏
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // 头像 — 默认头像兜底(未设置/加载失败显示 Person 图标)
            Box(Modifier.size(44.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .clickable { navController.navigate(Routes.profile(post.userId)) },
                contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.Person),
                    error = rememberVectorPainter(Icons.Filled.Person)
                )
            }

            // 关注按钮 — 未关注且非本人作品才显示
            if (showFollow) {
                Icon(Icons.Filled.Add, null, tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp).background(Color.White, CircleShape)
                        .padding(2.dp).clickable { onFollow() })
            }
            Spacer(Modifier.height((if (showFollow) 8 else 36).dp))

            // 点赞
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onLike() }) {
                Icon(if (post.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    null, tint = if (post.liked) Color(0xFFEF4444) else Color.White,
                    modifier = Modifier.size(36.dp))
                Text(formatCount(post.likeCount), color = Color.White, fontSize = 12.sp)
            }
            // 评论 — 打开底部弹窗
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onComment() }) {
                Icon(Icons.Filled.ChatBubbleOutline, null, tint = Color.White,
                    modifier = Modifier.size(32.dp))
                Text(formatCount(post.commentCount), color = Color.White, fontSize = 12.sp)
            }
            // 收藏
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onCollect() }) {
                Icon(if (post.collected) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    null, tint = if (post.collected) Color(0xFFFFD700) else Color.White,
                    modifier = Modifier.size(32.dp))
                Text(formatCount(post.collectCount), color = Color.White, fontSize = 12.sp)
            }
            // 分享 — 站内分享弹窗
            Icon(Icons.Filled.Share, null, tint = Color.White,
                modifier = Modifier.size(30.dp).clickable { onShare() })
        }

        // 底部信息栏
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .padding(start = 12.dp, end = 80.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.nickname ?: "用户", color = Color.White,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (post.isBlueV == 1) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp))
                }
            }
            if (!post.title.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(post.title!!, color = Color.White, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!post.content.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(post.content!!.take(100), color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(formatTime(post.createdAt), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            val region = post.authorIpRegion ?: post.author?.ipRegion
            if (!region.isNullOrBlank()) {
                Text("IP属地: $region", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            }
            // 背景音乐: 当前页自动播放并显示控件, 非当前页仅轻量提示
            if (!post.musicUrl.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                val mu = fullUrl(post.musicUrl) ?: post.musicUrl!!
                if (isCurrentPage) PagerMusicBar(mu)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("背景音乐", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** 单列模式背景音乐控件: 仅当前页展示, 进入即自动播放, 单曲循环, 仅允许暂停/继续 */
@Composable
private fun PagerMusicBar(
    musicUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }

    val player = remember(musicUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.parse(musicUrl)))
            prepare()
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { try { player.release() } catch (_: Exception) {} }
    }
    // 真实播放状态驱动图标, 避免播放失败/缓冲时图标错乱
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("背景音乐", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.weight(1f, fill = false))
            IconButton(
                onClick = {
                    if (isPlaying) player.pause() else player.play()
                    isPlaying = !isPlaying
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null, Modifier.size(18.dp), tint = Color.White
                )
            }
        }
    }
}


@Composable
private fun VideoPlayerAuto(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context)
            // 不抢占音频焦点(handleAudioFocus=false), 背景音乐可同时播放
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
            .build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.parse(url)))
                prepare(); playWhenReady = true
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    var dragRatio by remember { mutableStateOf<Float?>(null) }
    var currentSpeed by remember { mutableStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // 断点续播: 进入时恢复上次位置
    LaunchedEffect(player, url) {
        val p = SessionManager.getVideoProgress(context, url)
        if (p > 0) player.seekTo(p)
    }

    DisposableEffect(player, url) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    androidx.media3.common.Player.STATE_READY -> { duration = player.duration; isBuffering = false }
                    androidx.media3.common.Player.STATE_BUFFERING -> isBuffering = true
                    androidx.media3.common.Player.STATE_ENDED -> SessionManager.clearVideoProgress(context, url)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            val pos = player.currentPosition
            if (pos > 3000) SessionManager.saveVideoProgress(context, url, pos)
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (true) {
            if (isPlaying) { currentPos = player.currentPosition; duration = if (player.duration > 0) player.duration else duration }
            kotlinx.coroutines.delay(200)
        }
    }

    val scope = rememberCoroutineScope()
    val hideControls = { scope.launch { kotlinx.coroutines.delay(3000); showControls = false } }

    Box(modifier) {
        AndroidView(factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player; useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }, modifier = Modifier.fillMaxSize())

        // 点击暂停/播放 + 长按 3 倍速 + 松手恢复
        Box(Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (player.isPlaying) player.pause() else player.play()
                        showControls = true; hideControls()
                    },
                            onLongPress = { isLongPressing = true; player.setPlaybackSpeed(3f); showControls = true; }
                )
            }
            .pointerInput(Unit) {
                // Release long press -> restore user speed
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed } && isLongPressing) {
                            isLongPressing = false
                            player.setPlaybackSpeed(currentSpeed)
                            hideControls()
                        }
                    }
                }
            }
        )

        // 控件层
        AnimatedVisibility(
            visible = showControls || isLongPressing || !isPlaying,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // 进度条: 拖动时用本地值, 松手才 seekTo(避免拖动回弹与缓冲导致的假暂停)
                Slider(
                    value = dragRatio ?: (if (duration > 0) currentPos.toFloat() / duration else 0f),
                    onValueChange = { dragRatio = it },
                    onValueChangeFinished = {
                        dragRatio?.let { player.seekTo((it * duration).toLong()) }
                        dragRatio = null
                        showControls = true; hideControls()
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White, activeTrackColor = Color(0xFFEF4444),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (isPlaying) player.pause() else player.play() },
                            modifier = Modifier.size(36.dp)) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Text("${formatDuration(currentPos)} / ${formatDuration(duration)}",
                            color = Color.White, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Speed selector button
                        Box {
                            TextButton(onClick = { showSpeedMenu = true }) {
                                Text(
                                    if (currentSpeed == 1f) "1x" else "${currentSpeed}x",
                                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { spd ->
                                    DropdownMenuItem(
                                        text = { Text("${spd}x", fontWeight = if (spd == currentSpeed) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            currentSpeed = spd
                                            player.setPlaybackSpeed(spd)
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        if (isLongPressing) {
                            Surface(color = Color(0xFFEF4444), shape = RoundedCornerShape(6.dp)) {
                                Text("3x", color = Color.White, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        } else if (isBuffering) {
                            Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("缓冲中…", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        } else if (!isPlaying) {
                            Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("暂停", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${"%02d".format(s % 60)}"
}

// ══════════════ 评论区底部弹窗 ══════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentBottomSheet(postId: Int, appVm: AppViewModel, navController: NavController, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var comments by remember { mutableStateOf(listOf<Comment>()) }
    var cPage by remember { mutableStateOf(1) }
    var cEnd by remember { mutableStateOf(false) }
    var cLoading by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var sending by remember { mutableStateOf(false) }
    var reportComment by remember { mutableStateOf<Comment?>(null) }
    val myId = Session.user?.id ?: 0

    suspend fun loadComments() {
        if (cLoading || cEnd) return
        cLoading = true
        try {
            val r = Api.service.commentList(postId, cPage, 20)
            if (r.ok) {
                val list = r.data?.list ?: emptyList()
                if (list.isEmpty()) cEnd = true
                else {
                    val existed = comments.map { it.id }.toSet()
                    comments = comments + list.filter { it.id !in existed }
                    cPage++
                }
            }
        } catch (_: Exception) {}
        cLoading = false
    }

    LaunchedEffect(postId) {
        comments = emptyList(); cPage = 1; cEnd = false; loadComments()
    }

    fun sendComment() {
        if (input.isBlank()) { appVm.showToast("请输入评论内容"); return }
        sending = true; scope.launch {
            try {
                val body = mutableMapOf<String, Any>("post_id" to postId, "content" to input)
                replyTo?.let {
                    body["parent_id"] = if (it.parentId > 0) it.parentId else it.id
                    body["reply_to_user_id"] = it.userId
                }
                val r = Api.service.createComment(body)
                if (r.ok) {
                    appVm.showToast("评论成功"); input = ""; replyTo = null
                    comments = emptyList(); cPage = 1; cEnd = false; loadComments()
                } else appVm.showToast(r.msg ?: "评论失败")
            } catch (_: Exception) { appVm.showToast("网络错误") }
            sending = false
        }
    }

    fun likeComment(c: Comment) = scope.launch {
        try {
            val r = Api.service.likeComment(mapOf("comment_id" to c.id))
            val d = r.data
            if (r.ok && d != null) {
                comments = comments.map {
                    if (it.id == c.id) it.copy(isLiked = d.liked, likeCount = d.likeCount) else it
                }
            }
        } catch (_: Exception) {}
    }

    fun deleteComment(c: Comment) = scope.launch {
        try {
            val r = Api.service.deleteComment(mapOf("comment_id" to c.id))
            if (r.ok) {
                comments = comments.filter { it.id != c.id && it.parentId != c.id }
                appVm.showToast("已删除")
            }
        } catch (_: Exception) {}
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(min = 400.dp, max = 560.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("评论", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            HorizontalDivider()

            replyTo?.let {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("回复 @${it.nickname ?: "用户"}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    IconButton({ replyTo = null }, Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
                    }
                }
            }

            if (comments.isEmpty() && !cLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无评论", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp)) {
                    itemsIndexed(comments, key = { _, c -> c.id }) { idx, c ->
                        CommentItem(c, myId, navController,
                            onReply = { replyTo = c },
                            onLike = { likeComment(c) },
                            onDelete = { deleteComment(c) },
                            onReport = { reportComment = c })
                        c.replies?.forEach { rep ->
                            CommentItem(rep, myId, navController, isSub = true,
                                onReply = { replyTo = rep },
                                onLike = { likeComment(rep) },
                                onDelete = { deleteComment(rep) },
                                onReport = { reportComment = rep })
                        }
                        if (idx >= comments.lastIndex && !cEnd && !cLoading) {
                            LaunchedEffect(comments.size) { loadComments() }
                        }
                    }
                    if (cLoading) {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                .imePadding().navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it },
                    placeholder = { Text("说点什么...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp), maxLines = 3)
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { sendComment() }, enabled = !sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, "发送",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    reportComment?.let { c ->
        ReportDialog("comment", c.id, appVm) { reportComment = null }
    }
}

// ══════════════ 评论区单条评论 ══════════════

@Composable
private fun CommentItem(
    comment: Comment, myId: Int, navController: NavController, isSub: Boolean = false,
    onReply: () -> Unit, onLike: () -> Unit,
    onDelete: () -> Unit, onReport: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = if (isSub) 36.dp else 0.dp, top = 8.dp)) {
        Avatar(comment.avatarUrl, if (isSub) 28 else 36,
            onClick = { navController.navigate(Routes.profile(comment.userId)) })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.nickname ?: "用户",
                    fontSize = if (isSub) 12.sp else 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (comment.isBlueV == 1) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Filled.Verified, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isSub) 11.dp else 12.dp))
                }
                Spacer(Modifier.width(5.dp))
                LevelBadge(comment.level, comment.levelTitle)
                if (comment.status == "pending") {
                    Spacer(Modifier.width(6.dp))
                    Text("审核中", fontSize = 10.sp, color = Color(0xFFF59E0B))
                }
            }
            if (comment.emojiUrl != null && comment.emojiUrl.startsWith("emoji:")) {
                Text(comment.emojiUrl.removePrefix("emoji:"),
                    fontSize = if (isSub) 28.sp else 34.sp,
                    color = MaterialTheme.colorScheme.onSurface)
            } else if (comment.emojiUrl != null && comment.emojiUrl.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                AsyncImage(model = fullUrl(comment.emojiUrl) ?: comment.emojiUrl,
                    contentDescription = "表情",
                    modifier = Modifier.size(if (isSub) 48.dp else 64.dp))
            }
            if (!comment.content.isNullOrBlank()) {
                SelectionContainer {
                    Text(
                        buildString {
                            if (isSub && comment.replyToUserId > 0 &&
                                comment.replyToUserId != comment.userId)
                                append("回复 用户：")
                            append(comment.content ?: "")
                        },
                        fontSize = if (isSub) 13.sp else 14.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(comment.createdAt),
                    fontSize = if (isSub) 10.sp else 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!comment.ipRegion.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text("IP属地: ${comment.ipRegion}", fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Spacer(Modifier.width(14.dp))
                Text("回复", fontSize = if (isSub) 11.sp else 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onReply() })
                if (comment.userId == myId || Session.isAdmin) {
                    Spacer(Modifier.width(14.dp))
                    Text("删除", fontSize = if (isSub) 11.sp else 12.sp,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.clickable { onDelete() })
                }
                if (comment.userId != myId) {
                    Spacer(Modifier.width(14.dp))
                    Text("举报", fontSize = if (isSub) 11.sp else 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onReport() })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onLike() }) {
            Icon(if (comment.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                null, tint = if (comment.isLiked) Color(0xFFEF4444)
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(if (isSub) 12.dp else 14.dp))
            Spacer(Modifier.width(2.dp))
            Text(formatCount(comment.likeCount),
                fontSize = if (isSub) 10.sp else 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
