package org.dalanben.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.DEFAULT_BG
import org.dalanben.app.data.Session
import org.dalanben.app.data.ShareContent
import org.dalanben.app.data.User
import org.dalanben.app.data.PointsData
import org.dalanben.app.data.shareUrl
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.Avatar
import org.dalanben.app.ui.components.EmptyState
import org.dalanben.app.ui.components.LevelBadge
import org.dalanben.app.ui.components.ShareSheet
import org.dalanben.app.ui.components.UserRow
import org.dalanben.app.ui.components.VerifyBadge
import org.dalanben.app.ui.components.PullRefreshWrapper
import org.dalanben.app.util.formatCount
import org.dalanben.app.util.fullUrl
import org.dalanben.app.util.QrUtil

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MeScreen(navController: NavController, appVm: AppViewModel) {
    val context = LocalContext.current
    val user by appVm.user.collectAsState()
    var tabIndex by remember { mutableStateOf(0) }
    var meEnterNonce by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appVm.loadMe() }

    val uid = user?.id ?: Session.user?.id ?: 0

    var showFollowList by remember { mutableStateOf<String?>(null) } // follower | following
    var shareMe by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var followUsers by remember { mutableStateOf(listOf<User>()) }
    val scope = rememberCoroutineScope()

    var pointsData by remember { mutableStateOf<PointsData?>(null) }
    var checkingIn by remember { mutableStateOf(false) }

    fun reloadPoints() {
        scope.launch {
            try { val r = Api.service.points(); if (r.ok) pointsData = r.data } catch (_: Exception) {}
        }
    }
    fun doCheckin() {
        if (checkingIn || pointsData?.checkedToday == true) return
        checkingIn = true
        scope.launch {
            try {
                val r = Api.service.checkin()
                if (r.ok && r.data != null) {
                    val cd = r.data!!
                    pointsData = PointsData(
                        points = cd.points,
                        level = cd.level,
                        levelTitle = cd.levelTitle,
                        nextLevelPoints = cd.nextLevelPoints,
                        pointsToNext = cd.pointsToNext,
                        checkinStreak = cd.checkinStreak,
                        checkedToday = true,
                        totalCheckinDays = cd.totalCheckinDays,
                        inviteEarnedPoints = pointsData?.inviteEarnedPoints ?: user?.inviteEarnedPoints ?: 0,
                        inviteCount = pointsData?.inviteCount ?: 0,
                        inviteCode = pointsData?.inviteCode ?: user?.inviteCode
                    )
                    user?.let { u ->
                        appVm.setUser(u.copy(
                            points = cd.points, level = cd.level, levelTitle = cd.levelTitle,
                            nextLevelPoints = cd.nextLevelPoints, pointsToNext = cd.pointsToNext,
                            checkinStreak = cd.checkinStreak, totalCheckinDays = cd.totalCheckinDays
                        ))
                    }
                    appVm.showToast("签到成功 +${cd.earned} 积分")
                } else if (r.data?.already == true) {
                    appVm.showToast("今天已签到")
                    reloadPoints()
                } else {
                    appVm.showToast(r.msg ?: "签到失败")
                }
            } catch (e: Exception) {
                appVm.showToast("签到失败: ${e.message}")
            } finally {
                checkingIn = false
            }
        }
    }
    LaunchedEffect(user?.id) {
        if (user != null) reloadPoints()
    }

    // 每次进入「我的」页面自动刷新：重载个人资料 + 让作品列表按新 key 重新拉取最新数据
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, dest, _ ->
            if (dest.route?.substringBefore("/") == Routes.ME) {
                meEnterNonce++
                scope.launch { appVm.loadMe() }
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // 作品搜索（带防抖，避免每次输入都请求）
    var keywordInput by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    LaunchedEffect(keywordInput) {
        delay(400)
        keyword = keywordInput
    }

    fun openFollowList(type: String) {
        showFollowList = type
        followUsers = emptyList()
        scope.launch {
            try {
                val r = Api.service.followList(uid, type, 1, 50)
                followUsers = r.data?.list ?: emptyList()
            } catch (_: Exception) { }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PullRefreshWrapper(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    try {
                        appVm.loadMe()
                        reloadPoints()
                        meEnterNonce++
                    } finally {
                        refreshing = false
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) {
        Box(Modifier.fillMaxSize()) {
            if (uid > 0) {
                val type = when (tabIndex) { 1 -> "like"; 2 -> "collect"; else -> "user" }
                PostFeedList(
                    navController = navController,
                    appVm = appVm,
                    refreshKey = "me-$uid-$type-$keyword-$meEnterNonce",
                    emptyMsg = when (tabIndex) { 1 -> "还没有点赞的作品"; 2 -> "还没有收藏的作品"; else -> "还没有发布作品" },
                    fullWidthHeader = true,
                    expandedCards = true,
                    header = {
                        item {
                            // 主页背景图(封面): 未自定义时使用网页版默认背景图
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clickable { navController.navigate(Routes.EDIT_PROFILE) }
                            ) {
                                AsyncImage(
                                    model = fullUrl(user?.bgUrl ?: DEFAULT_BG),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // 用户信息头部
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(user?.avatarUrl, 64) { navController.navigate(Routes.EDIT_PROFILE) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SelectionContainer {
                                            Text(user?.nickname ?: "未登录", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        if (user?.isBlueV == 1) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Filled.Verified, null,
                                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                        if (!user?.title.isNullOrBlank()) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.small) {
                                                Text(user?.title ?: "", fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    SelectionContainer {
                                        Text("蓝本号: ${user?.blueId ?: "-"}", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!user?.ipRegion.isNullOrBlank()) {
                                        Text("IP属地: ${user?.ipRegion}", fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                                    }
                                    if (!user?.signature.isNullOrBlank()) {
                                        Text(user?.signature ?: "", fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                IconButton({ shareMe = true }) { Icon(Icons.Filled.Share, "分享") }
                                IconButton({ navController.navigate(Routes.SETTINGS) }) {
                                    Icon(Icons.Filled.Settings, "设置")
                                }
                            }

                            // 统计
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Column(Modifier.weight(1f).clickable { openFollowList("following") },
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(formatCount(user?.followingCount ?: 0), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("关注", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f).clickable { openFollowList("followers") },
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(formatCount(user?.followerCount ?: 0), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("粉丝", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(formatCount(user?.postCount ?: 0), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("作品", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(formatCount(user?.likeCount ?: 0), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("获赞", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            // 积分中心入口
                            OutlinedButton(
                                { navController.navigate(Routes.POINTS_CENTER) },
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            ) {
                                Text("积分中心", fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            // 签到卡片
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("每日签到", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface)
                                            val pd = pointsData
                                            Text(
                                                "连续 ${pd?.checkinStreak ?: user?.checkinStreak ?: 0} 天 · 累计 ${pd?.totalCheckinDays ?: user?.totalCheckinDays ?: 0} 天",
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${pointsData?.points ?: user?.points ?: 0} 积分", fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.height(4.dp))
                                            LevelBadge(user?.level ?: 0, user?.levelTitle)
                                            Spacer(Modifier.height(4.dp))
                                            VerifyBadge(user?.verifyTitle, user?.verifyStyle)
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = { doCheckin() },
                                        enabled = (pointsData?.checkedToday != true) && !checkingIn,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            when {
                                                checkingIn -> "签到中..."
                                                pointsData?.checkedToday == true -> "今日已签到 ✓"
                                                else -> "签到领积分"
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // 快捷操作：两两一排放签到下面
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Row(Modifier.fillMaxWidth()) {
                                        OutlinedButton({ navController.navigate(Routes.EDIT_PROFILE) },
                                            Modifier.weight(1f).padding(4.dp)) {
                                            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("编辑资料", fontSize = 13.sp)
                                        }
                                        OutlinedButton({ showQrDialog = true },
                                            Modifier.weight(1f).padding(4.dp)) {
                                            Icon(Icons.Filled.QrCode, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("我的蓝本码", fontSize = 13.sp)
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth()) {
                                        OutlinedButton({ navController.navigate(Routes.QR_SCAN) },
                                            Modifier.weight(1f).padding(4.dp)) {
                                            Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("扫一扫", fontSize = 13.sp)
                                        }
                                        OutlinedButton({ navController.navigate(Routes.FEEDBACK) },
                                            Modifier.weight(1f).padding(4.dp)) {
                                            Icon(Icons.Filled.Feedback, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("意见反馈", fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // 邀请朋友注册
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                Column {
                                    MenuRow(Icons.Filled.PersonAdd, "邀请朋友注册得积分") {
                                        val link = "https://dalanben.org/register?ic=${pointsData?.inviteCode ?: user?.inviteCode ?: ""}"
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("invite", link))
                                        appVm.showToast("已复制邀请链接")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // 无偿捐赠
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E7))) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Favorite, null,
                                            tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("支持大蓝本", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("本项目完全免费，你的每一份捐赠都是对独立社区的支持与鼓励。",
                                        fontSize = 11.sp, color = Color(0xFF795548), lineHeight = 16.sp)
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW,
                                                Uri.parse("https://ifdian.net/order/create?plan_id=e6ac580c94ad11f18c7a52540025c377&product_type=0&remark=&affiliate_code="))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, Color(0xFFE65100)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100))
                                    ) {
                                        Text("无偿捐赠", fontSize = 13.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
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
                        val r = Api.service.postList(uid, type, page, 12, keyword.takeIf { it.isNotBlank() })
                        if (r.ok) r.data?.list ?: emptyList() else throw IllegalStateException(r.msg ?: "加载失败")
                    }
                )
            } else {
                EmptyState("请先登录")
            }
        }
        }

        // 后台管理
        if (Session.isAdmin) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column {
                    MenuRow(Icons.Filled.AdminPanelSettings, "后台管理") { navController.navigate(Routes.ADMIN) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

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

        if (shareMe && user != null) {
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
                onDismiss = { shareMe = false }
            )
        }

        // 蓝本码弹窗
        if (showQrDialog && user != null) {
            val qrBitmap = remember(user) {
                QrUtil.generate("https://dalanben.org/s/user/${user!!.id}", 400)
            }
            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                title = { Text("我的蓝本码", fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "蓝本码",
                            modifier = Modifier.size(240.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("蓝本号: ${user!!.blueId}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("扫一扫即可进入我的主页", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline)
                    }
                },
                confirmButton = {
                    TextButton({ showQrDialog = false }) { Text("关闭") }
                }
            )
        }
    }
}
