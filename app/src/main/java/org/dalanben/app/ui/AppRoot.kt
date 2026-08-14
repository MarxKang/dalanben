package org.dalanben.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.dalanben.app.BuildConfig
import org.dalanben.app.data.Announcement
import org.dalanben.app.data.Api
import org.dalanben.app.data.AppVersion
import org.dalanben.app.data.AppTheme
import org.dalanben.app.data.RemoteConfigManager
import org.dalanben.app.data.SplashData
import org.dalanben.app.data.Session
import org.dalanben.app.data.SessionManager
import org.dalanben.app.data.User
import org.dalanben.app.data.isPhoneVerified
import org.dalanben.app.ui.screens.*
import org.dalanben.app.ui.theme.DalanbenTheme
import org.dalanben.app.ui.theme.ThemeMode
import org.dalanben.app.util.formatTime
import org.dalanben.app.util.fullUrl
import org.dalanben.app.util.Notify

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgotPassword"
    const val ADMIN_CENTER = "adminCenter"
    const val HOME = "home"
    const val TOPICS = "topics"
    const val MESSAGES = "messages"
    const val ME = "me"
    const val POST_DETAIL = "post/{postId}"
    const val PUBLISH = "publish"
    const val PROFILE = "profile/{userId}"
    const val EDIT_PROFILE = "editProfile"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val SEARCH = "search"
    const val TOPIC_DETAIL = "topic/{topicId}"
    const val CHAT = "chat/{peerId}"
    const val IMAGE = "image/{url}"
    const val VIDEO = "video/{url}/{cover}"
    const val FEEDBACK = "feedback"
    const val ADMIN = "admin"
    const val QR_SCAN = "qr_scan"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val RULES = "rules"
    const val CHILDREN = "children"
    const val WELCOME = "welcome"
    const val POINTS_CENTER = "pointsCenter"
    const val WEB_VIEW = "webView/{title}/{url}"
    const val OPEN_SOURCE = "openSource"
    const val CLEAN_CACHE = "cleanCache"
    const val CHANGELOG = "changelog"

    fun postDetail(id: Int) = "post/$id"
    fun profile(id: Int) = "profile/$id"
    fun topicDetail(id: Int) = "topic/$id"
    fun chat(id: Int) = "chat/$id"
    fun image(url: String) = "image/${android.net.Uri.encode(url)}"
    fun video(url: String, cover: String) =
        "video/${android.net.Uri.encode(url)}/${android.net.Uri.encode(cover.ifBlank { "none" })}"
    fun register(ic: String? = null) = if (ic.isNullOrBlank()) "register" else "register?ic=$ic"
    fun webView(title: String, url: String) =
        "webView/${android.net.Uri.encode(title)}/${android.net.Uri.encode(url)}"
}

