package com.cmft.scaleai.ui.scale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cmft.scaleai.ScaleAiApplication
import com.cmft.scaleai.data.SettingsManager
import com.cmft.scaleai.ui.profile.SettingsDialog

/**
 * 称重页
 *
 * 状态机驱动的称重全流程（Stage 5）：
 *  - Idle：开始同步 / 手动输入
 *  - Scanning/Connecting/Receiving：进度显示
 *  - Confirming：双人确认弹窗「本次是谁？」（默认高亮 UserMatcher 建议用户，低置信附提示）
 *  - Result：展示保存结果 + AI 报告状态（失败可重试）
 *  - Timeout：超时提示
 */
@Composable
fun ScaleScreen(
    innerPadding: PaddingValues,
    viewModel: ScaleViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as ScaleAiApplication
    val repository = app.repository
    val settingsManager = remember { SettingsManager(context) }
    var showSettings by remember { mutableStateOf(false) }
    var manualWeight by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏：标题 + 设置图标
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "称重",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            }

            // 信息/错误横幅
            state.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 主体：按状态机阶段渲染
            when (state.phase) {
                ScalePhase.Idle -> IdleContent(
                    manualWeight = manualWeight,
                    onManualChange = { manualWeight = it },
                    onStartBle = { viewModel.startBleSession() },
                    onStartManual = { viewModel.startManualEntry(manualWeight) }
                )
                ScalePhase.Scanning,
                ScalePhase.Connecting,
                ScalePhase.Receiving -> ProgressContent(state)
                ScalePhase.Result -> ResultContent(
                    state = state,
                    onReset = { viewModel.reset() },
                    onRetry = { viewModel.retryReport() }
                )
                ScalePhase.Timeout -> TimeoutContent(
                    state = state,
                    onReset = { viewModel.reset() }
                )
                ScalePhase.Confirming -> Text(
                    text = "请确认人选…",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // 设置对话框
        if (showSettings) {
            SettingsDialog(
                repository = repository,
                settingsManager = settingsManager,
                onDismiss = { showSettings = false }
            )
        }

        // 双人确认弹窗
        if (state.phase == ScalePhase.Confirming) {
            ConfirmUserDialog(
                state = state,
                onConfirm = { viewModel.confirmSelection(it) },
                onDismiss = { viewModel.reset() }
            )
        }
    }
}

@Composable
private fun IdleContent(
    manualWeight: String,
    onManualChange: (String) -> Unit,
    onStartBle: () -> Unit,
    onStartManual: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "点击开始同步体脂秤数据，或手动输入体重。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStartBle,
            modifier = Modifier.fillMaxWidth()
        ) { Text("开始同步秤数据") }

        Spacer(modifier = Modifier.height(28.dp))
        Text("或手动输入体重", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = manualWeight,
            onValueChange = onManualChange,
            label = { Text("体重 (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onStartManual,
            enabled = manualWeight.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存手动记录") }
    }
}

@Composable
private fun ProgressContent(state: ScaleUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.statusText.ifBlank { "正在测量…" },
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ResultContent(
    state: ScaleUiState,
    onReset: () -> Unit,
    onRetry: () -> Unit
) {
    val m = state.lastSavedMeasurement
    if (m == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无测量结果")
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("称重完成", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${"%.1f".format(m.weightKg)} kg",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (m.bodyFatPct != null) {
            Text(
                text = "体脂 ${"%.1f".format(m.bodyFatPct)}% · BMI ${"%.1f".format(m.bmi ?: 0.0)}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "基础代谢 ${m.bmrKcal ?: 0} kcal · 内脏脂肪 ${"%.1f".format(m.visceralFat ?: 0.0)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "手动记录（无体脂成分数据）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // AI 报告状态
        when (state.reportStatus) {
            ReportStatus.Generating -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.size(8.dp))
                Text("AI 报告生成中…")
            }
            ReportStatus.Success -> Text(
                "AI 报告已生成",
                color = MaterialTheme.colorScheme.primary
            )
            ReportStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AI 报告生成失败",
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }
            ReportStatus.None -> Unit
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onReset) { Text("再测一次") }
    }
}

@Composable
private fun TimeoutContent(state: ScaleUiState, onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️ 超时", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.message ?: "未能获取到称重数据",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onReset) { Text("返回") }
        }
    }
}

/**
 * 双人确认弹窗「本次是谁？」
 * 默认高亮 UserMatcher 匹配的建议用户，允许改选；匹配置信度低时附加提示。
 */
@Composable
private fun ConfirmUserDialog(
    state: ScaleUiState,
    onConfirm: (com.cmft.scaleai.data.entity.UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val match = state.match
    var selected by remember { mutableStateOf(state.selectedUserId ?: match?.user?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本次是谁？") },
        text = {
            Column {
                Text(
                    text = "本次称重（${"%.1f".format(state.measurementWeightKg)} kg）属于哪个档案？",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.lowConfidence) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠️ 匹配置信度较低，请手动确认人选",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                state.users.forEach { user ->
                    val isSelected = selected == user.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = user.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { selected = user.id })
                        Text(
                            text = "${user.name}（${if (user.gender == "male") "男" else "女"}）",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val chosen = state.users.firstOrNull { it.id == selected }
                if (chosen != null) onConfirm(chosen)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
