package com.expfal.yunayu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Yunayu Material3 主题：深色模式默认跟随系统（PRD 学业友好交互 4）。
 * Sprint 0 使用 Material3 默认配色，视觉体系待设计稿确定后替换。
 */
@Composable
fun YunayuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
