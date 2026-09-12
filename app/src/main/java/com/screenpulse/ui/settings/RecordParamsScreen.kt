package com.screenpulse.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.screenpulse.repository.AudioMode
import com.screenpulse.repository.RecordParams
import com.screenpulse.viewmodel.MainViewModel

/**
 * 录制参数页：分辨率、帧率、码率、音频模式、音量平衡、水印、倒计时。
 * 参数即时保存到 DataStore，录制时由前台服务读取。
 */
@Composable
fun RecordParamsScreen(vm: MainViewModel) {
    val saved by vm.recordParams
    var params by remember { mutableStateOf(saved) }

    fun save() = vm.saveParams(params)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ===== 分辨率档位 =====
        SectionTitle("分辨率")
        val presets = listOf(
            "240P" to (320 to 480),
            "480P" to (720 to 1280),
            "720P" to (720 to 1280),
            "1080P" to (1080 to 1920),
            "2K" to (1440 to 2560),
            "4K" to (2160 to 3840)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (label, dim) ->
                val selected = params.width == dim.first && params.height == dim.second
                FilterChip(
                    selected = selected,
                    onClick = {
                        params = params.copy(width = dim.first, height = dim.second)
                        save()
                    },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = "${params.width}×${params.height}",
            onValueChange = {},
            enabled = false,
            label = { Text("当前分辨率（横竖屏自动适配）") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        // ===== 自定义宽高 =====
        Row {
            OutlinedTextField(
                value = params.width.toString(),
                onValueChange = { v -> params = params.copy(width = v.toIntOrNull() ?: params.width); save() },
                label = { Text("自定义宽") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = params.height.toString(),
                onValueChange = { v -> params = params.copy(height = v.toIntOrNull() ?: params.height); save() },
                label = { Text("自定义高") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 帧率 =====
        SectionTitle("帧率")
        Row {
            listOf(30, 60).forEach { fps ->
                FilterChip(
                    selected = params.fps == fps,
                    onClick = { params = params.copy(fps = fps); save() },
                    label = { Text("${fps}FPS") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 码率 =====
        SectionTitle("码率（智能适配 + 手动）")
        val bitrateMbps = (params.bitrate / 1_000_000f).toInt()
        Text("${bitrateMbps} Mbps", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = bitrateMbps.toFloat(),
            onValueChange = {
                params = params.copy(bitrate = (it * 1_000_000).toInt()); save()
            },
            valueRange = 2f..20f,
            steps = 17
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 音频模式 =====
        SectionTitle("收音模式")
        AudioMode.entries.forEach { mode ->
            FilterChip(
                selected = params.audioMode == mode,
                onClick = { params = params.copy(audioMode = mode); save() },
                label = { Text(mode.label) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        // 音量平衡（仅混音/麦克风模式可调）
        if (params.audioMode != AudioMode.SYSTEM) {
            Text("人声音量：${params.micVolume}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = params.micVolume.toFloat(),
                onValueChange = { params = params.copy(micVolume = it.toInt()); save() },
                valueRange = 0f..100f
            )
        }
        if (params.audioMode == AudioMode.MIX) {
            Text("系统音量：${params.systemVolume}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = params.systemVolume.toFloat(),
                onValueChange = { params = params.copy(systemVolume = it.toInt()); save() },
                valueRange = 0f..100f
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 水印 =====
        SectionTitle("水印（默认关闭）")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("添加自定义水印", Modifier.weight(1f))
            Switch(
                checked = params.enableWatermark,
                onCheckedChange = { params = params.copy(enableWatermark = it); save() }
            )
        }
        if (params.enableWatermark) {
            OutlinedTextField(
                value = params.watermarkText,
                onValueChange = { params = params.copy(watermarkText = it); save() },
                label = { Text("水印文字") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        // ===== 倒计时 =====
        SectionTitle("倒计时录制")
        Row {
            listOf(0, 3, 5).forEach { sec ->
                FilterChip(
                    selected = params.countdown == sec,
                    onClick = { params = params.copy(countdown = sec); save() },
                    label = { Text(if (sec == 0) "无" else "${sec}s") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
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
