package com.screenpulse.repository

import android.content.Context
import com.screenpulse.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/** 本地录屏文件条目 */
data class VideoItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val lastModified: Long
) {
    val formattedSize: String get() = formatSize(sizeBytes)
    val formattedDuration: String get() = formatDuration(durationMs)

    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1073741824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1048576.0)
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }

        fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s)
            else "%02d:%02d".format(m, s)
        }
    }
}

/**
 * 视频文件仓库：扫描本地 ScreenPulse 目录中的 MP4，读取媒体信息。
 * 采用 MediaMetadataRetriever 获取时长/分辨率；无第三方库。
 */
class VideoRepo(private val ctx: Context) {

    fun watchVideos(): Flow<List<VideoItem>> = flow {
        emit(scan())
    }.flowOn(Dispatchers.IO)

    private fun scan(): List<VideoItem> {
        val dir = Util.recordsDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension.equals("mp4", true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f -> readMeta(f) }
            ?.filterNotNull() ?: emptyList()
    }

    private fun readMeta(file: File): VideoItem? {
        return try {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val dur = mmr.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val w = mmr.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val h = mmr.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            mmr.release()
            VideoItem(
                path = file.absolutePath,
                name = file.name,
                sizeBytes = file.length(),
                durationMs = dur,
                width = w,
                height = h,
                lastModified = file.lastModified()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun delete(item: VideoItem): Boolean = File(item.path).delete()

    fun rename(item: VideoItem, newName: String): Boolean {
        val f = File(item.path)
        val newFile = File(f.parentFile, newName.ensureMp4())
        return f.renameTo(newFile)
    }

    private fun String.ensureMp4(): String =
        if (endsWith(".mp4", true)) this else "$this.mp4"

    fun shareIntent(item: VideoItem): android.content.Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", File(item.path)
        )
        return android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
