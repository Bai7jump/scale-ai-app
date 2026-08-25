package com.cmft.scaleai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.cmft.scaleai.ui.coach.AiCoachScreen
import com.cmft.scaleai.ui.history.HistoryScreen
import com.cmft.scaleai.ui.scale.ScaleScreen
import com.cmft.scaleai.ui.theme.ScaleAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScaleAiTheme {
                MainScreen()
            }
        }
    }
}

/**
 * 底部导航的三个 Tab
 */
enum class MainTab(val label: String, val icon: ImageVector) {
    Scale("称重", Icons.Filled.MonitorWeight),
    Coach("AI教练", Icons.Filled.SmartToy),
    History("历史", Icons.Filled.History)
}

@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Scale) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.Scale -> ScaleScreen(innerPadding)
            MainTab.Coach -> AiCoachScreen(innerPadding)
            MainTab.History -> HistoryScreen(innerPadding)
        }
    }
}
