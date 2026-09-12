package com.screenpulse.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ============ 主题模式 ============
enum class ThemeMode(val value: Int) {
    FOLLOW_SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromValue(v: Int): ThemeMode = when (v) {
            1 -> LIGHT
            2 -> DARK
            else -> FOLLOW_SYSTEM
        }
    }
}

// ============ 音频模式 ============
enum class AudioMode(val value: Int, val label: String) {
    SYSTEM(0, "仅系统声音"),
    MIC(1, "仅麦克风"),
    MIX(2, "系统+麦克风混音");

    companion object {
        fun fromValue(v: Int): AudioMode = when (v) {
            1 -> MIC
            2 -> MIX
            else -> SYSTEM
        }
    }
}

// ============ 录制参数 ============
data class RecordParams(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val bitrate: Int = 8_000_000,          // 8 Mbps
    val audioMode: AudioMode = AudioMode.SYSTEM,
    val micVolume: Int = 100,              // 人声音量百分比
    val systemVolume: Int = 100,           // 系统音量百分比
    val enableWatermark: Boolean = false,
    val watermarkText: String = "ScreenPulse",
    val countdown: Int = 3                 // 0 / 3 / 5 秒
)

// ============ 悬浮窗状态 ============
data class FloatingPrefs(
    val enabled: Boolean = true,
    val x: Int = 0,
    val y: Int = 0
)

// ============ 压缩档位 ============
enum class CompressLevel(val value: Int, val label: String) {
    FAST(0, "极速压缩"),
    BALANCED(1, "均衡压缩"),
    LOSSLESS(2, "高清无损");

    companion object {
        fun fromValue(v: Int): CompressLevel = when (v) {
            0 -> FAST
            2 -> LOSSLESS
            else -> BALANCED
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "screenpulse_prefs")

/**
 * 轻量 DataStore 持久层：主题模式、录制参数、悬浮窗、快捷键、压缩档位。
 * 全部本地存储，无任何网络上报。
 */
class AppPreferencesRepo(private val ctx: Context) {

    private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
    private val KEY_WIDTH = intPreferencesKey("rec_width")
    private val KEY_HEIGHT = intPreferencesKey("rec_height")
    private val KEY_FPS = intPreferencesKey("rec_fps")
    private val KEY_BITRATE = intPreferencesKey("rec_bitrate")
    private val KEY_AUDIO_MODE = intPreferencesKey("audio_mode")
    private val KEY_MIC_VOL = intPreferencesKey("mic_volume")
    private val KEY_SYS_VOL = intPreferencesKey("sys_volume")
    private val KEY_WM_ENABLED = booleanPreferencesKey("wm_enabled")
    private val KEY_WM_TEXT = androidx.datastore.preferences.core.stringPreferencesKey("wm_text")
    private val KEY_COUNTDOWN = intPreferencesKey("countdown")
    private val KEY_FLOAT_ENABLED = booleanPreferencesKey("float_enabled")
    private val KEY_COMPRESS_LEVEL = intPreferencesKey("compress_level")
    private val KEY_SHORTCUT_ENABLED = booleanPreferencesKey("shortcut_enabled")

    // ---- 主题 ----
    val themeMode: Flow<ThemeMode> = ctx.dataStore.data.map { p ->
        ThemeMode.fromValue(p[KEY_THEME_MODE] ?: ThemeMode.FOLLOW_SYSTEM.value)
    }
    suspend fun setThemeMode(mode: ThemeMode) {
        ctx.dataStore.edit { it[KEY_THEME_MODE] = mode.value }
    }

    // ---- 录制参数 ----
    val recordParams: Flow<RecordParams> = ctx.dataStore.data.map { p ->
        RecordParams(
            width = p[KEY_WIDTH] ?: 1080,
            height = p[KEY_HEIGHT] ?: 1920,
            fps = p[KEY_FPS] ?: 30,
            bitrate = p[KEY_BITRATE] ?: 8_000_000,
            audioMode = AudioMode.fromValue(p[KEY_AUDIO_MODE] ?: 0),
            micVolume = p[KEY_MIC_VOL] ?: 100,
            systemVolume = p[KEY_SYS_VOL] ?: 100,
            enableWatermark = p[KEY_WM_ENABLED] ?: false,
            watermarkText = p[KEY_WM_TEXT] ?: "ScreenPulse",
            countdown = p[KEY_COUNTDOWN] ?: 3
        )
    }

    suspend fun saveRecordParams(params: RecordParams) {
        ctx.dataStore.edit {
            it[KEY_WIDTH] = params.width
            it[KEY_HEIGHT] = params.height
            it[KEY_FPS] = params.fps
            it[KEY_BITRATE] = params.bitrate
            it[KEY_AUDIO_MODE] = params.audioMode.value
            it[KEY_MIC_VOL] = params.micVolume
            it[KEY_SYS_VOL] = params.systemVolume
            it[KEY_WM_ENABLED] = params.enableWatermark
            it[KEY_WM_TEXT] = params.watermarkText
            it[KEY_COUNTDOWN] = params.countdown
        }
    }

    // ---- 悬浮窗 ----
    val floatingEnabled: Flow<Boolean> = ctx.dataStore.data.map { p ->
        p[KEY_FLOAT_ENABLED] ?: true
    }
    suspend fun setFloatingEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[KEY_FLOAT_ENABLED] = enabled }
    }

    // ---- 压缩档位 ----
    val compressLevel: Flow<CompressLevel> = ctx.dataStore.data.map { p ->
        CompressLevel.fromValue(p[KEY_COMPRESS_LEVEL] ?: CompressLevel.BALANCED.value)
    }
    suspend fun setCompressLevel(level: CompressLevel) {
        ctx.dataStore.edit { it[KEY_COMPRESS_LEVEL] = level.value }
    }

    // ---- 快捷键 ----
    val shortcutEnabled: Flow<Boolean> = ctx.dataStore.data.map { p ->
        p[KEY_SHORTCUT_ENABLED] ?: true
    }
    suspend fun setShortcutEnabled(enabled: Boolean) {
        ctx.dataStore.edit { it[KEY_SHORTCUT_ENABLED] = enabled }
    }
}
