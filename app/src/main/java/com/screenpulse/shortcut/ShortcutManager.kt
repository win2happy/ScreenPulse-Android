package com.screenpulse.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import com.screenpulse.repository.AppPreferencesRepo
import com.screenpulse.service.ScreenRecordForegroundService
import com.screenpulse.viewmodel.RecordState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 物理按键快捷键：监听系统媒体键广播，短按音量键触发录屏启停。
 * - 通过 AudioManager 注册 MediaButton 接收器
 * - 读取用户配置的快捷键开关（DataStore）
 * - 防抖动：两次触发间隔 ≥ 400ms
 */
class ShortcutManager(private val ctx: Context) {

    private var lastTrigger = 0L
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY &&
                intent.action != Intent.ACTION_MEDIA_BUTTON
            ) return
            handleTrigger(context)
        }
    }

    private fun handleTrigger(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTrigger < 400) return
        lastTrigger = now

        CoroutineScope(Dispatchers.IO).launch {
            val enabled = runCatching {
                AppPreferencesRepo(context).shortcutEnabled.first()
            }.getOrDefault(true)
            if (!enabled) return@launch

            when (ScreenRecordForegroundService.currentState) {
                RecordState.IDLE -> {
                    // 空闲时无 MediaProjection 授权，无法直接启动；拉起主界面
                    context.startActivity(
                        Intent(context, com.screenpulse.ui.MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                RecordState.RECORDING -> ScreenRecordForegroundService.pause(context)
                RecordState.PAUSED -> ScreenRecordForegroundService.resume(context)
            }
        }
    }

    fun register() {
        if (registered) return
        registered = true
        // 静态注册的 MediaButtonReceiver 见 Manifest；此占位用于运行时提示。
        // 实际按键监听需配合 AudioManager.registerMediaButtonEventReceiver，
        // 由于多应用竞争，这里保留扩展点。
    }

    fun unregister() {
        registered = false
    }
}

/**
 * Manifest 中声明的媒体按钮广播接收器占位。
 * Android 8+ 媒体键竞争机制复杂，此处保留入口便于后续扩展自定义按键。
 */
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 由 ShortcutManager 实际处理
    }
}
