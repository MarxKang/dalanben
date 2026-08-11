package org.dalanben.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import org.dalanben.app.R
import org.dalanben.app.data.Api
import org.dalanben.app.data.Banner
import org.dalanben.app.data.FestiveEntry
import org.dalanben.app.data.RemoteConfigManager
import org.dalanben.app.ui.AppViewModel
import org.dalanben.app.ui.Routes
import org.dalanben.app.ui.components.PullRefreshWrapper
import org.dalanben.app.ui.handleRemoteAction
import org.dalanben.app.util.parseHexColor

@Composable
fun HomeScreen(navController: NavController, appVm: AppViewModel) {
    val tabs = listOf("推荐" to "recommend", "精选" to "featured", "最新" to "latest")
    val tabIndex by appVm.homeTabIndex.collectAsState()
    var refreshTick by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshTick) { if (refreshing) refreshing = false }

    val context = LocalContext.current
    val configVersion by appVm.configVersion.collectAsState()
    val singleCol by appVm.singleColumnMode.collectAsState()
    val festiveEntries = remember(configVersion) { RemoteConfigManager.getFestiveEntries(context, "home_top") }
    val banners = remember(configVersion) { RemoteConfigManager.getBanners(context, "home_banner") }

    Column(Modifier.fillMaxSize()) {
        // 顶栏: logo + 搜索
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_brand_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(6.dp))
            Text("大蓝本", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(2f).height(36.dp)
                    .clickable { navController.navigate(Routes.SEARCH) }
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("搜索用户 / 帖子 / 话题", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            // 单列模式切换按钮
            IconButton(onClick = { appVm.toggleSingleColumn() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.ViewAgenda, "单列模式",
                    tint = if (singleCol) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 单列刷视频模式
        if (singleCol) {
            BackHandler(onBack = { appVm.exitSingleColumn() })
            val channel = tabs[tabIndex].second
            SingleColumnPager(
                navController = navController,
                appVm = appVm,
                channel = channel,
                onBack = { appVm.exitSingleColumn() }
            )
            return
        }

        // 节日入口 / 快捷入口（服务端可热更）
        if (festiveEntries.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                festiveEntries.forEach { entry ->
                    FestiveEntryCard(entry) {
                        handleRemoteAction(navController, entry.actionType, entry.actionTarget, entry.title)
                    }
                }
            }
        }

        // Banner 轮播（服务端可热更）
        if (banners.isNotEmpty()) {
            BannerCarousel(banners, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { banner ->
                handleRemoteAction(navController, banner.actionType, banner.actionTarget, banner.title)
            }
        }

        // 频道 Tab
        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { i, (label, _) ->
                Tab(
                    selected = tabIndex == i,
                    onClick = { if (tabIndex == i) refreshTick++ else appVm.setHomeTabIndex(i) },
                    text = { Text(label) }
                )
            }
        }
        PullRefreshWrapper(isRefreshing = refreshing, onRefresh = { refreshing = true; refreshTick++ }, modifier = Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize()) {
            val channel = tabs[tabIndex].second
            PostFeedList(
                navController = navController,
                appVm = appVm,
                refreshKey = "$channel-$refreshTick",
                emptyMsg = "暂无内容, 点击 Tab 可刷新"
            ) { page ->
                val resp = when (channel) {
                    "featured" -> Api.service.featured(page, 12)
                    "latest" -> Api.service.latest(page, 12)
                    else -> Api.service.recommend(page, 12)
                }
                if (resp.ok) resp.data?.list ?: emptyList() else throw IllegalStateException(resp.msg ?: "加载失败")
            }
            } // inner Box
        }
    }
}

@Composable
private fun FestiveEntryCard(entry: FestiveEntry, onClick: () -> Unit) {
    val bgColor = parseHexColor(entry.bgColor) ?: MaterialTheme.colorScheme.primaryContainer
    val textColor = parseHexColor(entry.textColor) ?: MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.iconUrl,
            contentDescription = entry.title,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(10.dp))
        Text(
            entry.title,
            fontSize = 14.sp,
            color = textColor,
            maxLines = 1,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BannerCarousel(banners: List<Banner>, modifier: Modifier = Modifier, onClick: (Banner) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { banners.size })
    Box(modifier = modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp))) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val banner = banners[page]
            AsyncImage(
                model = banner.imageUrl,
                contentDescription = banner.title,
                modifier = Modifier.fillMaxSize().clickable { onClick(banner) },
                contentScale = ContentScale.Crop
            )
        }
        if (banners.size > 1) {
            // 简单的页码指示器
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                banners.forEachIndexed { index, _ ->
                    val active = pagerState.currentPage == index
                    Box(
                        Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}
