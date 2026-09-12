//
// video/video_encoder.h - 视频 + 音频编码与 MP4 封装
//
// 使用 NDK 的 AMediaCodec（H.264 硬编码优先）+ AMediaMuxer 封装 MP4。
// 视频输入为 MediaProjection 虚拟显示的 Surface（画面区域裁剪在 frame_processor 完成）。
// 音频输入为 C++ 侧混音降噪后的 PCM（见 audio 模块），编码为 AAC。
//
#ifndef SP_VIDEO_ENCODER_H
#define SP_VIDEO_ENCODER_H

#include <jni.h>
#include <android/native_window.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaMuxer.h>
#include <cstdint>
#include <string>
#include <mutex>
#include <thread>
#include <atomic>
#include <deque>
#include <vector>

namespace sp {

struct VideoParams {
    int width = 1080;
    int height = 1920;
    int fps = 30;
    int bitrate = 8'000'000;
};

struct RegionRect {
    int left = 0, top = 0, right = 0, bottom = 0;
    bool enabled = false;   // false = 全屏
};

struct AudioMixState {
    int mode = 0;           // AudioMode
    int micVolume = 100;    // 人声音量 %
    int sysVolume = 100;    // 系统音量 %
};

/**
 * 录制核心：负责
 *  1) 创建视频编码器输入 Surface（供 MediaProjection 渲染）
 *  2) 消费编码后的 H.264 / AAC 数据写入 MP4
 *  3) 暂停/继续/停止，资源完整释放
 */
class VideoEncoder {
public:
    VideoEncoder() = default;
    ~VideoEncoder() { destroy(); }

    bool init(const VideoParams& params, const RegionRect& region);
    void setAudioMode(const AudioMixState& audio);
    void setWatermark(bool enabled, const std::string& text);

    // 创建视频编码器输入 surface（交给上层 MediaProjection）
    ANativeWindow* createInputSurface();

    bool start();
    bool pause();
    bool resume();
    std::string stop();   // 返回 MP4 输出路径（空表示失败）

    // 音频 PCM 入口：系统/麦克风两路（PCM16, 48kHz 立体声）
    void pushSystemPcm(const int16_t* data, size_t frames);
    void pushMicPcm(const int16_t* data, size_t frames);

    void destroy();

    static VideoEncoder& instance();

private:
    void videoLoop();
    void audioLoop();
    void tryStartMuxer();
    int64_t getNowUs();

    bool setupMuxer(const std::string& path);

    // ---- 视频 ----
    AMediaCodec* videoCodec_ = nullptr;
    ANativeWindow* inputSurface_ = nullptr;
    VideoParams vparams_;

    // ---- 音频 ----
    AMediaCodec* audioCodec_ = nullptr;
    AudioMixState audio_;

    // 两路 PCM 环形缓冲（字节）
    std::deque<int16_t> sysBuf_;
    std::deque<int16_t> micBuf_;
    std::mutex bufMutex_;

    // ---- 封装 ----
    AMediaMuxer* muxer_ = nullptr;
    int muxerFd_ = -1;
    ssize_t videoTrack_ = -1;
    ssize_t audioTrack_ = -1;
    bool muxerStarted_ = false;
    bool hasVideoTrack_ = false;
    bool hasAudioTrack_ = false;
    std::string outputPath_;

    // ---- 状态 ----
    std::atomic<bool> running_{false};
    std::atomic<bool> paused_{false};
    std::atomic<bool> done_{false};
    std::thread videoThread_;
    std::thread audioThread_;

    // 水印
    bool watermarkOn_ = false;
    std::string watermarkText_;
};

} // namespace sp

#endif // SP_VIDEO_ENCODER_H
