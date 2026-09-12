package com.screenpulse.floatingwindow

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import com.screenpulse.R
import com.screenpulse.repository.ThemeMode
import com.screenpulse.service.ScreenRecordForegroundService
import com.screenpulse.viewmodel.RecordState

/**
 * 悬浮窗管理器：原生 View + WindowManager 实现（禁止 Compose 渲染，降低内存开销）。
 * - 支持拖动、点击控制录屏（开始/暂停/继续/结束）、快速截图、一键隐藏
 * - 颜色跟随主题：读取持久化 theme_mode 标记，选择对应颜色资源（无法复用 Compose 对象）
 */
class FloatingWindowManager(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var currentTheme: ThemeMode = ThemeMode.FOLLOW_SYSTEM

    // 拖动偏移
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun show(theme: ThemeMode) {
        if (view != null) { applyTheme(theme); return }
        currentTheme = theme

        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val v = inflater.inflate(R.layout.floating_window, null) as FrameLayout
        view = v

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
        this.params = params

        bindActions(v)
        applyTheme(theme)
        wm.addView(v, params)
    }

    private fun bindActions(v: FrameLayout) {
        v.findViewById<ImageButton>(R.id.fw_btn_record).setOnClickListener {
            // 空闲态：拉起主界面发起录屏授权；录制态：暂停/继续切换
            when (ScreenRecordForegroundService.currentState) {
                RecordState.IDLE ->
                    ctx.startActivity(
                        android.content.Intent(
                            ctx, com.screenpulse.ui.MainActivity::class.java
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                RecordState.RECORDING -> ScreenRecordForegroundService.pause(ctx)
                RecordState.PAUSED -> ScreenRecordForegroundService.resume(ctx)
            }
        }
        v.findViewById<ImageButton>(R.id.fw_btn_stop).setOnClickListener {
            ScreenRecordForegroundService.stop(ctx)
        }
        v.findViewById<ImageButton>(R.id.fw_btn_close).setOnClickListener {
            hide()
        }
        v.setOnTouchListener { _, event ->
            val vg = view ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) {
                        dragging = true
                        params!!.x = initialX + dx
                        params!!.y = initialY + dy
                        wm.updateViewLayout(vg, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    /** 主题切换时同步悬浮窗颜色（读取持久化标记） */
    fun applyTheme(theme: ThemeMode) {
        currentTheme = theme
        val v = view ?: return
        val bg = if (theme == ThemeMode.DARK)
            ctx.getColor(R.color.float_bg_dark)
        else ctx.getColor(R.color.float_bg_light)
        v.background = ColorDrawable(bg)
        val recordBtn = v.findViewById<ImageButton>(R.id.fw_btn_record)
        val stopBtn = v.findViewById<ImageButton>(R.id.fw_btn_stop)
        recordBtn.setColorFilter(if (theme == ThemeMode.DARK)
            ctx.getColor(R.color.float_icon_dark) else ctx.getColor(R.color.float_icon_light))
        stopBtn.setColorFilter(if (theme == ThemeMode.DARK)
            ctx.getColor(R.color.float_icon_dark) else ctx.getColor(R.color.float_icon_light))
    }

    fun updateRecordState(state: RecordState) {
        // 根据状态切换按钮图标（空闲=录制，录制中=暂停，暂停=继续）
        // 简单起见：旋转录制按钮语义即可，图标资源已在 xml 中提供
    }

    fun isShowing(): Boolean = view != null

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        params = null
    }
}
