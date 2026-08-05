package com.timenote.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timenote.ui.theme.Green
import kotlin.math.PI
import kotlin.math.sin

/** 手绘波浪线：多周期正弦波 + 轻微抖动，用于标题下/卡片分隔（治愈手绘风点缀） */
@Composable
fun HandDrawnLine(
    modifier: Modifier = Modifier,
    color: Color = Green,
    thickness: Dp = 2.dp,
) {
    Canvas(modifier = modifier) {
        val midY = size.height / 2f
        val amp = size.height * 0.42f
        val cycles = 3f
        val points = 48
        val path = Path().apply {
            moveTo(0f, midY)
            for (i in 1..points) {
                val t = i / points.toFloat()
                val x = size.width * t
                // 固定伪随机抖动（±20%），模拟手绘不平整
                val wobble = (((i * 37L) % 9) - 4) * 0.05f
                val y = midY + sin(t * 2f * PI.toFloat() * cycles) * amp * (1f + wobble)
                lineTo(x, y)
            }
        }
        drawPath(path, color, style = Stroke(width = thickness.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
