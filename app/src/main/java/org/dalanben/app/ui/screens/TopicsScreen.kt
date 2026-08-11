package org.dalanben.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.Topic
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.EmptyState
import org.dalanben.app.ui.components.LoadingMore
import org.dalanben.app.ui.components.PullRefreshWrapper
import org.dalanben.app.ui.components.TopBar
import org.dalanben.app.util.formatCount
import org.dalanben.app.util.formatHeat
import org.dalanben.app.util.fullUrl

@Composable
fun TopicsScreen(navController: NavController, appVm: AppViewModel) {
    var topics by remember { mutableStateOf(listOf<Topic>()) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var kw by remember { mutableStateOf("") }
    var searchList by remember { mutableStateOf<List<Topic>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun loadPage() {
        if (loading || end) return
        loading = true
        try {
            val r = Api.service.topicList(page, 20)
            val list = r.data?.list ?: emptyList()
            if (list.isEmpty()) end = true else {
                val existed = topics.map { it.id }.toSet()
                topics = topics + list.filter { it.id !in existed }
                page += 1
            }
        } catch (e: Exception) { appVm.showToast("加载失败") }
        loading = false
    }

    fun doSearch(word: String = kw) {
        val w = word.trim()
        if (w.isEmpty()) { searchList = null; return }
        searching = true
        scope.launch {
            try {
                val r = Api.service.topicSearch(w)
                searchList = r.data?.list ?: emptyList()
            } catch (_: Exception) { searchList = emptyList() }
            searching = false
        }
    }

    LaunchedEffect(Unit) { loadPage() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                val total = listState.layoutInfo.totalItemsCount
                if (lastIndex != null && lastIndex >= total - 1 && total > 0 &&
                    !loading && !end && searchList == null
                ) {
                    loadPage()
                }
            }
    }
    LaunchedEffect(kw) {
        if (kw.isBlank()) { searchList = null; return@LaunchedEffect }
        delay(300)
        doSearch()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("话题广场", actions = {
            IconButton({ showCreate = true }) { Icon(Icons.Filled.Add, "创建话题") }
        })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                kw, { kw = it },
                placeholder = { Text("搜索话题") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                trailingIcon = { IconButton({ doSearch() }) { Icon(Icons.Filled.Search, "搜索") } }
            )
        }
        PullRefreshWrapper(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    try {
                        if (searchList != null) doSearch()
                        else { topics = emptyList(); page = 1; end = false; loading = false; loadPage() }
                    } finally {
                        refreshing = false
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val list = searchList ?: topics
            itemsIndexed(list, key = { _, t -> t.id }) { idx, t ->
                Card(
                    Modifier.fillMaxWidth().clickable { navController.navigate(Routes.topicDetail(t.id)) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!t.coverUrl.isNullOrBlank()) {
                            AsyncImage(fullUrl(t.coverUrl), null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop)
                        } else {
                            Surface(Modifier.size(48.dp), shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Tag, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("#${t.name}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            if (!t.description.isNullOrBlank()) {
                                Text(t.description!!, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${formatCount(t.postCount)} 帖子", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("热度 ${formatHeat(t.hotScore)}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (searchList != null) {
                if (searching) item { LoadingMore() }
                if (searchList!!.isEmpty() && !searching) item { EmptyState("未找到相关话题") }
            } else {
                if (loading) item { LoadingMore() }
                if (topics.isEmpty() && !loading) item { EmptyState("暂无话题") }
            }
        }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("创建话题") },
            text = {
                Column(Modifier.imePadding()) {
                    OutlinedTextField(name, { name = it }, label = { Text("话题名称") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(desc, { desc = it }, label = { Text("话题描述(选填)") },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton({
                    if (name.isBlank()) { appVm.showToast("请输入话题名称"); return@TextButton }
                    scope.launch {
                        try {
                            val r = Api.service.topicCreate(mapOf("name" to name, "description" to desc))
                            if (r.ok) {
                                appVm.showToast("创建成功")
                                showCreate = false
                                topics = emptyList(); page = 1; end = false; loadPage()
                            } else appVm.showToast(r.msg ?: "创建失败")
                        } catch (_: Exception) { appVm.showToast("网络错误") }
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton({ showCreate = false }) { Text("取消") } }
        )
    }
}
