package com.cmft.scaleai.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cmft.scaleai.data.entity.Measurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史页（完成版）：用户切换 + 双 Y 轴趋势图（体重/体脂率）+ 记录列表
 */
@Composable
fun HistoryScreen(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    viewModel: HistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // 顶部选中的档案 id（默认跟随激活档案）
    val selectedId = state.selectedUserId ?: state.activeUser?.id

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

        // 用户切换（你/她），默认高亮激活档案
        if (state.allUsers.isNotEmpty()) {
            val selectedIndex = state.allUsers.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 } ?: 0
            TabRow(
                selectedTabIndex = selectedIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                state.allUsers.forEachIndexed { index, user ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectUser(user.id) },
                        text = { Text(user.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.measurements.isEmpty()) {
            Text("暂无记录", modifier = Modifier.padding(top = 40.dp))
            return@Column
        }

        // ===== 体脂率趋势图（时间正序，单轴，折点可点击） =====
        val asc = state.measurements.reversed() // 倒序 -> 正序（时间从早到晚）
        val weights = asc.map { it.weightKg }
        val bodyFatPcts = asc.map { it.bodyFatPct }
        val dates = asc.map {
            SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(it.timestamp))
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("体脂率趋势", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Y轴:体脂率(%)  X轴:日期（点击折点查看数值）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                BodyFatChart(bodyFatPcts = bodyFatPcts, dates = dates, weights = weights)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 记录列表 =====
        LazyColumn {
            items(state.measurements) { m ->
                HistoryItemRow(m)
            }
        }
    }
}

/**
 * 单条记录：日期 / 体重 / 体脂 / 来源
 */
@Composable
private fun HistoryItemRow(m: Measurement) {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fmt.format(Date(m.timestamp)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sourceLabel(m.source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // 体重
                Text(
                    text = "体重 ${"%.1f".format(m.weightKg)} kg",
                    style = MaterialTheme.typography.bodyLarge
                )
                // 体脂（缺失显示 "-"）
                Text(
                    text = if (m.bodyFatPct != null) "体脂 ${"%.1f".format(m.bodyFatPct)}%" else "体脂 -",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 来源显示：ble=BLE、manual=手动、seed=模拟、其余未知 -> "-" */
private fun sourceLabel(source: String): String = when (source) {
    "ble" -> "BLE"
    "manual" -> "手动"
    "seed" -> "模拟"
    else -> "-"
}
