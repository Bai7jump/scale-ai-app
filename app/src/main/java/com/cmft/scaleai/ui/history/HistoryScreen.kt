package com.cmft.scaleai.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 历史页（骨架）
 * 后续实现：趋势图 + 记录列表
 */
@Composable
fun HistoryScreen(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "历史页",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
