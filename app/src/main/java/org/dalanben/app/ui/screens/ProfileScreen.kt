package org.dalanben.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.DEFAULT_BG
import org.dalanben.app.data.Session
import org.dalanben.app.data.ShareContent
import org.dalanben.app.data.User
import org.dalanben.app.data.shareUrl
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.*
import org.dalanben.app.util.formatCount
import org.dalanben.app.util.fullUrl

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(navController: NavController, appVm: AppViewModel, userId: Int) {
    var user by remember { mutableStateOf<User?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tabIndex by remember { mutableStateOf(0) }
    var showFollowList by remember { mutableStateOf<String?>(null) } // follower | following
    var followUsers by remember { mutableStateOf(listOf<User>()) }
    var reportUser by remember { mutableStateOf(false) }
    var shareProfile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isMe = userId == (Session.user?.id ?: -1)

    // 作品搜索（带防抖，避免每次输入都请求）
    var keywordInput by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    LaunchedEffect(keywordInput) {
        delay(400)
        keyword = keywordInput
    }

    LaunchedEffect(userId) {
        try {
            val r = Api.service.profile(userId)
            if (r.ok && r.data != null) user = r.data else error = r.msg ?: "用户不存在"
        } catch (_: Exception) { error = "网络错误" }
    }

    fun toggleFollow() = scope.launch {
        val u = user ?: return@launch
        try {
            val r = Api.service.follow(mapOf("user_id" to u.id))
            val d = r.data
            if (r.ok && d != null) {
                user = u.copy(
                    isFollowed = d.followed,
                    followerCount = u.followerCount + if (d.followed) 1 else -1
                )
            } else appVm.showToast(r.msg ?: "操作失败")
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    fun toggleBlock() = scope.launch {
        val u = user ?: return@launch
        try {
            val r = Api.service.block(mapOf("user_id" to u.id))
            val d = r.data
            if (r.ok && d != null) {
                user = u.copy(isBlocked = d.blocked)
                appVm.showToast(if (d.blocked) "已拉黑" else "已取消拉黑")
            } else appVm.showToast(r.msg ?: "操作失败")
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    fun openFollowList(type: String) {
        val u = user
        // 他人设置了隐藏对应列表: 直接提示, 不打开
        if (!isMe && u != null) {
            val hidden = if (type == "followers") u.privacyFollowers == 1 else u.privacyFollowing == 1
            if (hidden) {
                appVm.showToast(if (type == "followers") "对方已隐藏粉丝列表" else "对方已隐藏关注列表")
                return
            }
        }
        scope.launch {
            try {
                val r = Api.service.followList(userId, type, 1, 50)
                if (r.data?.private == true) {
                    appVm.showToast(if (type == "followers") "对方已隐藏粉丝列表" else "对方已隐藏关注列表")
                } else {
                    followUsers = r.data?.list ?: emptyList()
                    showFollowList = type
                }
            } catch (_: Exception) { }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(user?.nickname ?: "用户主页", onBack = { navController.popBackStack() }, actions = {
            IconButton({ shareProfile = true }) { Icon(Icons.Filled.Share, "分享") }
            if (!isMe) {
                IconButton({ reportUser = true }) { Icon(Icons.Filled.Flag, "举报") }
            }
        })

        when {
            error != null -> EmptyState(error!!)
            user == null -> FullScreenLoading()
            else -> {
                val u = user!!
                val type = when (tabIndex) { 1 -> "like"; 2 -> "collect"; else -> "user" }
                PostFeedList(
                    navController = navController,
                    appVm = appVm,
                    refreshKey = "profile-$userId-$type-$keyword",
                    emptyMsg = "暂无内容",
                    fullWidthHeader = true,
                    header = {
                        item {
                            // 用户背景图(封面): 未自定义时使用网页版默认背景图
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            ) {
                                AsyncImage(
                                    model = fullUrl(u.bgUrl ?: DEFAULT_BG),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(u.avatarUrl, 68)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SelectionContainer {
                                            Text(u.nickname ?: "用户", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        if (u.isBlueV == 1) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Filled.Verified, null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            LevelBadge(u.level, u.levelTitle)
                                            Spacer(Modifier.width(6.dp))
                                            VerifyBadge(u.verifyTitle, u.verifyStyle)
                                        }
                                        SelectionContainer {
                                            Text("蓝本号: ${u.blueId ?: "-"}", fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (!u.ipRegion.isNullOrBlank()) {
                                            Text("IP属地: ${u.ipRegion}", fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                                        }
                                        if (!u.title.isNullOrBlank()) {
                                            Text(u.title!!, fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                if (!u.signature.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(u.signature!!, fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!u.bio.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(u.bio!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row {
                                    val followingHidden = !isMe && (u.privacyFollowing == 1)
                                    val followerHidden = !isMe && (u.privacyFollowers == 1)
                                    Column(
                                        Modifier.weight(1f).then(
                                            if (followingHidden) Modifier else Modifier.clickable { openFollowList("following") }
                                        ),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(if (followingHidden) "—" else formatCount(u.followingCount),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(if (followingHidden) "已隐藏" else "关注", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(
                                        Modifier.weight(1f).then(
                                            if (followerHidden) Modifier else Modifier.clickable { openFollowList("followers") }
                                        ),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(if (followerHidden) "—" else formatCount(u.followerCount),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(if (followerHidden) "已隐藏" else "粉丝", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(formatCount(u.postCount), fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text("作品", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(formatCount(u.likeCount), fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text("获赞", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (!isMe) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = { toggleFollow() }, modifier = Modifier.weight(1f)) {
                                            Text(if (u.isFollowed) "已关注" else "+ 关注")
                                        }
                                        OutlinedButton(
                                            onClick = { navController.navigate(Routes.chat(u.id)) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("私信") }
                                        OutlinedButton(onClick = { toggleBlock() }) {
                                            Text(if (u.isBlocked) "取消拉黑" else "拉黑",
                                                color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    stickyHeader = {
                        stickyHeader {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                OutlinedTextField(
                                    value = keywordInput,
                                    onValueChange = { keywordInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    placeholder = { Text("搜索作品") },
                                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    trailingIcon = {
                                        if (keywordInput.isNotEmpty()) {
                                            IconButton({ keywordInput = "" }) {
                                                Icon(Icons.Filled.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium
                                )
                            }
                            Surface(color = MaterialTheme.colorScheme.background) {
                                TabRow(tabIndex) {
                                    Tab(tabIndex == 0, { tabIndex = 0 }, text = { Text("作品") })
                                    Tab(tabIndex == 1, { tabIndex = 1 }, text = { Text("点赞") })
                                    Tab(tabIndex == 2, { tabIndex = 2 }, text = { Text("收藏") })
                                }
                            }
                        }
                    },
                    loader = { page ->
                        val r = Api.service.postList(userId, type, page, 12, keyword.takeIf { it.isNotBlank() })
                        if (r.ok) r.data?.list ?: emptyList() else emptyList()
                    }
                )
            }
        }
    }

    // 关注/粉丝列表
    showFollowList?.let { type ->
        ModalBottomSheet(onDismissRequest = { showFollowList = null }) {
            Text(if (type == "followers") "粉丝列表" else "关注列表",
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(followUsers, key = { it.id }) { fu ->
                    UserRow(fu, onClick = {
                        showFollowList = null
                        navController.navigate(Routes.profile(fu.id))
                    })
                }
                if (followUsers.isEmpty()) item { EmptyState("暂无用户") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (reportUser) {
        ReportDialog("user", userId, appVm) { reportUser = false }
    }

    if (shareProfile && user != null) {
        val u = user!!
        ShareSheet(
            payload = ShareContent(
                shareType = "user",
                targetId = u.id,
                title = u.nickname ?: "用户",
                cover = u.avatarUrl,
                desc = u.bio ?: u.signature
            ),
            appVm = appVm,
            externalText = "${u.nickname ?: ""}\n${shareUrl("user", u.id)}",
            onDismiss = { shareProfile = false }
        )
    }
}
