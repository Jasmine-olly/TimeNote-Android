package com.timenote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// TimeNote 线条手绘治愈风调色板
// 骨架：奶油纸底 + 暖墨色字 + 淡彩点缀（鼠尾草绿主色）

/** 深鼠尾草绿（作标题/柱状图/白字底，兼容旧引用） */
val Green = Color(0xFF6F8A6A)

/** 鼠尾草绿（主题主色） */
val Sage = Color(0xFF8FA98B)
private val SageLight = Color(0xFFE2EADF)
private val Paper = Color(0xFFF6F1E5)      // 奶油纸底
private val PaperBright = Color(0xFFFDFBF4) // 弹窗亮纸
private val Ink = Color(0xFF4A4438)        // 暖墨棕字
private val InkMuted = Color(0xFF6B6559)
private val PinkSoft = Color(0xFFE8C4C0)   // 淡粉点缀
private val YellowSoft = Color(0xFFE8D9A0) // 鹅黄点缀
private val Line = Color(0xFFD8D2C4)       // 手绘线（浅）

private val LightColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageLight,
    onPrimaryContainer = Color(0xFF2E3A2B),
    secondary = Color(0xFFD8A8A0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E0DC),
    onSecondaryContainer = Color(0xFF4A312E),
    tertiary = Color(0xFFC9B76F),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE6D6),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PaperBright,
    surfaceContainerLow = Color(0xFFF1EBDB),
    surfaceContainer = Color(0xFFEBE4D2),
    surfaceContainerHigh = Color(0xFFE6DFCC),
    surfaceContainerHighest = Color(0xFFE1D9C6),
    outline = Line,
    outlineVariant = Color(0xFFE4DED0),
    error = Color(0xFFB3554A),
    onError = Color.White,
    errorContainer = Color(0xFFF6DDD8),
    onErrorContainer = Color(0xFF4A1F1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8BE9E),
    onPrimary = Color(0xFF1F2B1D),
    primaryContainer = Color(0xFF3C4A38),
    onPrimaryContainer = Color(0xFFDDE6D8),
    secondary = Color(0xFFD8A8A0),
    background = Color(0xFF2B2822),
    onBackground = Color(0xFFE8E2D4),
    surface = Color(0xFF2B2822),
    onSurface = Color(0xFFE8E2D4),
    surfaceVariant = Color(0xFF3A362E),
    onSurfaceVariant = Color(0xFFC5BFB0),
    surfaceContainerLowest = Color(0xFF24221D),
    surfaceContainerLow = Color(0xFF302C26),
    surfaceContainer = Color(0xFF353129),
    surfaceContainerHigh = Color(0xFF3B372F),
    surfaceContainerHighest = Color(0xFF423D34),
    outline = Color(0xFF4A463C),
    error = Color(0xFFD48A7F),
)

/** 圆润造型：卡片/按钮更大的圆角，贴合治愈手账感 */
private val Shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun TimeNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = Shapes,
        content = content,
    )
}
