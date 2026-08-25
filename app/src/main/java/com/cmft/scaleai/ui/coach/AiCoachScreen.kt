package com.cmft.scaleai.ui.coach

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
 * AI 教练页（骨架）
 * 后续实现：报告卡片 + 对话
 */
@Composable
fun AiCoachScreen(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "AI教练页",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
