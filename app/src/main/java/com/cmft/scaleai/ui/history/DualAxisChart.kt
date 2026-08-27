package com.cmft.scaleai.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 单轴体脂率折线图（纯 Compose Canvas，无第三方依赖）。
 *
 * Y 轴：体脂率(%)，带刻度标签（最大/中间/最小）。
 * X 轴：日期标签（MM-dd），采样显示。
 * 折点可点击：点击某个数据点高亮并显示该点信息（日期/体脂率/体重）。
 *
 * 体脂缺失断点：bodyFatPcts 中某位为 null 时，在该点断开（不连线）。
 *
 * @param bodyFatPcts  体脂率序列（可含 null 表示缺失，时间正序）
 * @param dates        X 轴日期标签（MM-dd，时间正序，与 bodyFatPcts 一一对应）
 * @param weights      体重序列（时间正序，用于点击时展示该点体重）
 */
@Composable
fun BodyFatChart(
    bodyFatPcts: List<Double?>,
    dates: List<String>,
    weights: List<Double>,
    modifier: Modifier = Modifier,
    fatColor: Color = Color(0xFFD97B4E),
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedColor: Color = Color(0xFF4E7A3A)
) {
    val fatValues = bodyFatPcts.filterNotNull()
    if (fatValues.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(70.dp))
            Text("暂无体脂数据", color = labelColor, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val fMin = fatValues.min()
    val fMax = fatValues.max()
    val n = bodyFatPcts.size
    val labelStyle = MaterialTheme.typography.labelSmall

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // 图表区高度固定，Canvas 高度一致，保证点击与绘制坐标统一
    val chartHeight = 180

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左 Y 轴刻度：上=最大值，中=中间值，下=最小值
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .width(40.dp)
                    .height(chartHeight.dp)
            ) {
                Text("${formatNumber(fMax)}%", style = labelStyle, color = labelColor)
                Spacer(modifier = Modifier.weight(1f))
                Text("${formatNumber((fMax + fMin) / 2)}%", style = labelStyle, color = labelColor)
                Spacer(modifier = Modifier.weight(1f))
                Text("${formatNumber(fMin)}%", style = labelStyle, color = labelColor)
            }

            // 主绘图区：折线 + 可点击点
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                // 检测按下(ACTION_DOWN)事件
                                val down = event.changes.firstOrNull { it.pressed }
                                if (down != null) {
                                    val offset = down.position
                                    if (n <= 1) {
                                        selectedIndex = if (bodyFatPcts[0] != null) 0 else null
                                    } else {
                                        var best = -1
                                        var bestDist = Float.MAX_VALUE
                                        bodyFatPcts.forEachIndexed { i, v ->
                                            if (v != null) {
                                                val x = i * (size.width / (n - 1).toFloat())
                                                val dist = kotlin.math.abs(offset.x - x)
                                                if (dist < bestDist) {
                                                    bestDist = dist
                                                    best = i
                                                }
                                            }
                                        }
                                        selectedIndex = if (best >= 0) best else null
                                    }
                                }
                            }
                        }
                    }
            ) {
                val padH = 4f
                val padTop = 10f
                val padBottom = 18f
                val plotW = size.width - padH * 2
                val plotH = size.height - padTop - padBottom
                if (plotW <= 0 || plotH <= 0) return@Canvas

                val xStep = if (n > 1) plotW / (n - 1) else plotW
                val xFor: (Int) -> Float = { i -> padH + i * xStep }
                val fRange = (fMax - fMin).takeIf { it > 0 } ?: 1.0
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

                // 体脂线（仅非 null 点之间连线，缺省处断点）
                var prevValid = -1
                bodyFatPcts.forEachIndexed { i, v ->
                    if (v == null) {
                        prevValid = -1
                    } else {
                        val x = xFor(i)
                        val y = fYFor(v)
                        if (prevValid >= 0) {
                            drawLine(
                                color = fatColor,
                                start = Offset(xFor(prevValid), fYFor(bodyFatPcts[prevValid]!!)),
                                end = Offset(x, y),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                        if (selectedIndex == i) {
                            drawCircle(color = selectedColor, radius = 7f, center = Offset(x, y))
                            drawCircle(color = Color.White, radius = 3f, center = Offset(x, y))
                        } else {
                            drawCircle(color = fatColor, radius = 4f, center = Offset(x, y))
                        }
                        prevValid = i
                    }
                }

                // X 轴底部刻度短竖线
                val step = (n / 5).coerceAtLeast(1)
                for (i in bodyFatPcts.indices step step) {
                    val x = xFor(i)
                    drawLine(
                        color = gridColor,
                        start = Offset(x, size.height - padBottom),
                        end = Offset(x, size.height - padBottom + 4f),
                        strokeWidth = 1f
                    )
                }
            }
        }

        // X 轴日期标签行（采样显示，最多 5 个）
        val step = (n / 5).coerceAtLeast(1)
        val labelIndices = buildList {
            for (i in bodyFatPcts.indices step step) add(i)
            if (lastOrNull() != bodyFatPcts.lastIndex) add(bodyFatPcts.lastIndex)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labelIndices.forEach { i ->
                Text(
                    text = dates.getOrElse(i) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }

        // 选中点信息
        if (selectedIndex != null && selectedIndex!! in bodyFatPcts.indices) {
            val idx = selectedIndex!!
            val fat = bodyFatPcts[idx]
            val date = dates.getOrElse(idx) { "" }
            val weight = weights.getOrElse(idx) { 0.0 }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(date, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (fat != null) "体脂 ${"%.1f".format(fat)}%" else "体脂 -",
                    style = MaterialTheme.typography.bodyMedium,
                    color = selectedColor
                )
                Text(
                    text = "体重 ${"%.1f".format(weight)} kg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor
                )
            }
        }
    }
}

/** 数字显示：去掉多余小数（<=1 位小数） */
private fun formatNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
