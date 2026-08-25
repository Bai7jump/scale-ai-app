package com.cmft.scaleai.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cmft.scaleai.data.entity.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 教练页（Phase 7）
 *
 * 结构：顶部栏(标题+档案切换) → 报告卡片(最新测量 + AI 报告/重试) → 对话气泡列表 → 底部输入区。
 *
 * - user 消息右对齐（primaryContainer 底色），assistant 消息左对齐（surfaceVariant 底色）。
 * - AI 请求中：输入区/报告卡片显示 CircularProgressIndicator。
 * - 建档 reportGenerated == false 时报告卡片显示「重试生成」，点击调 [AiCoachViewModel.regenerateReport]。
 * - 无需 BLE，仅负责 AI 教练对话 + 报告展示。
 */
@Composable
fun AiCoachScreen(
    innerPadding: PaddingValues,
    viewModel: AiCoachViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // 顶部栏：标题 + 档案切换
        TopBar(
            users = state.users,
            activeUser = state.activeUser,
            onSwitchUser = viewModel::switchUser
        )

        HorizontalDivider()

        // 错误横幅
        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (state.activeUser == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无用户档案，请先到「档案与设置」创建",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 报告卡片（最新测量 + AI 报告 / 重试）
            ReportCard(
                state = state,
                onRegenerate = viewModel::regenerateReport
            )

            // 对话气泡列表
            ChatList(
                messages = state.chatMessages,
                modifier = Modifier.weight(1f)
            )

            HorizontalDivider()

            // 底部输入区
            InputBar(
                value = input,
                onValueChange = { input = it },
                loading = state.loading,
                onSend = {
                    viewModel.sendMessage(input)
                    input = ""
                }
            )
        }
    }
}

// ===================== 顶部栏 + 档案切换 =====================

@Composable
private fun TopBar(
    users: List<com.cmft.scaleai.data.entity.UserProfile>,
    activeUser: com.cmft.scaleai.data.entity.UserProfile?,
    onSwitchUser: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "AI教练",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        // 档案切换 chips
        if (users.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                users.forEach { user ->
                    FilterChip(
                        selected = user.id == activeUser?.id,
                        onClick = { onSwitchUser(user.id) },
                        label = { Text(user.name) }
                    )
                }
            }
        }
    }
}

// ===================== 报告卡片 =====================

/**
 * 报告卡片：显示当前档案最新一次测量，及其生成的 AI 报告。
 * 当最新测量 reportGenerated == false 时显示「重试生成」按钮。
 *
 * 报告正文取自 chat_messages 中当前档案最近一条 assistant 消息
 * （报告以 assistant 消息落库，与测量暂无外键关联）。
 */
@Composable
private fun ReportCard(
    state: CoachUiState,
    onRegenerate: () -> Unit
) {
    val m = state.latestMeasurement ?: return
    val reportContent = state.chatMessages.lastOrNull { it.role == "assistant" }?.content

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AI 报告",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(m.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            // 测量摘要
            val bodyFat = m.bodyFatPct?.let { " · 体脂 ${"%.1f".format(it)}%" } ?: ""
            val bmi = m.bmi?.let { " · BMI ${"%.1f".format(it)}" } ?: ""
            Text(
                text = "${"%.1f".format(m.weightKg)} kg$bodyFat$bmi",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (m.source == "ble") "秤同步" else "手动记录",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            when {
                // AI 请求中
                state.reportStatus == ReportStatus.Generating -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("报告生成中…", style = MaterialTheme.typography.bodyMedium)
                }

                // 尚未生成 → 显示重试按钮
                !m.reportGenerated -> {
                    Text(
                        text = "报告尚未生成",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRegenerate,
                        enabled = !state.loading
                    ) { Text("重试生成") }
                }

                // 已生成但正文为空（异常兜底）
                reportContent.isNullOrBlank() -> Text(
                    text = "报告已生成",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // 已生成 → 展示报告正文
                else -> Text(
                    text = reportContent,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ===================== 对话列表 =====================

@Composable
private fun ChatList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    // 新消息到达时滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有对话，说说你的减脂目标吧",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
    }
}

/**
 * 单条消息气泡：user 右对齐（primaryContainer）、assistant 左对齐（surfaceVariant）。
 */
@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleContentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = message.content,
            color = bubbleContentColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = bubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

// ===================== 底部输入区 =====================

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 请求中的 loading 指示
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("输入消息…") },
            modifier = Modifier.weight(1f),
            maxLines = 4,
            enabled = !loading,
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = loading.not() && value.isNotBlank()
        ) {
            Icon(Icons.Filled.Send, contentDescription = "发送")
        }
    }
}

// ===================== 工具 =====================

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(timestamp))