data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot(initialNav: String? = null, initialUri: android.net.Uri? = null) {
    val navController = rememberNavController()
    val appVm: AppViewModel = viewModel()
    val start = if (Session.isLoggedIn) Routes.HOME else Routes.LOGIN

    val themeMode by appVm.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val localCtx = LocalContext.current

    // 启动时拉取热更新配置（远程主题、节日入口、Banner、公告、KV 开关）
    var remoteTheme by remember { mutableStateOf<AppTheme?>(RemoteConfigManager.getCached(localCtx)?.theme) }
    var announcement by remember { mutableStateOf<Announcement?>(null) }
    var splash by remember { mutableStateOf<SplashData?>(null) }
    LaunchedEffect(Unit) {
        try {
            val cfg = RemoteConfigManager.fetch(localCtx)
            if (cfg != null) {
                remoteTheme = cfg.theme
                val a = cfg.announcement
                if (a != null) {
                    val seen = SessionManager.getAnnouncementSeenId(localCtx)
                    if (!(a.showOnce == 1 && seen == a.id)) {
                        announcement = a
                    }
                }
                appVm.onConfigFetched()
            }
        } catch (_: Exception) {
            // 失败时回退旧版公告接口
            try {
                val r = Api.service.announcementActive()
                val a = r.data?.announcement
                if (r.ok && a != null) {
                    val seen = SessionManager.getAnnouncementSeenId(localCtx)
                    if (!(a.showOnce == 1 && seen == a.id)) {
                        announcement = a
                    }
                }
            } catch (_: Exception) { }
        }
    }

    // 启动图：服务端下发生效的则全屏展示（管理员后台可增/改/启停）
    LaunchedEffect(Unit) {
        try {
            val r = Api.service.splashActive()
            if (r.ok && r.data?.splash != null) splash = r.data!!.splash
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (Session.isLoggedIn) appVm.loadUnread()
            delay(20000)
        }
    }

    // 进入 App / 从后台回到前台时同步用户状态(含手机号绑定状态), 避免顶部横幅误报
    // 同时上报 IP 属地 (服务端控制 5 小时刷新一次, 每次前台都会调用但服务端节流; 失败静默不影响使用)
    val appScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Session.isLoggedIn) {
                    appVm.loadMe()
                    appScope.launch {
                        try { Api.service.updateIpRegion() } catch (_: Exception) { }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var updateVersion by remember { mutableStateOf<AppVersion?>(null) }
    LaunchedEffect(Unit) {
        val latest = appVm.checkAppUpdate()
        if (latest != null) updateVersion = latest
    }

    // 处理通知点击: 自动跳转到消息页
    LaunchedEffect(initialNav) {
        if (initialNav == "messages" && Session.isLoggedIn) {
            delay(600)
            navController.navigate(Routes.MESSAGES)
        }
    }

    val ctx = LocalContext.current

    // 深度链接处理: 从外部链接/分享/扫码打开 App
    fun handleDeepLink(uri: android.net.Uri?) {
        if (uri == null) return
        // 邀请注册深链: 即使未登录也记录邀请码并跳到注册页
        if ((uri.path ?: "").startsWith("/register")) {
            val ic = uri.getQueryParameter("ic") ?: uri.getQueryParameter("invite_code")
            if (!ic.isNullOrBlank()) {
                if (!Session.isLoggedIn) {
                    navController.navigate(Routes.register(ic)) { popUpTo(Routes.REGISTER) { inclusive = true } }
                }
                return
            }
        }
        // 优先匹配 path, 再匹配 fragment (兼容 /#/post/1 格式)
        // 注意: 作品/主页/话题均为公开内容, 未登录也可直接打开(个人主页接口无需鉴权)
        val candidates = listOfNotNull(uri.path, uri.fragment)
        val re = Regex("(/s)?/(post|user|topic)/(\\d+)")
        for (c in candidates) {
            val m = re.find(c.orEmpty())
            if (m != null) {
                val type = m.groupValues[2]
                val id = m.groupValues[3].toIntOrNull() ?: 0
                if (id <= 0) continue
                val route = when (type) {
                    "post" -> Routes.postDetail(id)
                    "user" -> Routes.profile(id)
                    "topic" -> Routes.topicDetail(id)
                    else -> null
                }
                if (route != null) {
                    navController.navigate(route)
                    return
                }
            }
        }
    }
    LaunchedEffect(initialUri) { handleDeepLink(initialUri) }

    // 定时轮询未读数, 有新增时推送系统通知
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (!Session.isLoggedIn) continue
            try {
                val resp = Api.service.unreadCount()
                if (resp.ok && resp.data != null) {
                    Notify.checkAndNotify(ctx, resp.data.msg, resp.data.notif)
                }
            } catch (_: Exception) { }
        }
    }

    val toast by appVm.toast.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snackbarHost.showSnackbar(it); appVm.consumeToast() } }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/") ?: start
    val mainTabs = listOf(
        TabItem(Routes.HOME, "首页", Icons.Filled.Home),
        TabItem(Routes.TOPICS, "话题", Icons.Filled.LocalFireDepartment),
        TabItem(Routes.MESSAGES, "消息", Icons.Filled.Forum),
        TabItem(Routes.ME, "我的", Icons.Filled.Person)
    )
    val showBottomBar = mainTabs.any { it.route == currentRoute }
    val unreadTotal by appVm.unread.collectAsState()

    val scope = rememberCoroutineScope()
    var showAppeal by remember { mutableStateOf(false) }
    var appealText by remember { mutableStateOf("") }
    var myAppeal by remember { mutableStateOf<Map<String, Any?>?>(null) }

    DalanbenTheme(darkTheme = darkTheme, remoteTheme = remoteTheme) {
        androidx.compose.runtime.CompositionLocalProvider(LocalNavController provides navController) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        val cur = navBackStackEntry?.destination
                        // 左半边: 首页 + 话题
                        mainTabs.take(2).forEach { tab ->
                            NavigationBarItem(
                                selected = cur?.hierarchy?.any { it.route == tab.route } == true,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                        // 中心发布按钮
                        NavigationBarItem(
                            selected = false,
                            onClick = { navController.navigate(Routes.PUBLISH) },
                            icon = {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    shadowElevation = 4.dp
                                ) {
                                    Icon(Icons.Filled.Add, "发布",
                                        tint = Color.White,
                                        modifier = Modifier.padding(12.dp))
                                }
                            }
                        )
                        // 右半边: 消息 + 我的
                        mainTabs.drop(2).forEach { tab ->
                            NavigationBarItem(
                                selected = cur?.hierarchy?.any { it.route == tab.route } == true,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true; restoreState = true
                                    }
                                },
                                icon = {
                                    if (tab.route == Routes.MESSAGES && (unreadTotal?.total ?: 0) > 0) {
                                        BadgedBox(badge = { Badge { Text((unreadTotal?.total ?: 0).toString()) } }) {
                                            Icon(tab.icon, tab.label)
                                        }
                                    } else {
                                        Icon(tab.icon, tab.label)
                                    }
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val me by appVm.user.collectAsState()
            LaunchedEffect(me?.id) {
                val u = me
                if (u != null && (u.status == "banned" || u.status == "muted" || u.activePenalty != null)) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        myAppeal = (Api.service.myAppeal().data as? Map<String, Any?>)?.get("appeal") as? Map<String, Any?>
                    } catch (_: Exception) { }
                }
            }
            Column(Modifier.padding(innerPadding).fillMaxSize()) {
                me?.let { u ->
                    if (u.status == "banned" || u.status == "muted" || u.activePenalty != null)
                        BanBanner(u, myAppeal?.get("status") as? String, onAppeal = { showAppeal = true })
                    if (!isPhoneVerified(u.phoneVerified)) {
                        Surface(color = Color(0xFFE8F0FE), tonalElevation = 0.dp) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PhoneAndroid, null, tint = Color(0xFF1A56C4), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("验证手机号后可发布内容和评论", color = Color(0xFF1A56C4), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                    Text("去绑定", fontSize = 13.sp, color = Color(0xFF1A56C4))
                                }
                            }
                        }
                    }
                }
                NavHost(
                    navController = navController, startDestination = start,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    enterTransition = { fadeIn(animationSpec = tween(150)) },
                    exitTransition = { fadeOut(animationSpec = tween(150)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                    popExitTransition = { fadeOut(animationSpec = tween(150)) }
                ) {
                    composable(Routes.LOGIN) { LoginScreen(navController, appVm) }
                    composable(Routes.FORGOT_PASSWORD) { ForgotPasswordScreen(navController, appVm) }
                    composable(
                        Routes.REGISTER,
                        arguments = listOf(navArgument("ic") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { back -> RegisterScreen(navController, appVm, back.arguments?.getString("ic")) }
                    composable(Routes.WELCOME) { WelcomeScreen(navController, appVm) }
                    composable(Routes.HOME) { HomeScreen(navController, appVm) }
                    composable(Routes.TOPICS) { TopicsScreen(navController, appVm) }
                    composable(Routes.MESSAGES) { MessagesScreen(navController, appVm) }
                    composable(Routes.ME) { MeScreen(navController, appVm) }
                    composable(Routes.POST_DETAIL, arguments = listOf(navArgument("postId") { type = NavType.IntType })) { back ->
                        PostDetailScreen(navController, appVm, back.arguments?.getInt("postId") ?: 0) }
                    composable(Routes.PUBLISH) { PublishScreen(navController, appVm, 0) }
                    composable(Routes.PROFILE, arguments = listOf(navArgument("userId") { type = NavType.IntType })) { back ->
                        ProfileScreen(navController, appVm, back.arguments?.getInt("userId") ?: 0) }
                    composable(Routes.EDIT_PROFILE) { EditProfileScreen(navController, appVm) }
                    composable(Routes.SETTINGS) { SettingsScreen(navController, appVm) }
                    composable(Routes.CLEAN_CACHE) { CacheCleanScreen(onBack = { navController.popBackStack() }) }
                    composable(Routes.CHANGELOG) { ChangelogScreen(onBack = { navController.popBackStack() }) }
                    composable(Routes.POINTS_CENTER) { PointsCenterScreen(navController, appVm) }
                    composable(Routes.ADMIN_CENTER) { AdminCenterScreen(navController, appVm) }
                    composable(Routes.NOTIFICATIONS) { NotificationsScreen(navController, appVm) }
                    composable(Routes.SEARCH) { SearchScreen(navController, appVm) }
                    composable(Routes.TOPIC_DETAIL, arguments = listOf(navArgument("topicId") { type = NavType.IntType })) { back ->
                        TopicDetailScreen(navController, appVm, back.arguments?.getInt("topicId") ?: 0) }
                    composable(Routes.CHAT, arguments = listOf(navArgument("peerId") { type = NavType.IntType })) { back ->
                        ChatScreen(navController, appVm, back.arguments?.getInt("peerId") ?: 0) }
                    composable(Routes.IMAGE, arguments = listOf(navArgument("url") { type = NavType.StringType })) { back ->
                        ImageViewerScreen(navController, back.arguments?.getString("url") ?: "") }
                    composable(Routes.VIDEO, arguments = listOf(navArgument("url") { type = NavType.StringType }, navArgument("cover") { type = NavType.StringType })) { back ->
                        VideoPlayerScreen(navController, back.arguments?.getString("url") ?: "", back.arguments?.getString("cover") ?: "") }
                    composable(Routes.FEEDBACK) { FeedbackScreen(navController, appVm) }
                    composable(Routes.ADMIN) { AdminScreen(navController, appVm) }
                    composable(Routes.QR_SCAN) { QrScanScreen(navController) { result ->
                        val uri = try { android.net.Uri.parse(result) } catch (_: Exception) { null }
                        handleDeepLink(uri)
                        navController.popBackStack()
                    } }
                    composable(Routes.PRIVACY) { LegalScreen(navController, "privacy") }
                    composable(Routes.TERMS) { LegalScreen(navController, "terms") }
                    composable(Routes.RULES) { LegalScreen(navController, "rules") }
                    composable(Routes.CHILDREN) { LegalScreen(navController, "children") }
                    composable(Routes.OPEN_SOURCE) { OpenSourceScreen(navController) }
                    composable(
                        Routes.WEB_VIEW,
                        arguments = listOf(
                            navArgument("title") { type = NavType.StringType },
                            navArgument("url") { type = NavType.StringType }
                        )
                    ) { back ->
                        WebViewScreen(
                            navController = navController,
                            title = back.arguments?.getString("title")?.let { android.net.Uri.decode(it) } ?: "",
                            url = back.arguments?.getString("url")?.let { android.net.Uri.decode(it) } ?: ""
                        )
                    }
                }
                if (showAppeal) {
                    AlertDialog(
                        onDismissRequest = { showAppeal = false },
                        title = { Text("账号申诉") },
                        text = {
                            Column(Modifier.imePadding()) {
                                Text("如您认为本次处罚有误，请填写申诉理由，管理员会尽快核实处理。",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(appealText, { appealText = it }, label = { Text("申诉理由") },
                                    placeholder = { Text("请说明情况，至少 10 个字") },
                                    modifier = Modifier.fillMaxWidth(), minLines = 4)
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = appealText.trim().length >= 10,
                                onClick = {
                                    scope.launch {
                                        try {
                                            val r = Api.service.appeal(mapOf(
                                                "appeal_type" to "account", "target_id" to 0, "content" to appealText.trim()))
                                            if (r.ok) { appVm.showToast("申诉已提交，请耐心等待处理"); myAppeal = mapOf("status" to "pending") }
                                            else appVm.showToast(r.msg ?: "提交失败")
                                        } catch (e: Exception) { appVm.showToast("提交失败") }
                                        showAppeal = false
                                    }
                                }) { Text("提交申诉") }
                        },
                        dismissButton = { TextButton({ showAppeal = false }) { Text("取消") } }
                    )
                }
            }
            val downloadState by appVm.download.collectAsState()
            downloadState?.let { DownloadProgressOverlay(appVm, it) }
            updateVersion?.let { v -> UpdateDialog(v, onDismiss = { updateVersion = null }) }
            announcement?.let { a ->
                AnnouncementDialog(a, onDismiss = {
                    if (a.showOnce == 1) { scope.launch { SessionManager.setAnnouncementSeenId(ctx, a.id) } }
                    announcement = null
                })
            }
        }
    }
        // Snackbar 移到屏幕中上部，避免被键盘遮挡
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.fillMaxWidth().padding(top = 120.dp)
        )
        // 启动图：全屏最顶层（zIndex 保证盖过底部导航栏与 Snackbar）
        splash?.let { s ->
            SplashOverlay(s, onDismiss = { splash = null }, modifier = Modifier.zIndex(200f))
        }
    } // Box
    } // CompositionLocalProvider
} // DalanbenTheme

