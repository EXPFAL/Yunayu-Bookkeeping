package com.expfal.yunayu.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Yunayu 品牌色板（仅主题层使用，业务代码禁止直接引用）。
 *
 * 来源：参考卡通图「主题-48165ef7.jpg」「图标-b13b362e.png」提取配色与风格灵感，
 * 仅作配色/风格参考，非 UI 布局参考。
 *
 * 整体气质：樱粉 + 奶油暖调、扁平圆润、低饱和、温馨童趣。
 *
 * 配色逻辑（既定色板）：
 * - 浅色：暖粉白底 + 暖棕主色。primary 取暖棕（粉底上对比度佳），secondary 取深化
 *   腮红粉（保证可读），tertiary 取深化鼠尾草绿，outline 走暖灰系；error 沿用
 *   Material3 默认语义红（不混入粉色）。
 * - 深色：整体替换 Material3 默认深紫，改为深暖灰紫底 + 提亮樱粉，暖调协调；
 *   error 沿用 Material3 深色默认语义红。
 *
 * 前景/背景组合已做对比度自查（正文 ≥ 4.5:1）；未列出的语义色（如 onSecondary 等）
 * 由主题装配处保持 Material3 默认派生。
 */

// 浅色主题品牌色
internal val LIGHT_BACKGROUND = Color(0xFFFFF8F6)
internal val LIGHT_SURFACE = Color(0xFFFFF8F6)
internal val LIGHT_SURFACE_VARIANT = Color(0xFFFFE6E6)
internal val LIGHT_PRIMARY = Color(0xFF7A4F4F)
internal val LIGHT_ON_PRIMARY = Color(0xFFFFF8F6)
internal val LIGHT_PRIMARY_CONTAINER = Color(0xFFFFE6E6)
internal val LIGHT_ON_PRIMARY_CONTAINER = Color(0xFF7A4F4F)
internal val LIGHT_SECONDARY = Color(0xFFE8879C)
internal val LIGHT_SECONDARY_CONTAINER = Color(0xFFFFD9E0)
internal val LIGHT_TERTIARY = Color(0xFF7FA37A)
internal val LIGHT_TERTIARY_CONTAINER = Color(0xFFDCEBD5)
internal val LIGHT_OUTLINE = Color(0xFFC9A9A0)
internal val LIGHT_ON_SURFACE = Color(0xFF4A3B38)
internal val LIGHT_ON_SURFACE_VARIANT = Color(0xFF8A7370)

// 深色主题品牌色
internal val DARK_BACKGROUND = Color(0xFF2B2326)
internal val DARK_SURFACE = Color(0xFF2B2326)
internal val DARK_SURFACE_VARIANT = Color(0xFF4A3B3E)
internal val DARK_PRIMARY = Color(0xFFF2B8C6)
internal val DARK_ON_PRIMARY = Color(0xFF4A2E33)
internal val DARK_PRIMARY_CONTAINER = Color(0xFF6B4450)
internal val DARK_ON_PRIMARY_CONTAINER = Color(0xFFFFD9E0)
internal val DARK_SECONDARY = Color(0xFFE8A0B0)
internal val DARK_SECONDARY_CONTAINER = Color(0xFF5C3A42)
internal val DARK_TERTIARY = Color(0xFFA8C6A0)
internal val DARK_TERTIARY_CONTAINER = Color(0xFF3E4F3A)
internal val DARK_ON_SURFACE = Color(0xFFF2E4E1)
internal val DARK_ON_SURFACE_VARIANT = Color(0xFFCDB4B0)
internal val DARK_OUTLINE = Color(0xFF9A817D)
