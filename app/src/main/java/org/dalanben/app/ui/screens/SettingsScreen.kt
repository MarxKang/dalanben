package org.dalanben.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import org.dalanben.app.ui.components.GifImage
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import org.dalanben.app.BuildConfig
import org.dalanben.app.data.Api
import org.dalanben.app.data.AppVersion
import org.dalanben.app.data.Session
import org.dalanben.app.data.User
import org.dalanben.app.data.isPhoneVerified
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.UpdateDialog
import org.dalanben.app.ui.components.EmptyState
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.ui.components.UserRow
import org.dalanben.app.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, appVm: AppViewModel) {
    val user by appVm.user.collectAsState()
    val themeMode by appVm.themeMode.collectAsState()
    var privacyDm by remember(user) { mutableStateOf((user?.privacyDm ?: 0) == 1) }
    var privacyAt by remember(user) { mutableStateOf((user?.privacyAt ?: 0) == 1) }
    // 隐藏关注列表 / 隐藏粉丝列表(兼容旧版单一 privacy_follow)
    var privacyFollowing by remember(user) {
        mutableStateOf(((user?.privacyFollowing ?: user?.privacyFollow) ?: 0) == 1)
    }
    var privacyFollowers by remember(user) {
        mutableStateOf(((user?.privacyFollowers ?: user?.privacyFollow) ?: 0) == 1)
    }
    var showPwDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var showPw2 by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showBlockList by remember { mutableStateOf(false) }
    var blockedUsers by remember { mutableStateOf(listOf<User>()) }
    var updateVersion by remember { mutableStateOf<AppVersion?>(null) }
    var showPhoneDialog by remember { mutableStateOf(false) }
    // 图形验证码共用状态
    var showCaptchaDialog2 by remember { mutableStateOf(false) }
    var captchaDialogImg by remember { mutableStateOf("") }
    var captchaDialogKey by remember { mutableStateOf("") }
    var captchaDialogInput by remember { mutableStateOf("") }
    var captchaDialogTarget by remember { mutableStateOf("") } // "pw" or "bind"
    var captchaDialogPhone by remember { mutableStateOf("") }
    var captchaDialogLoading by remember { mutableStateOf(false) }
    // 发送验证码后的重发倒计时(与登录/注册 Auth 的 120s 保持一致)
    var codeCountdown by remember { mutableStateOf(0) }
    LaunchedEffect(codeCountdown) {
        if (codeCountdown > 0) { delay(1000); codeCountdown-- }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun checkUpdate() = scope.launch {
        val latest = appVm.checkAppUpdate()
        if (latest != null) updateVersion = latest
        else appVm.showToast("已是最新版本")
    }

    LaunchedEffect(Unit) { appVm.loadMe() }

    fun savePrivacy() = scope.launch {
        try {
            val r = Api.service.privacy(mapOf(
                "privacy_dm" to privacyDm,
                "privacy_at" to privacyAt,
                "privacy_following" to privacyFollowing,
                "privacy_followers" to privacyFollowers,
                "privacy_follow" to (privacyFollowing || privacyFollowers) // 兼容旧版
            ))
            if (r.ok) { appVm.showToast("隐私设置已保存"); appVm.loadMe() }
            else appVm.showToast(r.msg ?: "保存失败")
        } catch (_: Exception) { appVm.showToast("网络错误") }
    }

    fun logout() = scope.launch {
        try { Api.service.logout() } catch (_: Exception) {}
        Session.clear(context)
        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("设置", onBack = { navController.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("隐私设置", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Card {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("私信限制", fontSize = 14.sp)
                            Text("开启后仅互关用户可给我发私信", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(privacyDm, { privacyDm = it; savePrivacy() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("@ 限制", fontSize = 14.sp)
                            Text("开启后仅互关用户可@我", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(privacyAt, { privacyAt = it; savePrivacy() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("隐藏我关注的人", fontSize = 14.sp)
                            Text("开启后其他人不可见我关注的用户", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(privacyFollowing, { privacyFollowing = it; savePrivacy() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("隐藏我的粉丝", fontSize = 14.sp)
                            Text("开启后其他人不可见我的粉丝列表", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(privacyFollowers, { privacyFollowers = it; savePrivacy() })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 手机号
            val userPhone = user?.phone
            val phoneVerified = user?.phoneVerified
            val isVerified = isPhoneVerified(phoneVerified)
            Text("手机号", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Card {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isVerified) (userPhone ?: "") else "未绑定", fontSize = 14.sp)
                            Text(
                                if (isVerified) "已验证" else "验证手机号后可发布内容和评论",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showPhoneDialog = true }) {
                            Text(if (isVerified) "更换" else "绑定") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 外观
            Text("外观", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Card {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = { appVm.setThemeMode(ThemeMode.SYSTEM) },
                            label = { Text("跟随系统") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { appVm.setThemeMode(ThemeMode.LIGHT) },
                            label = { Text("浅色") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { appVm.setThemeMode(ThemeMode.DARK) },
                            label = { Text("深色") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> "跟随系统：根据手机系统设置自动切换浅色 / 深色"
                            ThemeMode.LIGHT -> "当前为浅色模式"
                            ThemeMode.DARK -> "当前为深色模式"
                        },
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("账号与安全", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Card {
                Column {
                    TextButton({ showPwDialog = true }, Modifier.fillMaxWidth()) { Text("修改密码") }
                    TextButton({ navController.navigate(Routes.FORGOT_PASSWORD) }, Modifier.fillMaxWidth()) { Text("忘记密码（手机验证码重置）") }
                    TextButton({
                        showBlockList = true
                        scope.launch {
                            try { blockedUsers = Api.service.blockList().data?.list ?: emptyList() }
                            catch (_: Exception) {}
                        }
                    }, Modifier.fillMaxWidth()) { Text("黑名单管理") }
                    TextButton({ showLogoutDialog = true }, Modifier.fillMaxWidth()) {
                        Text("退出登录", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton({ showDeleteDialog = true }, Modifier.fillMaxWidth()) {
                        Text("注销账号", color = Color(0xFFEF4444))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("存储空间", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Card {
                Column {
                    TextButton({ navController.navigate(Routes.CLEAN_CACHE) },
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("垃圾清理", fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text("深度分析占用并一键清理缓存", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("›", fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("关于", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Card {
                Column {
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("当前版本", fontSize = 14.sp)
                            Text("v${BuildConfig.VERSION_NAME}", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton({ navController.navigate(Routes.CHANGELOG) }) { Text("更新日志") }
                        TextButton({ checkUpdate() }) { Text("检查更新") }
                    }
                    Spacer(Modifier.height(2.dp).fillMaxWidth())
                    TextButton({ navController.navigate(Routes.PRIVACY) },
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("隐私政策", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Text("›", fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(2.dp).fillMaxWidth())
                    TextButton({ navController.navigate(Routes.TERMS) },
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("用户协议", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Text("›", fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(2.dp).fillMaxWidth())
                    TextButton({ navController.navigate(Routes.RULES) },
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("社区规范", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Text("›", fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(2.dp).fillMaxWidth())
                    TextButton({ navController.navigate(Routes.CHILDREN) },
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("儿童政策", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Text("›", fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(2.dp).fillMaxWidth())
                    TextButton({ navController.navigate(Routes.OPEN_SOURCE) },
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("开源代码", fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Text("›", fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("大蓝本 · 散帅男性成长社区", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    // 版本更新弹窗
    updateVersion?.let { v ->
        UpdateDialog(v, onDismiss = { updateVersion = null })
    }

    // 修改密码
    if (showPwDialog) {
        var oldPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmNewPw by remember { mutableStateOf("") }
        var pwCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPwDialog = false },
            title = { Text("修改密码") },
            text = {
                Column(Modifier.imePadding()) {
                    OutlinedTextField(oldPw, { oldPw = it }, label = { Text("原密码") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(newPw, { newPw = it }, label = { Text("新密码(6-32位)") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(confirmNewPw, { confirmNewPw = it }, label = { Text("确认新密码") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(pwCode, { pwCode = it }, label = { Text("手机验证码") },
                            singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    val cr = Api.service.getCaptcha()
                                    if (cr.ok && cr.data != null) {
                                        captchaDialogImg = cr.data!!.imgurl
                                        captchaDialogKey = cr.data!!.md5key
                                        captchaDialogInput = ""
                                        captchaDialogTarget = "pw"
                                        captchaDialogPhone = user?.phone ?: ""
                                        showCaptchaDialog2 = true
                                    } else appVm.showToast(cr.msg ?: "获取图形验证码失败")
                                } catch (_: Exception) { appVm.showToast("网络错误") }
                            }
                        }, enabled = codeCountdown == 0
                        ) { Text(if (codeCountdown > 0) "${codeCountdown}s" else "获取验证码", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    if (newPw != confirmNewPw) { appVm.showToast("两次输入的密码不一致"); return@TextButton }
                    scope.launch {
                        try {
                            val r = Api.service.changePassword(mapOf(
                                "old_password" to oldPw, "new_password" to newPw, "code" to pwCode))
                            if (r.ok) { appVm.showToast("密码已修改, 请重新登录"); showPwDialog = false; logout() }
                            else appVm.showToast(r.msg ?: "修改失败")
                        } catch (_: Exception) { appVm.showToast("网络错误") }
                    }
                }) { Text("确认") }
            },
            dismissButton = { TextButton({ showPwDialog = false }) { Text("取消") } }
        )
    }

    // 退出登录确认
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton({
                    showLogoutDialog = false
                    logout()
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ showLogoutDialog = false }) { Text("取消") } }
        )
    }

    // 注销账号确认(需验证密码)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("注销账号", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("当前账号：${user?.blueId ?: "-"}\n注销后所有数据将在7天后清除,\n期间可联系管理员恢复。",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(deletePassword, { deletePassword = it },
                        label = { Text("密码") }, singleLine = true,
                        visualTransformation = if (showPw2) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = { IconButton({ showPw2 = !showPw2 }) {
                            Icon(if (showPw2) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility, null) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton({
                    if (deletePassword.isBlank()) {
                        appVm.showToast("请输入密码"); return@TextButton
                    }
                    deleting = true
                    scope.launch {
                        try {
                            val r = Api.service.deleteMe(
                                mapOf("account" to (user?.blueId ?: ""), "password" to deletePassword)
                            )
                            deleting = false
                            if (r.ok) {
                                appVm.showToast("账号已标记注销, 7天后自动清除")
                                Session.clear(context)
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } else appVm.showToast(r.msg ?: "注销失败")
                        } catch (_: Exception) { deleting = false; appVm.showToast("网络错误") }
                    }
                }, enabled = !deleting) {
                    if (deleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("确认注销", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton({ showDeleteDialog = false; deletePassword = "" }) {
                    Text("取消")
                }
            }
        )
    }

    // 黑名单
    if (showBlockList) {
        ModalBottomSheet(onDismissRequest = { showBlockList = false }) {
            Text("黑名单", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(blockedUsers, key = { it.id }) { bu ->
                    UserRow(bu, onClick = {}) {
                        TextButton({
                            scope.launch {
                                try {
                                    Api.service.block(mapOf("user_id" to bu.id))
                                    blockedUsers = blockedUsers.filter { it.id != bu.id }
                                    appVm.showToast("已移出黑名单")
                                } catch (_: Exception) { appVm.showToast("网络错误") }
                            }
                        }) { Text("移除") }
                    }
                }
                if (blockedUsers.isEmpty()) item { EmptyState("黑名单为空") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 手机绑定/换号弹窗
    if (showPhoneDialog) {
        var newPhone by remember { mutableStateOf("") }
        var newCode by remember { mutableStateOf("") }
        val isBound = user?.phone != null && user?.phone != ""
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = { Text(if (isBound) "更换手机号" else "绑定手机号") },
            text = {
                Column(Modifier.imePadding()) {
                    OutlinedTextField(newPhone, { newPhone = it }, label = { Text("手机号") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newCode, { newCode = it }, label = { Text("验证码") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                if (!newPhone.matches(Regex("^1\\d{10}$"))) { appVm.showToast("请输入正确的11位手机号"); return@launch }
                                try {
                                    val cr = Api.service.getCaptcha()
                                    if (cr.ok && cr.data != null) {
                                        captchaDialogImg = cr.data!!.imgurl
                                        captchaDialogKey = cr.data!!.md5key
                                        captchaDialogInput = ""
                                        captchaDialogTarget = "bind"
                                        captchaDialogPhone = newPhone
                                        showCaptchaDialog2 = true
                                    } else appVm.showToast(cr.msg ?: "获取图形验证码失败")
                                } catch (_: Exception) { appVm.showToast("网络错误") }
                            }
                        }, enabled = codeCountdown == 0
                        ) { Text(if (codeCountdown > 0) "${codeCountdown}s" else "获取验证码", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    scope.launch {
                        try {
                            val r = if (isBound) {
                                Api.service.changePhone(mapOf("new_phone" to newPhone, "new_code" to newCode))
                            } else {
                                Api.service.verifyPhone(mapOf("phone" to newPhone, "code" to newCode))
                            }
                            if (r.ok) { appVm.showToast(if (isBound) "手机号已更换" else "手机号绑定成功"); showPhoneDialog = false; appVm.loadMe() }
                            else appVm.showToast(r.msg ?: "操作失败")
                        } catch (_: Exception) { appVm.showToast("网络错误") }
                    }
                }) { Text("确认") }
            },
            dismissButton = { TextButton({ showPhoneDialog = false }) { Text("取消") } }
        )
    }

    // 图形验证码共通弹窗
    if (showCaptchaDialog2) {
        AlertDialog(
            onDismissRequest = { showCaptchaDialog2 = false },
            title = { Text("图形验证码", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("请依次输入4个区域内实心五角星的数量", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (captchaDialogImg.isNotBlank()) {
                        GifImage(
                            url = captchaDialogImg,
                            contentDescription = "验证码",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                val cr = Api.service.getCaptcha()
                                if (cr.ok && cr.data != null) {
                                    captchaDialogImg = cr.data!!.imgurl
                                    captchaDialogKey = cr.data!!.md5key
                                    captchaDialogInput = ""
                                }
                            } catch (_: Exception) {}
                        }
                    }) { Text("换一张", fontSize = 12.sp) }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        captchaDialogInput,
                        { if (it.length <= 4 && it.all { c -> c.isDigit() }) captchaDialogInput = it },
                        label = { Text("输入4位五角星数量") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = captchaDialogInput.length == 4 && !captchaDialogLoading, onClick = {
                    captchaDialogLoading = true
                    scope.launch {
                        try {
                            val r = Api.service.sendUserCode(mapOf(
                                "phone" to captchaDialogPhone,
                                "captcha_key" to captchaDialogKey,
                                "captcha_code" to captchaDialogInput
                            ))
                            captchaDialogLoading = false
                            if (r.ok) {
                                showCaptchaDialog2 = false
                                codeCountdown = 120
                                appVm.showToast("验证码已发送")
                            } else appVm.showToast(r.msg ?: "发送失败")
                        } catch (_: Exception) { captchaDialogLoading = false; appVm.showToast("网络错误") }
                    }
                }) { if (captchaDialogLoading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp) else Text("确认") }
            },
            dismissButton = { TextButton({ showCaptchaDialog2 = false }) { Text("取消") } }
        )
    }
}
