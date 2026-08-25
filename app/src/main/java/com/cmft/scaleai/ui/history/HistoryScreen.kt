package com.cmft.scaleai.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史页：趋势图 + 记录列表
 */
@Composable
fun HistoryScreen(
    innerPadding: PaddingValues,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "历史记录",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (state.measurements.isEmpty()) {
            Text("暂无记录", modifier = Modifier.padding(top = 40.dp))
            return@Column
        }

        // 趋势图（体重，时间正序）
        val weights = state.measurements.reversed().map { it.weightKg }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("体重趋势", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                TrendLineChart(values = weights)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 记录列表
        LazyColumn {
            items(state.measurements) { m ->
                HistoryItemRow(m)
            }
        }
    }
}

@Composable
private fun HistoryItemRow(m: com.cmft.scaleai.data.entity.Measurement) {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${fmt.format(Date(m.timestamp))}  ${"%.1f".format(m.weightKg)} kg",
                style = MaterialTheme.typography.bodyLarge
            )
            if (m.bodyFatPct != null) {
                Text(
                    text = "体脂 ${"%.1f".format(m.bodyFatPct)}%  ·  BMI ${"%.1f".format(m.bmi ?: 0.0)}  ·  基础代谢 ${m.bmrKcal ?: 0} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
