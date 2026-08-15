package org.dalanben.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dalanben.app.util.formatCount

/**
 * 骨架屏加载组件 - 闪烁占位效果
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
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
                shape = shape
            )
    )
}

/**
 * 帖子卡片骨架屏
 */
@Composable
fun PostCardShimmer(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(40.dp), CircleShape)
                Spacer(Modifier.width(8.dp))
                Column {
                    ShimmerBox(Modifier.width(120.dp).height(14.dp))
                    Spacer(Modifier.height(4.dp))
                    ShimmerBox(Modifier.width(80.dp).height(10.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(16.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
            Spacer(Modifier.height(12.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(180.dp), RoundedCornerShape(8.dp))
            Spacer(Modifier.height(12.dp))
            Row {
                ShimmerBox(Modifier.width(60.dp).height(24.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(16.dp))
                ShimmerBox(Modifier.width(60.dp).height(24.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(16.dp))
                ShimmerBox(Modifier.width(60.dp).height(24.dp), RoundedCornerShape(12.dp))
            }
        }
    }
}

/**
 * 按压缩放动画包装器
 */
@Composable
fun PressScaleBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 弹性按钮 - 点击有弹性效果
 */
@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    var isAnimating by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isAnimating) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bouncy",
        finishedListener = { isAnimating = false }
    )

    Button(
        onClick = {
            isAnimating = true
            onClick()
        },
        modifier = modifier.scale(scale),
        enabled = enabled,
        content = content
    )
}

/**
 * 渐显数字 - 数字变化时有滚动效果
 */
@Composable
fun AnimatedNumber(
    number: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: Int = 14
) {
    val animatedNumber by animateIntAsState(
        targetValue = number,
        animationSpec = tween(300),
        label = "number"
    )
    Text(
        text = formatCount(animatedNumber),
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp
    )
}

/**
 * 脉冲呼吸灯效果
 */
@Composable
fun PulseIndicator(
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

/**
 * 渐入滑动动画包装器
 */
@Composable
fun SlideInItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 80L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(300, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                ),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * 旋转加载图标
 */
@Composable
fun SpinningLoading(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinRotation"
    )

    CircularProgressIndicator(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation },
        color = color,
        strokeWidth = 2.dp
    )
}

/**
 * 弹出提示动画
 */
@Composable
fun AnimatedToast(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
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
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 8.dp
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 渐变背景动画
 */
@Composable
fun AnimatedGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffset"
    )

    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ),
                start = androidx.compose.ui.geometry.Offset(offset * 1000, 0f),
                end = androidx.compose.ui.geometry.Offset(offset * 1000 + 1000, 1000f)
            )
        )
    ) {
        content()
    }
}

/**
 * 心形点赞爆炸效果
 */
@Composable
fun LikeExplosion(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible) {
        val scope = rememberCoroutineScope()
        val particles = remember { List(8) { Animatable(0f) } }
        val alphas = remember { List(8) { Animatable(1f) } }
        val scales = remember { List(8) { Animatable(0f) } }

        LaunchedEffect(Unit) {
            particles.forEachIndexed { index, animatable ->
                scope.launch {
                    delay(index * 50L)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(500, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                    )
                }
                scope.launch {
                    delay(index * 50L)
                    alphas[index].animateTo(
                        targetValue = 0f,
                        animationSpec = tween(500)
                    )
                }
                scope.launch {
                    delay(index * 50L)
                    scales[index].animateTo(
                        targetValue = 1f,
                        animationSpec = tween(300, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f))
                    )
                }
            }
        }

        Box(modifier = modifier.size(100.dp)) {
            particles.forEachIndexed { index, animatable ->
                val angle = index * 45f
                val radians = Math.toRadians(angle.toDouble())
                val distance = animatable.value * 40
                val x = distance * Math.cos(radians)
                val y = distance * Math.sin(radians)

                Icon(
                    Icons.Filled.Favorite,
                    null,
                    tint = Color(0xFFEF4444).copy(alpha = alphas[index].value),
                    modifier = Modifier
                        .size(16.dp)
                        .offset(
                            x = (50 + x).toFloat().dp - 8.dp,
                            y = (50 + y).toFloat().dp - 8.dp
                        )
                        .scale(scales[index].value)
                )
            }
        }
    }
}
