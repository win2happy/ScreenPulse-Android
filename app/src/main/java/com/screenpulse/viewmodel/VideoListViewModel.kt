package com.screenpulse.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenpulse.repository.VideoItem
import com.screenpulse.repository.VideoRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = VideoRepo(app)
    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repo.watchVideos().collect { _videos.value = it }
        }
    }

    fun delete(item: VideoItem) {
        repo.delete(item)
        refresh()
    }

    fun rename(item: VideoItem, newName: String) {
        repo.rename(item, newName)
        refresh()
    }

    fun shareIntent(item: VideoItem) = repo.shareIntent(item)
}
