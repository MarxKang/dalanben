package org.dalanben.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dalanben.app.ui.components.GifImage
import org.dalanben.app.R
import org.dalanben.app.data.Api
import org.dalanben.app.data.LoginData
import org.dalanben.app.data.Session
import org.dalanben.app.data.User
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes

@Composable
private fun BrandHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_brand_logo),
            contentDescription = "大蓝本",
            modifier = Modifier.size(84.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(8.dp))
        Text("大蓝本", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("散帅男性成长社区", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 发送验证码按钮（图形验证码→发送短信，120秒冷却） */
@Composable
private fun CaptchaSmsButton(phone: String, appVm: AppViewModel, onCodeSent: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var countdown by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    // 图形验证码流程
    var showCaptcha by remember { mutableStateOf(false) }
    var captchaImg by remember { mutableStateOf("") }
    var captchaKey by remember { mutableStateOf("") }
    var captchaInput by remember { mutableStateOf("") }
    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(1000); countdown-- }
    }

    TextButton(
        onClick = {
            if (countdown > 0) return@TextButton
            if (phone.length < 7) { appVm.showToast("请输入正确的手机号"); return@TextButton }
            loading = true
            scope.launch {
                try {
                    val cr = Api.service.getCaptcha()
                    loading = false
                    if (cr.ok && cr.data != null) {
                        captchaImg = cr.data!!.imgurl
                        captchaKey = cr.data!!.md5key
                        captchaInput = ""
                        showCaptcha = true
                    } else appVm.showToast(cr.msg ?: "获取图形验证码失败")
                } catch (_: Exception) { loading = false; appVm.showToast("网络错误") }
            }
        },
        enabled = countdown == 0 && !loading
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
        else Text(if (countdown > 0) "${countdown}s" else "获取验证码",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }

    // 图形验证码弹窗
    if (showCaptcha) {
        AlertDialog(
            onDismissRequest = { showCaptcha = false },
            title = { Text("图形验证码", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("请依次输入4个区域内实心五角星的数量", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (captchaImg.isNotBlank()) {
                        GifImage(
                            url = captchaImg,
                            contentDescription = "验证码",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                val cr = Api.service.getCaptcha()
                                if (cr.ok && cr.data != null) {
                                    captchaImg = cr.data!!.imgurl
                                    captchaKey = cr.data!!.md5key
                                    captchaInput = ""
                                }
                            } catch (_: Exception) {}
                        }
                    }) { Text("换一张", fontSize = 12.sp) }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        captchaInput, { if (it.length <= 4 && it.all { c -> c.isDigit() }) captchaInput = it },
                        label = { Text("输入4位五角星数量") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = captchaInput.length == 4 && !loading, onClick = {
                    loading = true
                    scope.launch {
                        try {
                            val r = Api.service.sendAuthCode(mapOf(
                                "phone" to phone,
                                "captcha_key" to captchaKey,
                                "captcha_code" to captchaInput
                            ))
                            loading = false
                            if (r.ok) {
                                showCaptcha = false
                                countdown = 120
                                onCodeSent()
                            } else appVm.showToast(r.msg ?: "发送失败")
                        } catch (_: Exception) { loading = false; appVm.showToast("网络错误") }
                    }
                }) { if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp) else Text("确认") }
            },
            dismissButton = { TextButton({ showCaptcha = false }) { Text("取消") } }
        )
    }
}

