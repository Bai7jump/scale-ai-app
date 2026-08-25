package com.cmft.scaleai.ui.scale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 称重页（骨架）
 * 后续实现：同步按钮 + 手动输入 + 结果卡片
 */
@Composable
fun ScaleScreen(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = { /* TODO: 触发称重同步 */ }) {
            Text("点击同步秤数据")
        }
        Text(
            text = "称重页",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
        )
    }
}
