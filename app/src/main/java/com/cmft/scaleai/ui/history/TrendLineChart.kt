package com.cmft.scaleai.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 简单的趋势折线图（无第三方库）
 * @param values 数值序列（时间正序）
 * @param lineColor 线条颜色
 * @param pointColor 数据点颜色
 */
@Composable
fun TrendLineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF4E7A3A),
    pointColor: Color = Color(0xFF4E7A3A)
) {
    if (values.size < 2) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val w = size.width
        val h = size.height
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0

        val xStep = w / (values.size - 1)
        val yFor = { v: Double -> h - ((v - min) / range * (h - 20f)).toFloat() - 10f }

        // 画折线
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * xStep
            val y = yFor(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 4f))

        // 画数据点
        values.forEachIndexed { i, v ->
            drawCircle(
                color = pointColor,
                radius = 5f,
                center = Offset(i * xStep, yFor(v))
            )
        }
    }
}