@Composable
fun UpdateDialog(version: AppVersion, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val isForce = version.forceUpdate == 1
    val metaText = buildString {
        if (version.createdAt > 0) {
            append(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(version.createdAt * 1000L)))
        }
        if (version.sizeText.isNotBlank()) {
            if (isNotEmpty()) append("  ·  ")
            append("安装包 ${version.sizeText}")
        }
    }

    if (isForce) {
        // 强制更新: 全屏遮罩，不可关闭；clickable(空实现) 吞掉所有点击，防止穿透到下层页面
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    enabled = true,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { },
            contentAlignment = Alignment.Center
        ) {
            Card(Modifier.widthIn(max = 340.dp), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("发现新版本 v${version.versionName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (metaText.isNotBlank()) {
                        Text(metaText, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text("本次为强制更新，升级后即可继续使用。",
                        color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    val log = version.changelog.ifBlank { "修复若干已知问题，提升使用体验。" }
                    Text(log, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Button({
                        val url = if (version.downloadUrl.startsWith("http")) version.downloadUrl
                                   else "https://dalanben.org" + version.downloadUrl
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        catch (_: Exception) { android.widget.Toast.makeText(ctx, "无法打开更新链接", android.widget.Toast.LENGTH_SHORT).show() }
                    }, Modifier.fillMaxWidth()) { Text("去更新") }
                }
            }
        }
    } else {
        // 非强制: 普通弹窗，可关闭
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = { Text("发现新版本 v${version.versionName}") },
            text = {
                Column(Modifier.imePadding()) {
                    if (metaText.isNotBlank()) {
                        Text(metaText, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                    }
                    val log = version.changelog.ifBlank { "修复若干已知问题，提升使用体验。" }
                    Text(log, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton({
                    val url = if (version.downloadUrl.startsWith("http")) version.downloadUrl
                               else "https://dalanben.org" + version.downloadUrl
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (_: Exception) { android.widget.Toast.makeText(ctx, "无法打开更新链接", android.widget.Toast.LENGTH_SHORT).show() }
                    onDismiss()
                }) { Text("去更新") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
        )
    }
}

@Composable
fun BanBanner(user: User, appealStatus: String? = null, onAppeal: () -> Unit = {}) {
    val penType = user.activePenalty?.penaltyType ?: ""
    val isBan = penType.contains("ban") || user.status == "banned"
    val isMute = penType.contains("mute") || user.status == "muted"
    val isLimit = penType.contains("limit")
    val (bg, fg, title) = when {
        isBan -> Triple(Color(0xFFFDECEA), Color(0xFFB3261E), "账号已被封禁")
        isMute -> Triple(Color(0xFFFFF4E5), Color(0xFF9A5B00), "账号已被禁言")
        isLimit -> Triple(Color(0xFFE8F0FE), Color(0xFF1A56C4), "账号功能受限")
        else -> Triple(Color(0xFFFFF4E5), Color(0xFF9A5B00), "账号受限")
    }
    val reason = user.activePenalty?.reason?.takeIf { it.isNotBlank() }
    val sb = StringBuilder()
    if (reason != null) sb.append("原因：$reason")
    if ((isMute || isLimit) && (user.activePenalty?.expireAt ?: 0) > 0) {
        if (sb.isNotEmpty()) sb.append("\u3000")
        val remaining = user.activePenalty!!.expireAt - (System.currentTimeMillis() / 1000)
        sb.append("解封时间：${formatRemaining(remaining)}")
    }
    if (sb.isEmpty()) sb.append(when {
        isBan -> "您的账号因违反社区规定已被封禁，部分功能不可用。"
        isMute -> "您的账号因违反社区规定已被禁言，暂时无法发言。"
        isLimit -> "您的账号因违反社区规定部分功能受限。"
        else -> "您的账号因违反社区规定受限。"
    })
    val detail = sb.toString()
    Surface(color = bg, tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Block, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(detail, color = fg, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (appealStatus == "pending") {
                OutlinedButton(onClick = {}, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = fg)) { Text("审核中", fontSize = 13.sp, color = fg) }
            } else {
                OutlinedButton(onClick = onAppeal, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = fg)) { Text("申诉", fontSize = 13.sp, color = fg) }
            }
        }
    }
}

/** 剩余时间格式化: 正数=剩余时间, 负数=已解封 */
private fun formatRemaining(remaining: Long): String {
    if (remaining <= 0) return "已解封"
    return when {
        remaining < 60 -> "${remaining}秒后"
        remaining < 3600 -> "${remaining / 60}分钟后"
        remaining < 86400 -> "${remaining / 3600}小时后"
        remaining < 86400 * 30 -> "${remaining / 86400}天后"
        remaining < 86400 * 365 -> "${remaining / (86400 * 30)}个月后"
        else -> "${remaining / (86400 * 365)}年后"
    }
}

/** 进入 App 时的公告弹窗：文字 + 图片，支持仅显示一次，支持点击跳转 */
@Composable
fun AnnouncementDialog(a: Announcement, onDismiss: () -> Unit) {
    val navController = LocalNavController.current
    val hasAction = !a.actionType.isNullOrBlank() && a.actionType != "none" && !a.actionTarget.isNullOrBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.title?.takeIf { it.isNotBlank() } ?: "公告") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    a.content ?: "",
                    fontSize = 14.sp, lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                a.images.forEach { url ->
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = url, contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("我知道了") }
                if (hasAction && navController != null) {
                    TextButton(onClick = {
                        onDismiss()
                        handleRemoteAction(navController, a.actionType, a.actionTarget, a.title ?: "")
                    }) { Text("查看详情") }
                }
            }
        },
    )
}

