package org.dalanben.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.dalanben.app.data.*
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.components.ShareSheet
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.util.formatCount
import org.dalanben.app.util.formatHeat

@Composable
fun TopicDetailScreen(navController: NavController, appVm: AppViewModel, topicId: Int) {
    var topic by remember { mutableStateOf<Topic?>(null) }
    var shareSheetTopic by remember { mutableStateOf<Topic?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar(topic?.name?.let { "#$it" } ?: "话题", onBack = { navController.popBackStack() },
            actions = {
                topic?.let { t ->
                    IconButton({ shareSheetTopic = t }) {
                        Icon(Icons.Filled.Share, "分享", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            })

        topic?.let { t ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("#${t.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                if (!t.description.isNullOrBlank()) {
                    Text(t.description!!, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${formatCount(t.postCount)} 帖子 · 热度 ${formatHeat(t.hotScore)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }

        Box(Modifier.weight(1f)) {
            PostFeedList(navController, appVm, refreshKey = "topic-$topicId",
                emptyMsg = "该话题下暂无帖子") { page ->
                val r = Api.service.topicDetail(topicId, page, 12)
                if (r.ok) {
                    if (page == 1) topic = r.data?.topic
                    r.data?.list ?: emptyList()
                } else throw IllegalStateException(r.msg ?: "加载失败")
            }
        }
    }

    shareSheetTopic?.let { t ->
        ShareSheet(
            payload = ShareContent(
                shareType = "topic",
                targetId = t.id,
                title = "#${t.name}",
                cover = t.coverUrl,
                desc = shareSummary(t.description)
            ),
            appVm = appVm,
            externalText = "#${t.name}\n${shareUrl("topic", t.id)}",
            onDismiss = { shareSheetTopic = null }
        )
    }
}
