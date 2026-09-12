package com.screenpulse.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.screenpulse.Util
import com.screenpulse.jni.ScreenRecordNative
import com.screenpulse.repository.CompressLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager 后台视频压缩：
 * - App 退后台任务不被杀
 * - 三档压缩（极速 / 均衡 / 高清无损），优先 C++ 硬编转码，失败降级 FFmpeg
 * - 输出为 "_compressed" 后缀新文件，完成后删除源文件并更新文件列表
 */
class VideoCompressWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val input = inputData.getString(KEY_INPUT) ?: return Result.failure()
        val level = inputData.getInt(KEY_LEVEL, CompressLevel.BALANCED.value)
        val src = File(input)
        if (!src.exists()) return Result.failure()

        val out = File(
            src.parentFile,
            src.nameWithoutExtension + "_compressed.mp4"
        )

        val code = runCatching {
            ScreenRecordNative.doCompress(src.absolutePath, out.absolutePath, level)
        }.getOrDefault(-1)

        return if (code == 0 && out.exists()) {
            // 压缩成功：删除源文件，保留压缩后文件
            src.delete()
            Result.success()
        } else {
            out.delete()
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "screenpulse_compress"
        private const val KEY_INPUT = "input"
        private const val KEY_LEVEL = "level"

        /** 触发后台压缩任务 */
        fun enqueue(context: Context, file: File, level: CompressLevel) {
            val data = Data.Builder()
                .putString(KEY_INPUT, file.absolutePath)
                .putInt(KEY_LEVEL, level.value)
                .build()
            val request = OneTimeWorkRequestBuilder<VideoCompressWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME + "_" + file.name,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

/** WorkManager 自定义 WorkerFactory，使 VideoCompressWorker 可用构造注入 */
class RecordWorkerFactory(private val context: Context) : androidx.work.WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): androidx.work.ListenableWorker? {
        return when (workerClassName) {
            VideoCompressWorker::class.java.name ->
                VideoCompressWorker(appContext, workerParameters)
            else -> null
        }
    }
}
