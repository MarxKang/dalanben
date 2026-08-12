package org.dalanben.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.dalanben.app.data.AdminItem
import org.dalanben.app.data.Api
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.components.TopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 安卓管理员客户端 - 管理员中心(最高管理员可管理下属管理员) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCenterScreen(navController: NavController, appVm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val me by appVm.user.collectAsState()
    var admins by remember { mutableStateOf<List<AdminItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val isSuper = me?.role == "super_admin"
    // 创建表单
    var cAccount by remember { mutableStateOf("") }
    var cPwd by remember { mutableStateOf("") }
    var cNick by remember { mutableStateOf("") }

    fun load() {
        loading = true
        scope.launch {
            try {
                val r = Api.service.adminList()
                loading = false
                if (r.ok) admins = r.data?.list ?: emptyList()
                else appVm.showToast(r.msg ?: "加载失败")
            } catch (e: Exception) { loading = false; appVm.showToast("网络错误: ${e.message}") }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        TopBar("管理员中心", onBack = { navController.popBackStack() }, actions = {
            if (isSuper) {
                IconButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, "创建管理员") }
            }
        })

        // 当前账号卡
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(me?.nickname ?: "-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("蓝本号 ${me?.blueId ?: "-"}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isSuper) Color(0xFFB8860B) else MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        if (isSuper) "最高管理员" else "管理员",
                        fontSize = 11.sp, color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (!isSuper) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("仅最高管理员可查看和管理下属管理员", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("管理员列表", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { load() }, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                else Text("刷新", fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            items(admins, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.nickname.ifBlank { a.blueId }, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = if (a.role == "super_admin") Color(0xFFB8860B)
                                            else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        if (a.role == "super_admin") "最高管理员" else "管理员",
                                        fontSize = 10.sp,
                                        color = if (a.role == "super_admin") Color.White
                                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text("蓝本号 ${a.blueId}" +
                                    (if (a.phone.isNotBlank()) " · ${a.phone}" else "") +
                                    " · ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(a.createdAt * 1000))}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (a.role != "super_admin" && a.id != me?.id) {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        val r = Api.service.adminDemote(mapOf("user_id" to a.id))
                                        if (r.ok) { appVm.showToast("已降级"); load() }
                                        else appVm.showToast(r.msg ?: "操作失败")
                                    } catch (_: Exception) { appVm.showToast("网络错误") }
                                }
                            }) { Text("降级", fontSize = 12.sp, color = Color(0xFFEF4444)) }
                        }
                    }
                }
            }
            if (admins.isEmpty() && !loading) {
                item { Text("暂无管理员", fontSize = 13.sp, modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    // 创建管理员弹窗
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("创建管理员", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.imePadding()) {
                    OutlinedTextField(cAccount, { cAccount = it }, label = { Text("账号(3-20位字母/数字/下划线)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(cPwd, { cPwd = it }, label = { Text("密码(6-32位)") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(cNick, { cNick = it }, label = { Text("昵称(选填)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !creating, onClick = {
                    if (cAccount.isBlank() || cPwd.isBlank()) { appVm.showToast("请填写账号和密码"); return@TextButton }
                    creating = true
                    scope.launch {
                        try {
                            val r = Api.service.adminCreate(mapOf(
                                "account" to cAccount.trim(), "password" to cPwd, "nickname" to cNick.trim()))
                            creating = false
                            if (r.ok) {
                                appVm.showToast("管理员创建成功")
                                showCreate = false
                                cAccount = ""; cPwd = ""; cNick = ""
                                load()
                            } else appVm.showToast(r.msg ?: "创建失败")
                        } catch (e: Exception) { creating = false; appVm.showToast("网络错误: ${e.message}") }
                    }
                }) { if (creating) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp) else Text("创建") }
            },
            dismissButton = { TextButton({ showCreate = false }) { Text("取消") } }
        )
    }
}
