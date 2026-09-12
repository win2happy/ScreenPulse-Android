package com.screenpulse.ui.videolist

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.screenpulse.repository.VideoItem
import com.screenpulse.viewmodel.VideoListViewModel

/**
 * 录屏文件列表：预览、重命名、删除、一键分享。
 * 使用应用专属目录 + MediaStore/FileProvider 分享，无需存储权限。
 */
@Composable
fun VideoListScreen(vm: VideoListViewModel) {
    val context = LocalContext.current
    val videos by vm.videos.collectAsState()
    var renameTarget by remember { mutableStateOf<VideoItem?>(null) }
    var deleteTarget by remember { mutableStateOf<VideoItem?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("录屏文件", style = MaterialTheme.typography.titleLarge, Modifier.weight(1f))
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, "刷新")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (videos.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("暂无录屏文件", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "录制完成后将显示在这里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(count = videos.size, key = { videos[it].path }) { index ->
                    val item = videos[index]
                    VideoCard(
                        item = item,
                        onPlay = {
                            context.startActivity(
                                Intent(context, com.screenpulse.ui.videolist.VideoPlayerActivity::class.java)
                                    .putExtra("path", item.path)
                            )
                        },
                        onRename = { renameTarget = item; renameText = item.name },
                        onDelete = { deleteTarget = item },
                        onShare = {
                            context.startActivity(
                                Intent.createChooser(vm.shareIntent(item), "分享视频")
                            )
                        }
                    )
                }
            }
        }
    }

    // ===== 重命名对话框 =====
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(target, renameText.substringBeforeLast("."))
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    // ===== 删除对话框 =====
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件") },
            text = { Text("确定删除「${target.name}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target); deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun VideoCard(
    item: VideoItem,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${item.formattedDuration} · ${item.formattedSize} · ${item.width}×${item.height}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, "分享", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Refresh, "重命名")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
