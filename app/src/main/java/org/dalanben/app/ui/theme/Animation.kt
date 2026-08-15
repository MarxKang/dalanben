package org.dalanben.app.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 统一动画配置 - 让整个App动画风格一致
 */
object AppAnim {
    // ========== 时间配置 ==========
    const val FAST = 150
    const val NORMAL = 300
    const val SLOW = 500
    const val EXTRA_SLOW = 800

    // ========== 弹簧动画配置 ==========
    fun <T> springSnappy() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )

    fun <T> springBouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> springGentle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    // ========== 缓动曲线 ==========
    val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EaseInOutQuart = CubicBezierEasing(0.76f, 0f, 0.24f, 1f)
    val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    // ========== 页面转场动画 ==========
    val slideInFromRight = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(NORMAL, easing = EaseOutQuint)
    ) + fadeIn(animationSpec = tween(FAST))

    val slideOutToLeft = slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(NORMAL, easing = EaseInOutQuart)
    ) + fadeOut(animationSpec = tween(FAST))

    val slideInFromLeft = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(NORMAL, easing = EaseOutQuint)
    ) + fadeIn(animationSpec = tween(FAST))

    val slideOutToRight = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(NORMAL, easing = EaseInOutQuart)
    ) + fadeOut(animationSpec = tween(FAST))

    // 底部弹出（用于详情页、弹窗）
    val slideInFromBottom = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(NORMAL, easing = EaseOutQuint)
    ) + fadeIn(animationSpec = tween(FAST))

    val slideOutToBottom = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(NORMAL, easing = EaseInOutQuart)
    ) + fadeOut(animationSpec = tween(FAST))

    // 缩放淡入（用于卡片、弹窗）
    val scaleIn = scaleIn(
        initialScale = 0.92f,
        animationSpec = tween(NORMAL, easing = EaseOutBack)
    ) + fadeIn(animationSpec = tween(FAST))

    val scaleOut = scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(NORMAL, easing = EaseInOutQuart)
    ) + fadeOut(animationSpec = tween(FAST))
}

/**
 * 渐入动画包装器 - 元素进入时有淡入+上移效果
 */
@Composable
fun FadeInSlideUp(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable BoxScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(AppAnim.NORMAL)) +
                slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(AppAnim.NORMAL, easing = AppAnim.EaseOutQuint)
                ),
        modifier = modifier
    ) {
        Box { content() }
    }
}

/**
 * 列表项交错动画 - 让列表项依次出现
 */
@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 50).toLong()) // 每项延迟50ms
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(AppAnim.NORMAL)) +
                slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(AppAnim.NORMAL, easing = AppAnim.EaseOutQuint)
                ),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * 按压缩放效果 - 点击时有缩放反馈
 */
@Composable
fun rememberPressScaleAnim(): Animatable<Float, AnimationVector1D> {
    return remember { Animatable(1f) }
}

/**
 * 脉冲动画 - 用于吸引注意力
 */
@Composable
fun pulseAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    return infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    ).value
}

/**
 * 旋转加载动画
 */
@Composable
fun spinAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    return infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    ).value
}

/**
 * 呼吸发光动画
 */
@Composable
fun breatheAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    return infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    ).value
}
