package com.screenpulse.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screenpulse.service.ScreenRecordForegroundService
import com.screenpulse.util.PermissionUtils
import com.screenpulse.viewmodel.MainViewModel
import com.screenpulse.viewmodel.RecordState
import kotlinx.coroutines.delay

/**
 * 主首页：大录制按钮 + 状态展示 + 倒计时 + 参数摘要。
 * 点击 → MediaProjection 授权 → 倒计时 → 启动前台服务。
 */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val params by vm.recordParams
    var state by remember { mutableStateOf(ScreenRecordForegroundService.currentState) }
    var countdown by remember { mutableStateOf(0) }
    // 授权成功后等待倒计时完成再启动
    var pendingStart by remember { mutableStateOf<Pair<Int, android.content.Intent>?>(null) }

    // 轮询录屏状态（权威状态在服务中）
    LaunchedEffect(Unit) {
        while (true) {
            state = ScreenRecordForegroundService.currentState
            delay(500)
        }
    }

    // 倒计时结束真正启动
    LaunchedEffect(countdown, pendingStart) {
        if (pendingStart != null && countdown <= 0) {
            val (code, data) = pendingStart!!
            pendingStart = null
            ScreenRecordForegroundService.start(context, code, data)
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            if (params.countdown > 0) {
                countdown = params.countdown
                pendingStart = result.resultCode to result.data!!
            } else {
                ScreenRecordForegroundService.start(context, result.resultCode, result.data!!)
            }
        }
    }

    fun onRecordClick() {
        when (state) {
            RecordState.IDLE ->
                PermissionUtils.launchProjection(context as android.app.Activity)
            RecordState.RECORDING, RecordState.PAUSED ->
                ScreenRecordForegroundService.stop(context)
        }
    }

    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            Text("ScreenPulse 瞬录", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "纯净 · 零广告 · 全功能免费",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))

            // ===== 录制按钮 =====
            Surface(
                onClick = { onRecordClick() },
                shape = CircleShape,
                color = if (state == RecordState.IDLE)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(140.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(32.dp))
                    Icon(
                        if (state == RecordState.IDLE) Icons.Filled.RecordVoiceOver
                        else Icons.Filled.Stop,
                        contentDescription = "录制",
                        tint = if (state == RecordState.IDLE) Color.White
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stateLabel(state),
                        color = if (state == RecordState.IDLE) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stateDesc(state),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state == RecordState.IDLE)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.weight(1f))

            // ===== 参数摘要 =====
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "当前录制参数",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ParamText("${params.width}×${params.height}")
                        ParamText("${params.fps}FPS")
                        ParamText("${params.bitrate / 1_000_000}Mbps")
                        ParamText(params.audioMode.label)
                    }
                }
            }
        }

        // ===== 倒计时覆盖层 =====
        if (countdown > 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "$countdown",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LaunchedEffect(countdown) {
                    delay(1000)
                    countdown--
                }
            }
        }
    }
}

@Composable
private fun ParamText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private fun stateLabel(s: RecordState) = when (s) {
    RecordState.IDLE -> "开始录制"
    RecordState.RECORDING -> "录制中"
    RecordState.PAUSED -> "已暂停"
}

private fun stateDesc(s: RecordState) = when (s) {
    RecordState.IDLE -> "点击开始，支持全屏 / 区域录制"
    RecordState.RECORDING -> "正在录制… 点按悬浮窗可暂停"
    RecordState.PAUSED -> "已暂停，点按继续"
}
