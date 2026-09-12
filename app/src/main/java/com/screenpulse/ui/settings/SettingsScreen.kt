package com.screenpulse.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.core.content.ContextCompat
import com.screenpulse.repository.ThemeMode
import com.screenpulse.util.PermissionUtils
import com.screenpulse.viewmodel.MainViewModel

/**
 * 设置页：主题模式（跟随系统/浅色/深色）、悬浮窗、快捷键、压缩档位。
 * 主题切换写入 DataStore → ViewModel Flow → Compose 即时重组，无需重启。
 */
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val themeMode by vm.themeMode
    val floatingEnabled by vm.floatingEnabled
    val shortcutEnabled by vm.shortcutEnabled
    val compressLevel by vm.compressLevel

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ===== 主题设置 =====
        SectionTitle("UI 主题")
        ThemeMode.FOLLOW_SYSTEM.let {
            RadioRow("跟随系统", "自动匹配系统深色/浅色", themeMode == it) { vm.changeTheme(it) }
        }
        ThemeMode.LIGHT.let {
            RadioRow("浅色模式", "白色背景 + 绿色主色", themeMode == it) { vm.changeTheme(it) }
        }
        ThemeMode.DARK.let {
            RadioRow("深色模式", "深色背景 + 低饱和绿", themeMode == it) { vm.changeTheme(it) }
        }
        Spacer(Modifier.height(8.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 麦克风权限 =====
        SectionTitle("权限")
        SwitchRow(
            title = "麦克风权限",
            subtitle = "用于仅麦克风 / 混音收音",
            checked = micGranted
        ) { if (micGranted) {
                // 已授权无需操作
            } else micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        Spacer(Modifier.height(8.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 悬浮窗 =====
        SectionTitle("悬浮窗")
        SwitchRow(
            title = "启用悬浮窗",
            subtitle = "桌面常驻拖动控制，需系统悬浮窗权限",
            checked = floatingEnabled
        ) {
            if (!PermissionUtils.hasOverlay(context)) {
                PermissionUtils.requestOverlay(context as android.app.Activity)
            } else vm.setFloatingEnabled(!floatingEnabled)
        }
        Spacer(Modifier.height(8.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 快捷键 =====
        SectionTitle("自定义快捷键")
        SwitchRow(
            title = "启用物理按键启停",
            subtitle = "媒体键短按触发录屏开始/暂停/继续",
            checked = shortcutEnabled
        ) { vm.setShortcutEnabled(!shortcutEnabled) }
        Spacer(Modifier.height(8.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 压缩档位 =====
        SectionTitle("视频压缩档位")
        com.screenpulse.repository.CompressLevel.entries.forEach { level ->
            RadioRow(level.label, compressSubtitle(level), compressLevel == level) {
                vm.setCompressLevel(level)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun compressSubtitle(level: com.screenpulse.repository.CompressLevel): String = when (level) {
    com.screenpulse.repository.CompressLevel.FAST -> "极致缩小体积，适合日常录制"
    com.screenpulse.repository.CompressLevel.BALANCED -> "画质与体积平衡，通用首选"
    com.screenpulse.repository.CompressLevel.LOSSLESS -> "保留原始画质，适合高清教程"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
