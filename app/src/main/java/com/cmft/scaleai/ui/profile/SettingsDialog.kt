package com.cmft.scaleai.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cmft.scaleai.data.ScaleRepository
import com.cmft.scaleai.data.SettingsManager
import kotlinx.coroutines.launch

/**
 * 档案/设置对话框：档案列表 + 切换 + API Key
 */
@Composable
fun SettingsDialog(
    repository: ScaleRepository,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val users by repository.observeUsers().collectAsState(initial = emptyList())
    val apiKey by settingsManager.apiKey.collectAsState(initial = null)
    var apiKeyInput by remember { mutableStateOf(apiKey ?: "") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(apiKey) { apiKeyInput = apiKey ?: "" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("档案与设置") },
        text = {
            Column {
                Text("用户档案", style = MaterialTheme.typography.titleSmall)
                users.forEach { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${user.name}（${if (user.gender == "male") "男" else "女"}）",
                            modifier = Modifier.weight(1f)
                        )
                        if (user.isActive) {
                            Text("当前", color = MaterialTheme.colorScheme.primary)
                        } else {
                            TextButton(onClick = {
                                scope.launch {
                                    repository.setActiveUser(user.id)
                                }
                            }) {
                                Text("设为当前")
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("DeepSeek API Key", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = {
                    scope.launch { settingsManager.saveApiKey(apiKeyInput.trim()) }
                }) {
                    Text("保存")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
