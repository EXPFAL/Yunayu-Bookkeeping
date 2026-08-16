package com.expfal.yunayu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Yunayu 品牌形状：以 Material3 默认 [Shapes] 为基底，仅覆写 medium=16.dp
 * （卡片大圆角）、small=10.dp（chips/小控件协调），其余沿用默认。
 */
internal val YUNAYU_SHAPES: Shapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(10.dp),
)

/**
 * Yunayu 品牌排版：以 Material3 默认 [Typography] 为基底，仅覆写
 * headlineLarge/displayLarge/headlineMedium/titleLarge 四个金额专用 style——
 * 在默认 TextStyle 上仅加 fontWeight=Bold + fontFeatureSettings="tnum"（数字等宽），
 * 字号/行高/字族沿用默认不动（防布局回归）。
 * 注意：仅金额数字使用此四 style，勿扩展至通用文本。
 */
internal val YUNAYU_TYPOGRAPHY: Typography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
        ),
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
        ),
        titleLarge = base.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
        ),
    )
}

/**
 * Yunayu Material3 主题：深色模式默认跟随系统（PRD 学业友好交互 4）。
 *
 * 品牌主题：樱粉 + 奶油暖调、扁平圆润、低饱和、温馨童趣。浅/深两套品牌色见 Color.kt。
 * 以 Material3 默认 lightColorScheme/darkColorScheme 为基底，仅覆写品牌 token
 * （error 及 onSecondary 等未列出的语义映射保持默认基底派生），保证既有语义色调用零回归。
 * 挂载 colorScheme/shapes/typography 三件套。
 */
@Composable
fun YunayuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = DARK_BACKGROUND,
            surface = DARK_SURFACE,
            surfaceVariant = DARK_SURFACE_VARIANT,
            primary = DARK_PRIMARY,
            onPrimary = DARK_ON_PRIMARY,
            primaryContainer = DARK_PRIMARY_CONTAINER,
            onPrimaryContainer = DARK_ON_PRIMARY_CONTAINER,
            secondary = DARK_SECONDARY,
            secondaryContainer = DARK_SECONDARY_CONTAINER,
            tertiary = DARK_TERTIARY,
            tertiaryContainer = DARK_TERTIARY_CONTAINER,
            onSurface = DARK_ON_SURFACE,
            onSurfaceVariant = DARK_ON_SURFACE_VARIANT,
            outline = DARK_OUTLINE,
        )
    } else {
        lightColorScheme(
            background = LIGHT_BACKGROUND,
            surface = LIGHT_SURFACE,
            surfaceVariant = LIGHT_SURFACE_VARIANT,
            primary = LIGHT_PRIMARY,
            onPrimary = LIGHT_ON_PRIMARY,
            primaryContainer = LIGHT_PRIMARY_CONTAINER,
            onPrimaryContainer = LIGHT_ON_PRIMARY_CONTAINER,
            secondary = LIGHT_SECONDARY,
            secondaryContainer = LIGHT_SECONDARY_CONTAINER,
            tertiary = LIGHT_TERTIARY,
            tertiaryContainer = LIGHT_TERTIARY_CONTAINER,
            onSurface = LIGHT_ON_SURFACE,
            onSurfaceVariant = LIGHT_ON_SURFACE_VARIANT,
            outline = LIGHT_OUTLINE,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = YUNAYU_SHAPES,
        typography = YUNAYU_TYPOGRAPHY,
        content = content,
    )
}
