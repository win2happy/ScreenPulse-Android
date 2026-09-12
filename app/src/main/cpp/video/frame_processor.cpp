//
// video/frame_processor.cpp - 区域裁剪与水印（EGL/GLES 管线框架）
//
#include "video/frame_processor.h"
#include "common/log.h"
#include <cstring>

namespace sp {

void FrameProcessor::setRegion(const FrameRect& rect) {
    region_ = rect;
    regionEnabled_ = (rect.right > rect.left) && (rect.bottom > rect.top);
}

void FrameProcessor::clearRegion() {
    regionEnabled_ = false;
    region_ = {};
}

void FrameProcessor::setWatermark(bool enabled, const char* text) {
    watermarkEnabled_ = enabled;
    if (text) {
        std::strncpy(watermarkText_, text, sizeof(watermarkText_) - 1);
        watermarkText_[sizeof(watermarkText_) - 1] = '\0';
    }
}

bool FrameProcessor::init() {
    // 实际实现：eglGetDisplay / eglInitialize / eglCreateContext / 编译 GLSL 着色器
    // 这里返回骨架状态；NDK 环境编译时补齐 EGL 上下文与 shader。
    LOGI("FrameProcessor::init - GL pipeline placeholder");
    glReady_ = false; // 无 EGL 环境时降级为直通（不裁剪），见 JNI 桥降级逻辑
    return glReady_;
}

void FrameProcessor::processFrame() {
    // 主循环骨架：绑定纹理 → 用裁剪矩形构建顶点坐标 → 绘制 → 渲染到编码 surface
    // 由 video_encoder 的 surface 输入管线在每帧回调时调用。
    if (!glReady_) return;
}

void FrameProcessor::release() {
    // 释放 GL 资源
    glReady_ = false;
}

} // namespace sp