/** 下载进度浮层：下载/生成水印期间覆盖在界面上，结束时由 downloadPost 调用 dismissDownload 关闭 */
@Composable
/** 把秒数格式化为「X 秒」/「X 分 Y 秒」 */
private fun formatEta(sec: Int): String = when {
    sec < 60 -> "$sec 秒"
    else -> "${sec / 60} 分 ${sec % 60} 秒"
}

@Composable
fun DownloadProgressOverlay(appVm: AppViewModel, state: DownloadUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                enabled = false,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {}
    ) {
        Card(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 320.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    state.phase,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(14.dp))
                if (state.progress >= 0) {
                    LinearProgressIndicator(
                        progress = (state.progress / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.progress}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                }
                // 预计剩余时间 / 已等待时间
                val info = when {
                    state.progress >= 0 && state.etaSeconds != null && state.etaSeconds > 0 ->
                        "预计剩余 ${formatEta(state.etaSeconds)}"
                    state.progress < 0 && state.elapsedSeconds > 0 ->
                        "已等待 ${formatEta(state.elapsedSeconds)}"
                    else -> ""
                }
                if (info.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        info,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { appVm.cancelDownload() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("停止", fontSize = 14.sp)
                }
            }
        }
    }
}

/** 供 Dialog 等独立作用域获取 NavController 的 CompositionLocal */
val LocalNavController = androidx.compose.runtime.compositionLocalOf<androidx.navigation.NavController?> { null }

