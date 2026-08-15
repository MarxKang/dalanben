@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package org.dalanben.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.dalanben.app.data.Api
import org.dalanben.app.data.Post
import org.dalanben.app.data.ShareContent
import org.dalanben.app.data.shareSummary
import org.dalanben.app.data.Session
import org.dalanben.app.data.shareUrl
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.EmptyState
import org.dalanben.app.ui.components.LoadingMore
import org.dalanben.app.ui.components.PostCard
import org.dalanben.app.ui.components.ShareSheet

/** 网络偶发抖动时单次重试: 第一次抛异常(非取消)则再试一次, 仍失败返回 null */
private suspend fun <T> retryOnce(block: suspend () -> T): T? {
    return try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        try { block() } catch (_: Exception) { null }
    }
}

/** 可复用的帖子 Feed 列表: 分页加载 + 点赞/收藏/分享/更多操作
 *  支持返回列表时恢复滚动位置: 列表状态(posts/页码/滚动位置)按 refreshKey 缓存到 AppViewModel,
 *  离开(进详情/切 tab)时写回, 重建时恢复到原帖位置。 */
@Composable
fun PostFeedList(
    navController: NavController,
    appVm: AppViewModel,
    refreshKey: Any?,
    emptyMsg: String = "暂无内容",
    header: (LazyListScope.() -> Unit)? = null,
    stickyHeader: (LazyListScope.() -> Unit)? = null,
    footer: (LazyListScope.() -> Unit)? = null,
    fullWidthHeader: Boolean = false,
    itemHorizontalPadding: Dp = 10.dp,
    expandedCards: Boolean = false,
    loader: suspend (page: Int) -> List<Post>
) {
    val cacheKey = refreshKey?.toString() ?: "default"
    val cached = appVm.feedCache[cacheKey]

    key(refreshKey) {
        var posts by remember { mutableStateOf(cached?.posts ?: listOf<Post>()) }
        var page by remember { mutableStateOf(cached?.page ?: 1) }
        var loading by remember { mutableStateOf(false) }
        var end by remember { mutableStateOf(cached?.end ?: false) }
        var firstLoaded by remember { mutableStateOf(cached != null) }
        val scope = rememberCoroutineScope()

        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = cached?.index ?: 0,
            initialFirstVisibleItemScrollOffset = cached?.offset ?: 0
        )
        DisposableEffect(Unit) {
            onDispose {
                appVm.feedCache[cacheKey] = AppViewModel.FeedCache(
                    posts = posts, page = page, end = end,
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset
                )
            }
        }

        suspend fun loadPage() {
            if (loading || end) return
            loading = true
            try {
                val list = loader(page)
                if (list.isEmpty()) end = true
                else {
                    val existed = posts.map { it.id }.toSet()
                    posts = posts + list.filter { it.id !in existed }
                    page += 1
                    if (list.size < 5) end = true
                }
            } catch (e: CancellationException) {
                // 列表滚动/页面离开时协程被取消，不需要提示错误
                throw e
            } catch (e: Exception) {
                appVm.showToast("加载失败: ${e.message}")
            } finally {
                loading = false
                firstLoaded = true
            }
        }

        LaunchedEffect(Unit) { if (posts.isEmpty()) loadPage() }

        // 基于滚动位置触发分页，避免在 itemsIndexed 最后一个 item 里嵌套 LaunchedEffect
        // 导致 item 离开合成时协程取消并报错
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= totalItems - 3 && !loading && !end && posts.isNotEmpty()
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) loadPage()
        }

        fun update(id: Int, f: (Post) -> Post) {
            posts = posts.map { if (it.id == id) f(it) else it }
        }

        fun toggleLike(p: Post) = scope.launch {
            val r = retryOnce { Api.service.like(mapOf("post_id" to p.id)) }
            if (r == null) { appVm.showToast("网络错误"); return@launch }
            val d = r.data
            if (r.ok && d != null) update(p.id) { it.copy(liked = d.liked, likeCount = d.likeCount) }
            else appVm.showToast(r.msg ?: "操作失败")
        }

        fun toggleCollect(p: Post) = scope.launch {
            val r = retryOnce { Api.service.collect(mapOf("post_id" to p.id)) }
            if (r == null) { appVm.showToast("网络错误"); return@launch }
            val d = r.data
            if (r.ok && d != null) update(p.id) {
                it.copy(collected = d.collected, collectCount = it.collectCount + if (d.collected) 1 else -1)
            } else appVm.showToast(r.msg ?: "操作失败")
        }

        fun deletePost(p: Post) = scope.launch {
            try {
                val r = Api.service.deletePost(mapOf("post_id" to p.id))
                if (r.ok) {
                    posts = posts.filter { it.id != p.id }
                    appVm.showToast("已删除")
                } else appVm.showToast(r.msg ?: "删除失败")
            } catch (_: Exception) { appVm.showToast("网络错误") }
        }

        var shareSheetPost by remember { mutableStateOf<Post?>(null) }

        fun sharePost(p: Post) {
            shareSheetPost = p
        }

        var moreTarget by remember { mutableStateOf<Post?>(null) }
        var reportTarget by remember { mutableStateOf<Post?>(null) }
        var deleteConfirmTarget by remember { mutableStateOf<Post?>(null) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = if (fullWidthHeader) PaddingValues(vertical = 8.dp) else PaddingValues(horizontal = itemHorizontalPadding, vertical = 8.dp)
        ) {
            header?.invoke(this)
            stickyHeader?.invoke(this)
            itemsIndexed(posts, key = { _, p -> p.id }) { index, post ->
                val cardModifier = if (fullWidthHeader) Modifier.padding(horizontal = itemHorizontalPadding) else Modifier
                // 交错动画：每个帖子卡片依次淡入滑入
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(index * 60L)
                    visible = true
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ) + androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = androidx.compose.animation.core.tween(
                            300,
                            easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                        )
                    ),
                    modifier = cardModifier
                ) {
                    PostCard(
                        post = post,
                        onPostClick = { navController.navigate(Routes.postDetail(post.id)) },
                        onUserClick = { navController.navigate(Routes.profile(post.userId)) },
                        onLike = { toggleLike(post) },
                        onComment = { navController.navigate(Routes.postDetail(post.id)) },
                        onCollect = { toggleCollect(post) },
                        onShare = { sharePost(post) },
                        onMore = { moreTarget = post },
                        // 帖子流中点击封面: 屏蔽查看图片/视频, 改为进入帖子详情
                        onVideoClick = { _, _ ->
                            navController.navigate(Routes.postDetail(post.id))
                        },
                        expandedContent = expandedCards
                    )
                }
            }
            if (loading) item { LoadingMore() }
            if (firstLoaded && posts.isEmpty() && !loading) item { EmptyState(emptyMsg) }
            footer?.invoke(this)
        }

        // 更多操作弹窗
        moreTarget?.let { p ->
            AlertDialog(
                onDismissRequest = { moreTarget = null },
                title = { Text("更多操作") },
                text = {
                    Column {
                        if (p.userId == Session.user?.id) {
                            TextButton(onClick = {
                                moreTarget = null; deleteConfirmTarget = p
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = {
                            moreTarget = null; reportTarget = p
                        }, modifier = Modifier.fillMaxWidth()) { Text("举报") }
                        TextButton(onClick = {
                            moreTarget = null
                            scope.launch {
                                try {
                                    Api.service.dislike(mapOf("post_id" to p.id))
                                    posts = posts.filter { it.id != p.id }
                                    appVm.showToast("将减少此类内容推荐")
                                } catch (_: Exception) { appVm.showToast("网络错误") }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("不感兴趣") }
                        TextButton(onClick = {
                            moreTarget = null
                            scope.launch {
                                try {
                                    Api.service.blockAuthor(mapOf("user_id" to p.userId))
                                    posts = posts.filter { it.userId != p.userId }
                                    appVm.showToast("已屏蔽该作者")
                                } catch (_: Exception) { appVm.showToast("网络错误") }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("屏蔽作者") }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton({ moreTarget = null }) { Text("取消") } }
            )
        }

        reportTarget?.let { p ->
            ReportDialog(
                targetType = "post", targetId = p.id, appVm = appVm,
                onDismiss = { reportTarget = null }
            )
        }

        deleteConfirmTarget?.let { p ->
            AlertDialog(
                onDismissRequest = { deleteConfirmTarget = null },
                title = { Text("删除作品") },
                text = { Text("确定删除该作品吗? 删除后不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteConfirmTarget = null
                        deletePost(p)
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton({ deleteConfirmTarget = null }) { Text("取消") } }
            )
        }

        shareSheetPost?.let { p ->
            ShareSheet(
                payload = ShareContent(
                    shareType = "post",
                    targetId = p.id,
                    title = p.title ?: "帖子",
                    cover = p.coverUrl ?: p.cover,
                    desc = shareSummary(p.content)
                ),
                appVm = appVm,
                externalText = "${p.title ?: ""}\n${shareUrl("post", p.id)}",
                onDismiss = { shareSheetPost = null }
            )
        }
    }
}

/** 举报类型（与网页版 main.js reportTarget 完全一致） */
private val REPORT_TYPES = listOf("垃圾广告", "色情低俗", "辱骂攻击", "虚假信息", "其他")

/** 通用举报弹窗（与网页版一致：固定举报类型下拉 + 选填补充说明） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDialog(targetType: String, targetId: Int, appVm: AppViewModel, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(REPORT_TYPES.first()) }
    var desc by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("举报") },
        text = {
            Column(Modifier.imePadding()) {
                // 举报类型下拉（网页版为固定 5 项）
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("举报类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        REPORT_TYPES.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = { type = t; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // 补充说明（选填，最多 200 字）
                OutlinedTextField(
                    value = desc,
                    onValueChange = { if (it.length <= 200) desc = it },
                    label = { Text("补充说明(选填)") },
                    placeholder = { Text("请描述举报原因...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val reason = (type + " " + desc.trim()).trim()
                if (reason.isBlank()) { appVm.showToast("请填写举报原因"); return@TextButton }
                scope.launch {
                    try {
                        val r = Api.service.report(mapOf(
                            "target_type" to targetType, "target_id" to targetId, "reason" to reason
                        ))
                        appVm.showToast(if (r.ok) "举报已提交" else (r.msg ?: "提交失败"))
                    } catch (_: Exception) { appVm.showToast("网络错误") }
                    onDismiss()
                }
            }) { Text("提交") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}
