package com.screenpulse.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 权限工具：仅申请录屏 / 麦克风 / 悬浮窗三项必要权限，无多余权限。
 */
object PermissionUtils {

    const val REQ_MIC = 100
    const val REQ_PROJECTION = 101
    const val REQ_NOTIF = 102

    fun hasMic(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED

    fun requestMic(activity: Activity) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    /** 校验并启动 MediaProjection 录屏授权弹窗 */
    fun launchProjection(activity: Activity) {
        val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        activity.startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    /** 悬浮窗权限（Android 6+ 需动态授权） */
    fun hasOverlay(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(context)
        else true

    fun requestOverlay(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}"))
        )
    }

    /** Android 13+ 通知权限 */
    fun hasNotification(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
        else true
}
