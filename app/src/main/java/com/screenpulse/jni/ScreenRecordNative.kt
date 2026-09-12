package com.screenpulse.jni

/**
 * JNI 桥接接口：上层只传递控制参数，绝不传递大块图像/音频原始数据。
 * 画面帧、PCM 混音、区域裁剪全部在 C++/NDK 层完成，规避 Kotlin 层 GC 卡顿。
 * 对应 C++ 端实现见 src/main/cpp/jni_bridge.cpp（注册函数与 Kotlin 方法一一对应）。
 */
object ScreenRecordNative {

    init {
        System.loadLibrary("screenpulse_core")
    }

    /** 初始化录制器（加载编解码器、音频采集器、帧缓冲池） */
    external fun initRecorder(): Int

    /** 设置视频参数：宽、高、帧率、码率(bps) */
    external fun setVideoParam(width: Int, height: Int, fps: Int, bitrate: Int): Int

    /** 设置区域录制裁剪矩形（屏幕坐标，超出范围自动裁剪到屏幕内） */
    external fun setRegionCrop(left: Int, top: Int, right: Int, bottom: Int): Int

    /** 设置音频模式：0=仅系统，1=仅麦克风，2=混音；后两个为人声/系统音量(0-100) */
    external fun setAudioMode(mode: Int, micVolume: Int, sysVolume: Int): Int

    /** C++ 层通过 MediaCodec 创建编码输入 Surface（虚拟显示渲染目标） */
    external fun createInputSurface(): android.view.Surface?

    /** 推送系统音频 PCM（DirectByteBuffer 零拷贝，PCM16 48kHz 立体声） */
    external fun pushSystemPcm(buffer: java.nio.ByteBuffer)

    /** 推送麦克风 PCM（C++ 层就地降噪后混音） */
    external fun pushMicPcm(buffer: java.nio.ByteBuffer)

    /** 开始编码：传入 MediaProjection 创建的虚拟显示 Surface 句柄 */
    external fun startEncode(surface: Any): Int

    /** 暂停编码（保留资源） */
    external fun pauseEncode(): Int

    /** 继续编码 */
    external fun resumeEncode(): Int

    /**
     * 停止编码并封装 MP4。
     * @return 输出文件绝对路径（失败返回 null）
     */
    external fun stopEncode(): String?

    /**
     * 后台视频压缩（WorkManager 调用）。
     * @param inputPath 源 MP4
     * @param outputPath 输出 MP4
     * @param level 0=极速 1=均衡 2=高清无损
     * @return 0 成功，非 0 失败
     */
    external fun doCompress(inputPath: String, outputPath: String, level: Int): Int

    /** 释放全部音视频资源 */
    external fun releaseRecorder(): Int

    /** 原生库是否加载成功 */
    val isLoaded: Boolean
        get() = runCatching { loadSuccess() }.getOrDefault(false)

    private external fun loadSuccess(): Boolean
}
