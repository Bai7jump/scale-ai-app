package com.cmft.scaleai.ui.scale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cmft.scaleai.ScaleAiApplication
import com.cmft.scaleai.data.SettingsManager
import com.cmft.scaleai.ui.profile.SettingsDialog

/**
 * 称重页
 * 右上角设置入口 + 同步按钮
 */
@Composable
fun ScaleScreen(innerPadding: PaddingValues) {
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as ScaleAiApplication
    val repository = app.repository
    val settingsManager = remember { SettingsManager(context) }

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

            // 主体：同步按钮
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { /* TODO: 触发称重同步 */ }) {
                    Text("点击同步秤数据")
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                repository = repository,
                settingsManager = settingsManager,
                onDismiss = { showSettings = false }
            )
        }
    }
}
