//
// video/frame_processor.h - 画面区域裁剪与水印（GPU/EGL 管线）
//
#ifndef SP_FRAME_PROCESSOR_H
#define SP_FRAME_PROCESSOR_H

#include <cstdint>

namespace sp {

struct FrameRect {
    int left = 0, top = 0, right = 0, bottom = 0;
};

/**
 * 区域录制 + 水印叠加。
 * 设计要点（技术文档 §3.3）：
 *   不要在 Kotlin 层把画面读成 Bitmap 裁剪（内存/延迟极高）；
 *   在 C++ 层用 EGL + OpenGL ES 把虚拟显示 Surface 采样到纹理，
 *   按区域矩形裁剪后渲染到编码器输入 Surface，同时可叠加文字水印。
 *
 * 说明：本文件提供完整接口与初始化/状态管理框架，GLSL 着色器与
 * 帧处理主循环为可运行的实现骨架，供 NDK 环境直接补齐 GL 上下文。
 */
class FrameProcessor {
public:
    FrameProcessor() = default;
    ~FrameProcessor() = default;

    // 设置裁剪区域（相对输入全屏画面坐标）
    void setRegion(const FrameRect& rect);
    void clearRegion();

    // 设置水印
    void setWatermark(bool enabled, const char* text);

    // 初始化 GL 上下文与着色器（返回 false 表示 GPU 不可用）
    bool init();

    // 处理一帧：从 input surface 采样 → 裁剪 → 水印 → 输出到编码 surface
    // 实际由 video_encoder 的 surface 输入管线驱动
    void processFrame();

    // 释放 GL 资源
    void release();

    bool isRegionEnabled() const { return regionEnabled_; }

private:
    FrameRect region_{};
    bool regionEnabled_ = false;
    bool watermarkEnabled_ = false;
    char watermarkText_[64] = {0};
    bool glReady_ = false;

    // GL 资源占位（EGLDisplay/Context、Program、纹理、VBO 等）
    void* eglDisplay_ = nullptr;
    void* glProgram_ = nullptr;
};

} // namespace sp

#endif // SP_FRAME_PROCESSOR_H
