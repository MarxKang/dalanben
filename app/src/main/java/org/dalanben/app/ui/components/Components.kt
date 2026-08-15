package org.dalanben.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import org.dalanben.app.data.Post
import org.dalanben.app.data.User
import org.dalanben.app.util.formatCount
import org.dalanben.app.util.formatTime
import org.dalanben.app.util.fullUrl
import org.dalanben.app.util.safeMedia

@Composable
fun Avatar(url: String?, size: Int = 40, onClick: (() -> Unit)? = null) {
    val mod = Modifier
        .size(size.dp)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    AsyncImage(
        model = fullUrl(url),
        contentDescription = null,
        modifier = mod,
        contentScale = ContentScale.Crop,
        placeholder = rememberVectorPainter(Icons.Filled.Person),
        error = rememberVectorPainter(Icons.Filled.Person)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            }
        },
        actions = actions
    )
}

@Composable
fun LoadingMore() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun EmptyState(msg: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Inbox, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 旋转加载动画
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                        scaleX = scale
                        scaleY = scale
                    },
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "加载中...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ImageGrid(images: List<String>, onImageClick: (String) -> Unit) {
    if (images.isEmpty()) return
    val columns = when {
        images.size == 1 -> 1
        images.size <= 4 -> 2
        else -> 3
    }
    val maxShow = if (images.size == 1) 1 else minOf(images.size, 9)
    val shown = images.take(maxShow)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val rows = shown.chunked(columns)
        rows.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { url ->
                    AsyncImage(
                        model = fullUrl(url),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(url) },
                        contentScale = ContentScale.Crop
                    )
                }
                // 补齐末尾空格
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}

/** 帖子内嵌视频播放器: 直接在当前页面播放, 不跳转全屏页; 支持站内断点续播 */
@Composable
fun InlineVideoPlayer(url: String, cover: String, modifier: Modifier = Modifier, onFullscreen: (() -> Unit)? = null) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }
    var currentSpeed by remember(url) { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember(url) { mutableStateOf(false) }
    // Breakpoint resume: restore last position on enter
    LaunchedEffect(player, url) {
        val p = org.dalanben.app.data.SessionManager.getVideoProgress(context, url)
        if (p > 0) player.seekTo(p)
    }
    // Save position on leave; clear on finish
    DisposableEffect(player, url) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) {
                    org.dalanben.app.data.SessionManager.clearVideoProgress(context, url)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            val pos = player.currentPosition
            if (pos > 3000) org.dalanben.app.data.SessionManager.saveVideoProgress(context, url, pos)
            player.release()
        }
    }
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Top-right controls: fullscreen + speed
        Row(
            Modifier.align(Alignment.TopEnd).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed selector
            Box {
                IconButton(onClick = { showSpeedMenu = true }) {
                    Text(
                        if (currentSpeed == 1f) "1x" else "${currentSpeed}x",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
            if (onFullscreen != null) {
                IconButton(
                    onClick = onFullscreen,
                ) {
                    Icon(Icons.Filled.Fullscreen, "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(6.dp))
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    onPostClick: () -> Unit,
    onUserClick: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onCollect: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    onVideoClick: (String, String) -> Unit,
    expandedContent: Boolean = false
) {
    val blue = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPostClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.avatarUrl ?: post.author?.avatarUrl, 40) { onUserClick() }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.nickname ?: post.author?.nickname ?: "用户",
                            fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if ((post.isBlueV ?: post.author?.isBlueV ?: 0) == 1) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Verified, null, tint = blue, modifier = Modifier.size(15.dp))
                        }
                    }
                    Text(formatTime(post.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // IP 属地 (作者)
                    val region = post.authorIpRegion ?: post.author?.ipRegion
                    if (!region.isNullOrBlank()) {
                        Text("IP属地: $region", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
                IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(8.dp))
            // 标题
            if (!post.title.isNullOrBlank()) {
                Text(post.title!!, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
            }
            // 内容：在“我的”页面展示完整内容，避免显示占比过小
            if (!post.content.isNullOrBlank()) {
                Text(
                    post.content!!,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expandedContent) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
            }
            // 媒体
            val media = post.mediaUrls.safeMedia()
            if (post.postType == "video" && media.isNotEmpty()) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                        .clickable { onVideoClick(fullUrl(media[0]) ?: "", fullUrl(post.coverUrl) ?: "") },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = fullUrl(post.coverUrl ?: media[0]),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(8.dp))
            } else if (media.isNotEmpty()) {
                ImageGrid(media) { onVideoClick(it, "") }
                Spacer(Modifier.height(8.dp))
            }
            // 操作栏
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AnimatedLikeButton(
                    isLiked = post.liked,
                    likeCount = post.likeCount,
                    onClick = onLike
                )
                Spacer(Modifier.width(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onComment() }) {
                    Icon(Icons.Filled.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(formatCount(post.commentCount), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(20.dp))
                AnimatedCollectButton(
                    isCollected = post.collected,
                    collectCount = post.collectCount,
                    onClick = onCollect
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onShare() }) {
                    Icon(Icons.Filled.Share, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun UserRow(
    user: User,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(user.avatarUrl, 44) { onClick() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.nickname ?: "用户", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                if (user.isBlueV == 1) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                }
            }
            if (!user.signature.isNullOrBlank()) {
                Text(user.signature!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

/** 等级头衔徽章: 金棕色渐变胶囊, 显示 "Lv.X 头衔" */
@Composable
fun LevelBadge(level: Int, title: String?, modifier: Modifier = Modifier) {
    if (level <= 0 && title.isNullOrBlank()) return
    val label = if (title.isNullOrBlank()) "Lv.$level" else "Lv.$level $title"
    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFC8911A), Color(0xFFE6B422))
                ),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 官方认证头衔徽章：根据 verify_style 着色，显示自定义认证头衔文字 */
@Composable
fun VerifyBadge(title: String?, style: String?, modifier: Modifier = Modifier) {
    if (title.isNullOrBlank()) return
    val color = when (style) {
        "red" -> Color(0xFFE53935)
        "green" -> Color(0xFF2E7D32)
        "gold" -> Color(0xFFC8911A)
        "purple" -> Color(0xFF7B1FA2)
        "orange" -> Color(0xFFEF6C00)
        else -> Color(0xFF1976D2) // blue default
    }
    Box(
        modifier = modifier
            .background(color = color, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PillButton(text: String, active: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = active,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
    ) { Text(text, fontSize = 13.sp) }
}
