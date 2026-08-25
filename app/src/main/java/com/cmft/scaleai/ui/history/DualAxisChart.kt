package com.cmft.scaleai.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 双 Y 轴趋势图（纯 Compose Canvas，无第三方依赖）。
 *
 * 左 Y 轴：体重(kg)；右 Y 轴：体脂率(%)。
 * 两条折线量纲不同，故各有独立坐标轴。
 *
 * 体脂缺失断点：bodyFatPcts 中某一位为 null 时，在该点断开（不连线），
 * 只在相邻两个非 null 的点之间连线，从而形成断点。
 *
 * @param weights      体重序列（时间正序，与 bodyFatPcts 一一对应）
 * @param bodyFatPcts  体脂率序列（可含 null 表示缺失，时间正序）
 * @param weightColor  体重线颜色
 * @param fatColor     体脂线颜色
 */
@Composable
fun DualAxisChart(
    weights: List<Double>,
    bodyFatPcts: List<Double?>,
    modifier: Modifier = Modifier,
    weightColor: Color = Color(0xFF4E7A3A),
    fatColor: Color = Color(0xFFD97B4E),
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (weights.size < 2) return

    val fatValues = bodyFatPcts.filterNotNull()
    val wMin = weights.min()
    val wMax = weights.max()
    val fMin = fatValues.minOrNull() ?: 0.0
    val fMax = fatValues.maxOrNull() ?: 0.0

    val labelStyle = MaterialTheme.typography.labelSmall

    Row(modifier = modifier.fillMaxWidth()) {
        // 左轴刻度：上=最大值，下=最小值
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(34.dp)) {
            Text(formatNumber(wMax), style = labelStyle, color = labelColor)
            Column(modifier = Modifier.weight(1f)) { /* 撑开，让两极刻度对齐图区上下 */ }
            Text(formatNumber(wMin), style = labelStyle, color = labelColor)
        }

        // 主绘图区
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(180.dp)
                .padding(horizontal = 8.dp)
        ) {
            val padH = 4f
            val padTop = 10f
            val padBottom = 10f
            val plotW = size.width - padH * 2
            val plotH = size.height - padTop - padBottom
            if (plotW <= 0 || plotH <= 0) return@Canvas

            val n = weights.size
            val xStep = plotW / (n - 1)
            val xFor: (Int) -> Float = { i -> padH + i * xStep }

            val wRange = (wMax - wMin).takeIf { it > 0 } ?: 1.0
            val fRange = (fMax - fMin).takeIf { it > 0 } ?: 1.0
            // 顶部=最大值，底部=最小值
            val wYFor: (Double) -> Float = { v -> padTop + ((wMax - v) / wRange * plotH).toFloat() }
            val fYFor: (Double) -> Float = { v -> padTop + ((fMax - v) / fRange * plotH).toFloat() }

            // 水平网格线（均分 4 段）
            for (g in 0..4) {
                val y = padTop + plotH * g / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(padH, y),
                    end = Offset(padH + plotW, y),
                    strokeWidth = 1f
                )
            }

            // 体重线（连所有点）
            val wPath = Path()
            weights.forEachIndexed { i, v ->
                val x = xFor(i)
                val y = wYFor(v)
                if (i == 0) wPath.moveTo(x, y) else wPath.lineTo(x, y)
            }
            drawPath(wPath, color = weightColor, style = Stroke(width = 4f))
            weights.forEachIndexed { i, v ->
                drawCircle(color = weightColor, radius = 4f, center = Offset(xFor(i), wYFor(v)))
            }

            // 体脂线（仅非 null 点之间连线，缺省处断点）
            var prevValid = -1
            bodyFatPcts.forEachIndexed { i, v ->
                if (v == null) {
                    prevValid = -1  // 断点：重置连线段
                } else {
                    val x = xFor(i)
                    val y = fYFor(v)
                    if (prevValid >= 0) {
                        drawLine(
                            color = fatColor,
                            start = Offset(xFor(prevValid), fYFor(bodyFatPcts[prevValid]!!)),
                            end = Offset(x, y),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(color = fatColor, radius = 4f, center = Offset(x, y))
                    prevValid = i
                }
            }
        }

        // 右轴刻度：上=最大值，下=最小值
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(34.dp)) {
            Text(formatNumber(fMax), style = labelStyle, color = labelColor)
            Column(modifier = Modifier.weight(1f)) { }
            Text(formatNumber(fMin), style = labelStyle, color = labelColor)
        }
    }
}

/** 数字显示：去掉多余小数（<=1 位小数） */
private fun formatNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
