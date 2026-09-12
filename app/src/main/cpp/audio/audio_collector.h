//
// audio/audio_collector.h - 音频采集入口
//
// 说明：Android 10+ 系统内录通过 MediaProjection 的 AudioPlaybackCaptureConfiguration
// 由 Kotlin 侧 AudioRecord 采集；麦克风同样由 Kotlin 侧 AudioRecord 采集。
// 两路 PCM 通过 JNI（DirectByteBuffer 零拷贝）推入 C++ 层做混音降噪，
// 避免 Kotlin 大数组拷贝导致的 GC 卡顿。
//
#ifndef SP_AUDIO_COLLECTOR_H
#define SP_AUDIO_COLLECTOR_H

#include <cstdint>

namespace sp {

class AudioCollector {
public:
    // 系统音频 PCM（PCM16, 48kHz 立体声）
    static void pushSystem(const int16_t* data, size_t frames);
    // 麦克风 PCM（PCM16, 48kHz 立体声）
    static void pushMic(const int16_t* data, size_t frames);
    // 清空缓冲
    static void reset();
};

} // namespace sp

#endif // SP_AUDIO_COLLECTOR_H