/** 全屏启动图：倒计时可跳过，向上滑动跳转链接（action_type=url），超时自动关闭。
 *  点击不再触发任何动作（避免误触跳过）；上滑超过阈值才打开链接并关闭。
 *  背景用主题浅色而非纯黑，避免图片加载中/失败时整屏黑；图片加载失败给提示与进入按钮。 */
@Composable
fun SplashOverlay(splash: SplashData, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var remaining by remember(splash.id) { mutableStateOf(splash.duration.coerceIn(1, 30)) }

    LaunchedEffect(splash.id) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
        onDismiss()
    }

    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .then(modifier)
            .background(MaterialTheme.colorScheme.surface)
            // 滑动交互：向上滑动超过阈值 → 打开链接并关闭（点击无动作，避免误触）
            .pointerInput(splash.id) {
                var swiped = false
                var acc = 0f
                val threshold = with(density) { 120.dp.toPx() }
                detectVerticalDragGestures(
                    onDragEnd = { acc = 0f },
                    onDragCancel = { acc = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        acc += dragAmount
                        if (!swiped && acc < -threshold) {
                            swiped = true
                            if (splash.action_type == "url" && splash.action_target.isNotBlank()) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(splash.action_target)))
                                } catch (_: Exception) {}
                            }
                            onDismiss()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = fullUrl(splash.image_url),
            contentDescription = splash.title,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            error = {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🖼️", fontSize = 42.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("启动图加载失败", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDismiss) { Text("直接进入") }
                }
            }
        )
        // 右上角跳过
        Surface(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 44.dp, end = 16.dp),
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.35f),
        ) {
            Text(
                "跳过 ${remaining}s",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        // 上滑提示（仅当配置了跳转链接时显示）
        if (splash.action_type == "url" && splash.action_target.isNotBlank()) {
            Text(
                "上滑查看详情",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        // 底部标题
        if (splash.title.isNotBlank()) {
            Text(
                splash.title,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
