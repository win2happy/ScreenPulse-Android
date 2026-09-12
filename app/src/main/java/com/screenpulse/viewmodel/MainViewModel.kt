package com.screenpulse.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenpulse.repository.AppPreferencesRepo
import com.screenpulse.repository.CompressLevel
import com.screenpulse.repository.RecordParams
import com.screenpulse.repository.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 录屏全局状态机：空闲 / 录制 / 暂停 */
enum class RecordState { IDLE, RECORDING, PAUSED }

/**
 * 全局主 ViewModel：主题模式、录制参数、悬浮窗开关、压缩档位、快捷键。
 * 数据变更经 DataStore Flow 驱动 Compose 重组，主题切换即时生效无需重启。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppPreferencesRepo(app)

    val themeMode: StateFlow<ThemeMode> = repo.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.FOLLOW_SYSTEM)

    val recordParams: StateFlow<RecordParams> = repo.recordParams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordParams())

    val floatingEnabled: StateFlow<Boolean> = repo.floatingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val compressLevel: StateFlow<CompressLevel> = repo.compressLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompressLevel.BALANCED)

    val shortcutEnabled: StateFlow<Boolean> = repo.shortcutEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun changeTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun saveParams(p: RecordParams) = viewModelScope.launch { repo.saveRecordParams(p) }
    fun setFloatingEnabled(enabled: Boolean) = viewModelScope.launch { repo.setFloatingEnabled(enabled) }
    fun setCompressLevel(level: CompressLevel) = viewModelScope.launch { repo.setCompressLevel(level) }
    fun setShortcutEnabled(enabled: Boolean) = viewModelScope.launch { repo.setShortcutEnabled(enabled) }
}
