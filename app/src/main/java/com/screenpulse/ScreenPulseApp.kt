package com.screenpulse

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import androidx.work.WorkManager
import com.screenpulse.util.RecordWorkerFactory
/**
 * 应用入口：初始化通知渠道、WorkManager 自定义工厂。
 * 本应用零网络、零广告、零埋点，仅本地存储。
 */
class ScreenPulseApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration =
        Configuration.Builder()
            .setWorkerFactory(RecordWorkerFactory(applicationContext))
            .build()

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        // 录屏前台服务通知
        nm.createNotificationChannel(
            NotificationChannel(
                Util.CHANNEL_RECORD,
                "录屏服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ScreenPulse 正在录制时的常驻通知"
                setShowBadge(false)
            }
        )
        // 压缩进度通知
        nm.createNotificationChannel(
            NotificationChannel(
                Util.CHANNEL_COMPRESS,
                "视频压缩",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "后台视频压缩任务进度"
            }
        )
    }
}

/** 全局常量与通道名 */
object Util {
    const val CHANNEL_RECORD = "channel_record"
    const val CHANNEL_COMPRESS = "channel_compress"
    const val NOTIF_RECORD_ID = 1001
    const val NOTIF_COMPRESS_ID = 1002

    /** 录屏文件根目录（应用专属外部目录，免存储权限，符合分区存储） */
    fun recordsDir(): java.io.File = java.io.File(
        android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_MOVIES
        ), "ScreenPulse"
    ).apply { mkdirs() }
}
