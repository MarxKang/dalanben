package org.dalanben.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** 宝贝回家公益卡片 */
@Composable
fun MissingChildCard(modifier: Modifier = Modifier) {
    var child by remember { mutableStateOf<JSONObject?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val body = withContext(Dispatchers.IO) {
                val client = org.dalanben.app.data.Api.okHttpClient
                val req = Request.Builder().url("https://openapi.dwo.cc/api/babygome?type=json")
                    .header("Accept", "application/json").build()
                client.newCall(req).execute().body?.string() ?: ""
            }
            child = JSONObject(body).optJSONObject("data")
        } catch (_: Exception) {}
    }

    child?.let { c ->
        val name = c.optString("name", "")
        val sex = c.optString("sex", "")
        val birthDay = c.optString("birthDay", "")
        val lostDay = c.optString("lostDay", "")
        val lostAddress = c.optString("lostAddress", "")
        val feature = c.optString("feature", "")
        val photoUrl = c.optString("photoUrl", "")
        val detailUrl = c.optString("detailUrl", "")
        val categoryName = c.optString("categoryName", "")

        Card(
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E7)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDD0D 宝贝回家 · 公益寻人",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Spacer(Modifier.weight(1f))
                    Text(categoryName, fontSize = 10.sp, color = Color(0xFFBF360C))
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth()) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(model = photoUrl, contentDescription = null,
                            modifier = Modifier.size(width = 72.dp, height = 96.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.LightGray),
                            contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text(sex, fontSize = 13.sp, color = Color.Gray)
                        }
                        Spacer(Modifier.height(2.dp))
                        if (birthDay.isNotBlank()) Text("出生: $birthDay", fontSize = 11.sp, color = Color.Gray)
                        if (lostDay.isNotBlank()) Text("失踪: $lostDay", fontSize = 11.sp, color = Color.Gray)
                        if (lostAddress.isNotBlank()) Text("地点: $lostAddress", fontSize = 11.sp, color = Color.Gray)
                        if (feature.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            val text = if (!expanded && feature.length > 30) feature.take(30) + "..." else feature
                            Text(text, fontSize = 11.sp, color = Color(0xFF795548), lineHeight = 15.sp)
                            if (feature.length > 30) {
                                TextButton(onClick = { expanded = !expanded },
                                    contentPadding = PaddingValues(0.dp), modifier = Modifier.height(28.dp)) {
                                    Text(if (expanded) "收起" else "展开", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                val ctx = LocalContext.current
                TextButton(
                    onClick = { if (detailUrl.isNotBlank()) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(detailUrl))) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE65100))
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("查看详情 · 助力团圆", fontSize = 12.sp)
                }
            }
        }
    }
}
