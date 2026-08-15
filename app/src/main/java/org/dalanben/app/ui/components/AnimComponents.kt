package org.dalanben.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dalanben.app.util.formatCount

/**
 * 带动画效果的点赞按钮
 */
@Composable
fun AnimatedLikeButton(
    isLiked: Boolean,
    likeCount: Int,
    onClick: () -> Unit
) {
    var animating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 缩放动画
    val scale by animateFloatAsState(
        targetValue = if (animating) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "likeScale",
        finishedListener = {
            animating = false
        }
    )

    // 旋转动画
    val rotation by animateFloatAsState(
        targetValue = if (animating) 15f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "likeRotation"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            if (!animating) {
                animating = true
                onClick()
                scope.launch {
                    delay(300)
                    animating = false
                }
            }
        }
    ) {
        Icon(
            if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            null,
            tint = if (isLiked) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(18.dp)
                .scale(scale)
                .graphicsLayer { rotationZ = rotation }
        )
        Spacer(Modifier.width(4.dp))
        Text(
            formatCount(likeCount),
            fontSize = 13.sp,
            color = if (isLiked) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 带动画效果的收藏按钮
 */
@Composable
fun AnimatedCollectButton(
    isCollected: Boolean,
    collectCount: Int,
    onClick: () -> Unit
) {
    var animating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = if (animating) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "collectScale",
        finishedListener = {
            animating = false
        }
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            if (!animating) {
                animating = true
                onClick()
                scope.launch {
                    delay(300)
                    animating = false
                }
            }
        }
    ) {
        Icon(
            if (isCollected) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            null,
            tint = if (isCollected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(18.dp)
                .scale(scale)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            formatCount(collectCount),
            fontSize = 13.sp,
            color = if (isCollected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 带脉冲效果的按钮
 */
@Composable
fun PulseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier.scale(pulseScale),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text)
    }
}

/**
 * 渐显数字 - 数字变化时有滚动效果
 */
@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    var currentCount by remember { mutableIntStateOf(count) }
    val animatedCount by animateIntAsState(
        targetValue = count,
        animationSpec = tween(300),
        label = "counter"
    )

    LaunchedEffect(count) {
        currentCount = count
    }

    Text(
        text = formatCount(animatedCount),
        modifier = modifier,
        color = color
    )
}

/**
 * 闪烁加载占位符
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha),
                shape = MaterialTheme.shapes.small
            )
    )
}

/**
 * 旋转加载图标
 */
@Composable
fun SpinningIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinRotation"
    )

    Icon(
        icon,
        null,
        tint = tint,
        modifier = modifier.graphicsLayer { rotationZ = rotation }
    )
}

/**
 * 弹出提示 - 从底部弹出然后消失
 */
@Composable
fun PopupToast(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut()
    ) {
        LaunchedEffect(Unit) {
            delay(2000)
            onDismiss()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.clip(MaterialTheme.shapes.medium)
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}
