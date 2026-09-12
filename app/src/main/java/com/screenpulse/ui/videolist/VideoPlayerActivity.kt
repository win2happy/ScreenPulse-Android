package com.screenpulse.ui.videolist

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.screenpulse.ui.theme.ScreenPulseTheme
import java.io.File

/**
 * 视频预览页：使用 ExoPlayer 播放本地录屏文件。
 */
class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra("path") ?: run { finish(); return }
        setContent {
            ScreenPulseTheme(com.screenpulse.repository.ThemeMode.FOLLOW_SYSTEM) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VideoPlayerScreen(path)
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerScreen(path: String) {
    val context = LocalContext.current
    val uri = rememberUri(path)

    val player = androidx.compose.runtime.remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
            }
        }
    )
}

@androidx.compose.runtime.Composable
private fun rememberUri(path: String): Uri {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(path) {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path)
        )
    }
}
