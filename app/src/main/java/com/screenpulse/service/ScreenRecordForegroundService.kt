package com.screenpulse.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.screenpulse.R
import com.screenpulse.Util
import com.screenpulse.jni.ScreenRecordNative
import com.screenpulse.repository.AppPreferencesRepo
import com.screenpulse.repository.RecordParams
import com.screenpulse.viewmodel.RecordState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 录屏前台服务：
 * - 前台服务保活（mediaProjection 类型），防系统回收
 * - MediaProjection 创建虚拟显示，Surface 由 C++/NDK 层 MediaCodec 提供并直接编码，
 *   画面帧、PCM 混音、区域裁剪全部在 C++ 层完成，规避 Kotlin 层 GC 卡顿
 * - 支持 30/60fps、全屏/区域录制、三种音频模式
 * - 暂停/继续/结束完整释放资源，杜绝内存泄漏
 */
class ScreenRecordForegroundService : Service() {

    companion object {
        private const val ACTION_START = "com.screenpulse.action.START"
        private const val ACTION_STOP = "com.screenpulse.action.STOP"
        private const val ACTION_PAUSE = "com.screenpulse.action.PAUSE"
        private const val ACTION_RESUME = "com.screenpulse.action.RESUME"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_DATA = "extra_data"

        /** 全局录屏状态权威来源（供 UI / 悬浮窗 / 通知读取） */
        @Volatile
        var currentState: RecordState = RecordState.IDLE
            private set

        /** 最近一次录制输出路径 */
        @Volatile
        var lastRecordPath: String? = null
            private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, ScreenRecordForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenRecordForegroundService::class.java).setAction(ACTION_STOP)
            )
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, ScreenRecordForegroundService::class.java).setAction(ACTION_PAUSE)
            )
        }

        fun resume(context: Context) {
            context.startService(
                Intent(context, ScreenRecordForegroundService::class.java).setAction(ACTION_RESUME)
            )
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recordThread: HandlerThread? = null
    private var recordHandler: Handler? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var params: RecordParams = RecordParams()
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecord(intent)
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> if (isRecording) pauseRecording()
            ACTION_RESUME -> if (currentState == RecordState.PAUSED) resumeRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecord(intent: Intent) {
        startForegroundCompat()
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        if (data == null || resultCode == 0) {
            stopSelf()
            return
        }
        scope.launch {
            params = AppPreferencesRepo(this@ScreenRecordForegroundService)
                .recordParams.first()
            beginRecord(resultCode, data)
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification("正在启动录制…", RecordState.RECORDING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Util.NOTIF_RECORD_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(Util.NOTIF_RECORD_ID, notification)
        }
    }

    private fun beginRecord(resultCode: Int, data: Intent) {
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { releaseAll() }
            }, null)

            // ---- 初始化 C++ 音视频核心 ----
            if (ScreenRecordNative.initRecorder() != 0) { stopSelf(); return }

            // Android 8-9 无系统内录：非"仅麦克风"自动降级
            val audioMode = if (params.audioMode.value != 0 && Build.VERSION.SDK_INT < 29)
                1 else params.audioMode.value

            ScreenRecordNative.setVideoParam(params.width, params.height, params.fps, params.bitrate)
            ScreenRecordNative.setRegionCrop(0, 0, params.width, params.height)
            ScreenRecordNative.setAudioMode(audioMode, params.micVolume, params.systemVolume)

            // C++ 层通过 MediaCodec 创建输入 Surface
            val surface: Surface = ScreenRecordNative.createInputSurface() ?: run {
                stopSelf(); return
            }

            recordThread = HandlerThread("ScreenPulseRecord").apply { start() }
            recordHandler = Handler(recordThread!!.looper)

            // 全屏镜像虚拟显示
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenPulseDisplay",
                params.width, params.height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )

            if (ScreenRecordNative.startEncode(surface) != 0) {
                stopSelf(); return
            }

            isRecording = true
            currentState = RecordState.RECORDING
            updateNotification("正在录制 ${params.width}x${params.height} ${params.fps}fps · ${params.audioMode.label}")
        } catch (e: Exception) {
            e.printStackTrace()
            releaseAll()
        }
    }

    private fun pauseRecording() {
        if (ScreenRecordNative.pauseEncode() == 0) {
            currentState = RecordState.PAUSED
            updateNotification("录制已暂停")
        }
    }

    private fun resumeRecording() {
        if (ScreenRecordNative.resumeEncode() == 0) {
            currentState = RecordState.RECORDING
            updateNotification("正在录制 ${params.width}x${params.height} ${params.fps}fps")
        }
    }

    private fun stopRecording() {
        if (!isRecording) { stopSelf(); return }
        try {
            lastRecordPath = ScreenRecordNative.stopEncode()
            ScreenRecordNative.releaseRecorder()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            releaseAll()
        }
        // 触发 WorkManager 后台压缩（不阻塞 UI）
        lastRecordPath?.let { path: String ->
            val level = runCatching {
                runBlocking { AppPreferencesRepo(this@ScreenRecordForegroundService).compressLevel.first() }
            }.getOrDefault(com.screenpulse.repository.CompressLevel.BALANCED)
            com.screenpulse.util.VideoCompressWorker.enqueue(this, File(path), level)
        }
    }

    private fun releaseAll() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { ScreenRecordNative.releaseRecorder() } catch (_: Exception) {}
        recordThread?.quitSafely()
        recordThread = null
        virtualDisplay = null
        mediaProjection = null
        currentState = RecordState.IDLE
        isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Util.NOTIF_RECORD_ID, buildNotification(text, currentState))
    }

    private fun buildNotification(text: String, state: RecordState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.screenpulse.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ScreenRecordForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Util.CHANNEL_RECORD)
            .setContentTitle("ScreenPulse 瞬录")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_stop, "停止", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAll()
    }
}