// ───────── 登录 ─────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController, appVm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) } // 0=密码, 1=短信
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    fun doLogin(body: Map<String, Any>) {
        loading = true
        scope.launch {
            try {
                val resp = Api.service.login(body)
                loading = false
                if (resp.ok && resp.data != null) {
                    val d = resp.data!!
                    Session.set(context, d.token ?: "", User(
                        id = d.userId, nickname = d.nickname, blueId = d.blueId, role = d.role, status = d.status
                    ))
                    // 登录成功后立即上报 IP 属地 (服务端 5 小时节流; 失败静默)
                    try { Api.service.updateIpRegion() } catch (_: Exception) { }
                    appVm.setUser(Session.user ?: return@launch)
                    appVm.refreshAll()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                } else appVm.showToast(resp.msg ?: "登录失败")
            } catch (e: Exception) { loading = false; appVm.showToast("网络错误: ${e.message}") }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        BrandHeader()
        Spacer(Modifier.height(24.dp))

        // 登录方式切换
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(tab == 0, { tab = 0 }, label = { Text("密码登录") })
            Spacer(Modifier.width(8.dp))
            FilterChip(tab == 1, { tab = 1 }, label = { Text("短信登录") })
        }
        Spacer(Modifier.height(16.dp))

        if (tab == 0) {
            OutlinedTextField(account, { account = it }, label = { Text("账号/手机号") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ showPw = !showPw }) { Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Button(onClick = {
                if (account.isBlank() || password.isBlank()) { appVm.showToast("请输入账号和密码"); return@Button }
                doLogin(mapOf("account" to account, "password" to password))
            }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !loading
            ) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("登录") }
        } else {
            OutlinedTextField(phone, { phone = it }, label = { Text("手机号") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(code, { code = it }, label = { Text("验证码") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CaptchaSmsButton(phone, appVm) {}
            }
            Spacer(Modifier.height(18.dp))
            Button(onClick = {
                if (phone.isBlank() || code.isBlank()) { appVm.showToast("请输入手机号和验证码"); return@Button }
                doLogin(mapOf("account" to phone, "mode" to "sms", "code" to code))
            }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !loading
            ) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("登录") }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { navController.navigate(Routes.REGISTER) }) {
            Text("还没有账号？立即注册", color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dalanben.org")))
        }) {
            Text("先不登录，浏览网页版", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───────── 注册 ─────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController, appVm: AppViewModel, inviteCode: String? = null) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var account by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    // 来自邀请深链的邀请码(未登录状态)
    var inviteCodeState by remember { mutableStateOf(inviteCode ?: "") }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))
        BrandHeader()
        Spacer(Modifier.height(24.dp))
        // 邀请提示
        if (inviteCodeState.isNotBlank()) {
            Card(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PersonAdd, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("你正在通过邀请链接注册", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { inviteCodeState = "" },
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("不用", fontSize = 12.sp)
                    }
                }
            }
        }
        OutlinedTextField(account, { account = it }, label = { Text("账号(3-20位字母/数字/下划线)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(nickname, { nickname = it }, label = { Text("昵称(2-16字符)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("密码(6-32位)") },
            singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ showPw = !showPw }) { Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(phone, { phone = it }, label = { Text("手机号") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(code, { code = it }, label = { Text("验证码") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.width(8.dp))
            CaptchaSmsButton(phone, appVm) {}
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            val fields = listOf(account, nickname, password, phone, code)
            if (fields.any { it.isBlank() }) { appVm.showToast("请填写完整信息"); return@Button }
            loading = true
            scope.launch {
                try {
                    val body = mutableMapOf<String, Any>(
                        "account" to account, "nickname" to nickname, "password" to password,
                        "phone" to phone, "code" to code
                    )
                    if (inviteCodeState.isNotBlank()) body["invite_code"] = inviteCodeState
                    val resp = Api.service.register(body)
                    loading = false
                    if (resp.ok && resp.data != null) {
                        val d = resp.data!!
                        Session.set(context, d.token ?: "", User(
                            id = d.userId, nickname = d.nickname, blueId = d.blueId, role = d.role, status = d.status,
                            shareholderNo = d.shareholderNo, inviteCode = d.inviteCode,
                            totalPartners = d.totalPartners,
                            inviterName = d.inviterName, inviterAvatar = d.inviterAvatar
                        ))
                        // 注册成功后立即上报 IP 属地 (服务端 5 小时节流; 失败静默)
                        try { Api.service.updateIpRegion() } catch (_: Exception) { }
                        appVm.setUser(Session.user ?: return@launch)
                        appVm.refreshAll()
                        navController.navigate(Routes.WELCOME) { popUpTo(Routes.REGISTER) { inclusive = true } }
                    } else appVm.showToast(resp.msg ?: "注册失败")
                } catch (e: Exception) { loading = false; appVm.showToast("网络错误: ${e.message}") }
            }
        }, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !loading
        ) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else Text("注册") }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { navController.popBackStack() }) { Text("已有账号？去登录", color = MaterialTheme.colorScheme.primary) }
    }
}
