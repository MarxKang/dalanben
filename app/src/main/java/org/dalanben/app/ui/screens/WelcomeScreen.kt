package org.dalanben.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.dalanben.app.data.Api
import org.dalanben.app.data.Session
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.util.fullUrl

private val WcBlueTop = Color(0xFF4A72FF)
private val WcBlueMid = Color(0xFF3A63F3)
private val WcBlueDeep = Color(0xFF2440A8)

/**
 * 注册成功欢迎仪式：第 xx 位社区合伙人。
 * 仪式感来自三处：编号滚动、光晕呼吸、卡片弹入。文案走「兄弟搭台子」的社区调性。
 */
@Composable
fun WelcomeScreen(navController: NavController, appVm: AppViewModel) {
    val context = LocalContext.current
    val user = Session.user
    var shNo by remember { mutableStateOf(user?.shareholderNo ?: 0) }
    var total by remember { mutableStateOf(user?.totalPartners ?: 0) }
    var inviterName by remember { mutableStateOf(user?.inviterName) }
    var inviterAvatar by remember { mutableStateOf(user?.inviterAvatar) }
    var loadedInviter by remember { mutableStateOf(false) }
    val inviteCode = user?.inviteCode ?: ""
    val inviteLink = "https://dalanben.org/register?ic=$inviteCode"

    // 兜底：本地会话没带编号时，向服务端补一次（避免显示「第 0 位」）
    LaunchedEffect(Unit) {
        if ((shNo <= 0 || total <= 0) || !loadedInviter) {
            try {
                val me = Api.service.me()
                me.data?.let {
                    if (it.shareholderNo > 0) shNo = it.shareholderNo
                    if (it.totalPartners > 0) total = it.totalPartners
                    if (!it.inviterName.isNullOrBlank()) {
                        inviterName = it.inviterName
                        inviterAvatar = it.inviterAvatar
                    }
                    loadedInviter = true
                }
            } catch (_: Exception) {}
        }
    }

    // 卡片入场
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); entered = true }
    val cardScale by animateFloatAsState(
        if (entered) 1f else 0.93f,
        animationSpec = tween(520, easing = FastOutSlowInEasing), label = "wcScale"
    )
    val cardAlpha by animateFloatAsState(
        if (entered) 1f else 0f,
        animationSpec = tween(420), label = "wcAlpha"
    )

    // 编号滚动（三次方缓出）
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(shNo) {
        if (shNo <= 0) { shown = 0; return@LaunchedEffect }
        val dur = 1100L
        val t0 = System.currentTimeMillis()
        while (true) {
            val p = ((System.currentTimeMillis() - t0).toFloat() / dur).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            shown = (shNo * eased).toInt()
            if (p >= 1f) break
            delay(16)
        }
        shown = shNo
    }

    // 光晕呼吸
    val inf = rememberInfiniteTransition(label = "wcGlow")
    val glow by inf.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "wcGlowV"
    )

    val before = (total - 1).coerceAtLeast(0)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(cardScale)
                .alpha(cardAlpha),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            // ── 头部：编号牌 ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(WcBlueTop, WcBlueMid, WcBlueDeep)))
            ) {
                // 顶部光晕
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-70).dp)
                        .size(200.dp)
                        .alpha(glow * 0.35f)
                        .background(
                            Brush.radialGradient(listOf(Color.White, Color.Transparent)),
                            shape = RoundedCornerShape(100.dp)
                        )
                )
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("大蓝本 · 社区合伙人", fontSize = 12.sp, color = Color.White)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("N O .", fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
                    Text(
                        shown.toString().padStart(4, '0'),
                        fontSize = 52.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, lineHeight = 56.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (shNo > 0) "你是第 $shNo 位社区合伙人" else "欢迎加入大蓝本",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (before > 0)
                            "你来的时候，这儿刚满 $before 个人\n这个号跟你一辈子，后来的只能排你后面"
                        else "这个号跟你一辈子，后来的只能排你后面",
                        fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center, lineHeight = 19.sp
                    )
                }
            }

            // ── 邀请人信息 ──
            if (!inviterName.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = fullUrl(inviterAvatar),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "你是由 $inviterName 邀请加入的",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "他在名单里永远是你上一位",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ── 权益 ──
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                PartnerPerk("1", "这串号只属于你", "编号跟人走，注销了也不会再发给别人")
                PartnerPerk("2", "名册上的第 ${if (shNo > 0) shNo else 1} 行", "哪天社区做起来了，那一行写的是你的名字")
                PartnerPerk("3", "喊个兄弟来，+200 积分", "你带进来的人越多，在这儿说话越算数")

                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "大蓝本不是谁的公司，是一帮人一起搭的台子。\n你多说一句真话，台子就稳一分；\n多带一个兄弟，台子就宽一尺。\n往后的日子，一起扛。",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("invite", inviteLink))
                        appVm.showToast("邀请链接已复制，发给兄弟就行")
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("喊个兄弟一起来", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("先自己进去逛逛", fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PartnerPerk(idx: String, title: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(idx, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
