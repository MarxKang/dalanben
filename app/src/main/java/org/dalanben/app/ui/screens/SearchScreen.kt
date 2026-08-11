package org.dalanben.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.HotSearchItem
import org.dalanben.app.data.SearchResult
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.*
import org.dalanben.app.util.formatHeat
import org.dalanben.app.util.fullUrl
import org.dalanben.app.util.safeMedia

@Composable
fun SearchScreen(navController: NavController, appVm: AppViewModel) {
    var kw by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var suggestions by remember { mutableStateOf<SearchResult?>(null) }
    var showSuggest by remember { mutableStateOf(false) }
    var hot by remember { mutableStateOf(listOf<HotSearchItem>()) }
    var searching by remember { mutableStateOf(false) }
    var submittedKw by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try { hot = Api.service.hotSearch().data?.list ?: emptyList() } catch (_: Exception) {}
    }

    // 实时联想(防抖 300ms) —— 对齐网页版 onSearchInput
    LaunchedEffect(kw) {
        if (kw.isBlank()) { suggestions = null; showSuggest = false; return@LaunchedEffect }
        delay(300)
        try { suggestions = Api.service.search(kw, "all", 1, 5).data } catch (_: Exception) { suggestions = null }
        showSuggest = kw.isNotBlank() && kw != submittedKw
    }

    fun doSearch(word: String = kw) {
        val w = word.trim()
        if (w.isEmpty()) { appVm.showToast("请输入关键词"); return }
        kw = w
        submittedKw = w
        showSuggest = false
        suggestions = null
        searching = true
        scope.launch {
            try {
                val r = Api.service.search(w, "all", 1, 20)
                if (r.ok) result = r.data else appVm.showToast(r.msg ?: "搜索失败")
            } catch (_: Exception) { appVm.showToast("网络错误") }
            searching = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("搜索", onBack = { navController.popBackStack() })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                kw, { kw = it },
                placeholder = { Text("搜索用户、作品、话题、圈子...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                trailingIcon = {
                    IconButton({ doSearch() }) { Icon(Icons.Filled.Search, "搜索") }
                }
            )
        }

        when {
            showSuggest && suggestions != null -> SearchSuggestion(
                suggestions!!, navController,
                onUser = { navController.navigate(Routes.profile(it)) },
                onTopic = { navController.navigate(Routes.topicDetail(it)) },
                onPost = { navController.navigate(Routes.postDetail(it)) }
            )
            result == null -> SearchHotList(hot,
                onTopic = { doSearch(it) },
                onUser = { navController.navigate(Routes.profile(it)) }
            )
            else -> SearchResultSections(result!!, navController, searching)
        }
    }
}

// 分区标题(对齐网页版 renderSearchResult 的 h3)
@Composable
private fun SectionHeader(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// 热搜榜(对齐网页版 hot-search)
@Composable
private fun SearchHotList(
    hot: List<HotSearchItem>,
    onTopic: (String) -> Unit,
    onUser: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Filled.Whatshot, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("大蓝本热搜榜", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(hot, key = { "${it.rank}_${it.keyword}_${it.ktype}" }) { h ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable {
                        if (h.ktype == "topic") onTopic(h.keyword ?: "") else onUser(h.refId)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    h.rank.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = when (h.rank) {
                        1 -> MaterialTheme.colorScheme.error
                        2 -> androidx.compose.ui.graphics.Color(0xFFF97316)
                        3 -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
                        else -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.width(28.dp)
                )
                Text(h.keyword ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (h.hotScore > 0) {
                    Text(
                        "热度 ${formatHeat(h.hotScore)}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(if (h.ktype == "user") "用户" else "话题", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hot.isEmpty()) item { EmptyState("暂无热搜") }
    }
}

// 实时联想下拉(对齐网页版 searchSuggest)
@Composable
private fun SearchSuggestion(
    s: SearchResult,
    navController: NavController,
    onUser: (Int) -> Unit,
    onTopic: (Int) -> Unit,
    onPost: (Int) -> Unit
) {
    val users = s.users.take(3)
    val topics = s.topics.take(3)
    val posts = s.posts.take(1)
    val hasAny = users.isNotEmpty() || topics.isNotEmpty() || posts.isNotEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(users, key = { "u${it.id}" }) { u ->
            UserRow(u, onClick = { onUser(u.id) })
        }
        items(topics, key = { "t${it.id}" }) { t ->
            Row(
                Modifier.fillMaxWidth().clickable { onTopic(t.id) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.LocalOffer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("#${t.name}", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("${t.postCount} 作品", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(posts, key = { "p${it.id}" }) { p ->
            val cover = (p.mediaUrls.safeMedia().firstOrNull()) ?: p.coverUrl
            Row(
                Modifier.fillMaxWidth().clickable { onPost(p.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null) AsyncImage(fullUrl(cover), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Icon(Icons.Filled.Article, null, tint = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title ?: (p.content ?: "").take(20), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(p.nickname ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (!hasAny) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("无匹配结果", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// 分区结果(对齐网页版 renderSearchResult: 用户→作品→话题→圈子)
@Composable
private fun SearchResultSections(
    r: SearchResult,
    navController: NavController,
    searching: Boolean
) {
    if (searching) { FullScreenLoading(); return }
    val hasAny = r.users.isNotEmpty() || r.posts.isNotEmpty() || r.topics.isNotEmpty()
    if (!hasAny) { EmptyState("没有找到相关内容"); return }
    val toPost: (Int) -> Unit = { navController.navigate(Routes.postDetail(it)) }
    LazyColumn(Modifier.fillMaxSize()) {
        if (r.users.isNotEmpty()) {
            item { SectionHeader("用户", Icons.Filled.Person) }
            items(r.users, key = { "u${it.id}" }) { u ->
                UserRow(u, onClick = { navController.navigate(Routes.profile(u.id)) })
            }
        }
        if (r.posts.isNotEmpty()) {
            item { SectionHeader("作品", Icons.Filled.Article) }
            items(r.posts, key = { "p${it.id}" }) { p ->
                Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    PostCard(
                        post = p,
                        onPostClick = { toPost(p.id) },
                        onUserClick = { toPost(p.id) },
                        onLike = { toPost(p.id) },
                        onComment = { toPost(p.id) },
                        onCollect = { toPost(p.id) },
                        onShare = { toPost(p.id) },
                        onMore = { toPost(p.id) },
                        onVideoClick = { _, _ -> toPost(p.id) }
                    )
                }
            }
        }
        if (r.topics.isNotEmpty()) {
            item { SectionHeader("话题", Icons.Filled.LocalOffer) }
            items(r.topics, key = { "t${it.id}" }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { navController.navigate(Routes.topicDetail(t.id)) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#${t.name}", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text("${t.postCount} 作品", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
