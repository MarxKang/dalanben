package org.dalanben.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import org.dalanben.app.R
import org.dalanben.app.data.AppTheme

// 品牌色
val BrandBlue = Color(0xFF3A63F3)
val BrandBlueDark = Color(0xFF2F50D8)
val BrandBlueSoft = Color(0xFFEEF3FE)

/**
 * 全局字体: 思源黑体(Noto Sans SC, SIL OFL 开源协议)
 * 观感最接近苹方(PingFang SC); 苹方为苹果专有版权字体不可随 APK 分发, 故采用此开源替代
 */
val AppFontFamily = FontFamily(
    Font(R.font.noto_sans_sc_regular, FontWeight.Normal),
    Font(R.font.noto_sans_sc_bold, FontWeight.Bold)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = AppFontFamily, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = AppFontFamily, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = AppFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = AppFontFamily, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp)
)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandBlueDark,
    background = Color(0xFFF4F5F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F1F6),
    onBackground = Color(0xFF15181F),
    onSurface = Color(0xFF15181F),
    onSurfaceVariant = Color(0xFF5B6472),
    outline = Color(0xFF9AA1AC),
    error = Color(0xFFEF4444)
)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandBlueDark,
    background = Color(0xFF0E1116),
    surface = Color(0xFF171B23),
    surfaceVariant = Color(0xFF202634),
    onBackground = Color(0xFFE9ECF3),
    onSurface = Color(0xFFE9ECF3),
    onSurfaceVariant = Color(0xFF9AA3B2),
    outline = Color(0xFF3A4250),
    error = Color(0xFFEF4444)
)

/** 从 Context 链中找到 Activity(用于设置系统栏) */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 解析 #RRGGBB / #AARRGGBB 为 Compose Color，失败返回 null */
private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val s = hex.trim()
    return try {
        val value = when {
            s.startsWith("#") -> s.drop(1)
            s.startsWith("0x", ignoreCase = true) -> s.drop(2)
            else -> s
        }.toLong(16)
        when (s.length) {
            7 -> Color(value or 0xFF000000) // #RRGGBB
            9 -> Color(value) // #AARRGGBB
            6 -> Color(value or 0xFF000000)
            8 -> Color(value)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/** 将远程主题颜色覆盖到基础配色方案 */
private fun ColorScheme.withRemoteTheme(theme: AppTheme?): ColorScheme {
    if (theme == null) return this
    return this.copy(
        primary = parseHexColor(theme.primaryColor) ?: primary,
        secondary = parseHexColor(theme.secondaryColor) ?: secondary,
        background = parseHexColor(theme.backgroundColor) ?: background,
        surface = parseHexColor(theme.surfaceColor) ?: surface
    )
}

/**
 * iOS 26 风格全局形状: 大圆角让按钮/输入框/卡片呈现胶囊与椭圆造型
 * - extraSmall/small: 输入框(OutlinedTextField)与小型组件 → 椭圆
 * - medium/large/extraLarge: 卡片/弹窗 → 大圆角玻璃感
 */
private val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

@Composable
fun DalanbenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    remoteTheme: AppTheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = (if (darkTheme) DarkColors else LightColors).withRemoteTheme(remoteTheme)

    // 让状态栏 / 导航栏颜色与明暗主题保持一致
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            val window = activity?.window
            if (window != null) {
                val wic = WindowInsetsControllerCompat(window, view)
                wic.isAppearanceLightStatusBars = !darkTheme
                wic.isAppearanceLightNavigationBars = !darkTheme
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
