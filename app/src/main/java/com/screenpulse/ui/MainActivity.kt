package com.screenpulse.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.screenpulse.repository.ThemeMode
import com.screenpulse.ui.settings.HomeScreen
import com.screenpulse.ui.settings.RecordParamsScreen
import com.screenpulse.ui.settings.SettingsScreen
import com.screenpulse.ui.theme.ScreenPulseTheme
import com.screenpulse.ui.videolist.VideoListScreen
import com.screenpulse.viewmodel.MainViewModel
import com.screenpulse.viewmodel.VideoListViewModel

/**
 * 主界面：底部导航（首页 / 参数 / 文件 / 设置），全局主题切换即时生效。
 */
class MainActivity : ComponentActivity() {

    private val mainVm: MainViewModel by viewModels()
    private val videoVm: VideoListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by mainVm.themeMode
            ScreenPulseTheme(themeMode = themeMode) {
                MainScaffold(mainVm = mainVm, videoVm = videoVm)
            }
        }
    }
}

private enum class Tab(val label: String) {
    HOME("录制"), PARAMS("参数"), FILES("文件"), SETTINGS("设置")
}

@Composable
private fun MainScaffold(mainVm: MainViewModel, videoVm: VideoListViewModel) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(tab.icon(), contentDescription = tab.label)
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        val content: @Composable () -> Unit = when (currentTab) {
            Tab.HOME -> { { HomeScreen(mainVm) } }
            Tab.PARAMS -> { { RecordParamsScreen(mainVm) } }
            Tab.FILES -> { { VideoListScreen(videoVm) } }
            Tab.SETTINGS -> { { SettingsScreen(mainVm) } }
        }
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            content()
        }
    }
}

private fun Tab.icon() = when (this) {
    Tab.HOME -> Icons.Filled.Home
    Tab.PARAMS -> Icons.Filled.Tune
    Tab.FILES -> Icons.Filled.Movie
    Tab.SETTINGS -> Icons.Filled.Settings
}
