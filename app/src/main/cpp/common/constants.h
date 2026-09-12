//
// common/constants.h - 全局常量与音频/编码参数
//
#ifndef SP_CONSTANTS_H
#define SP_CONSTANTS_H

namespace sp {

// 采样率 / 声道（Android 内录与麦克风统一到 48kHz 立体声）
constexpr int kSampleRate = 48000;
constexpr int kChannels   = 2;
constexpr int kBytesPerSample = 2; // PCM16

// 音频模式
enum AudioMode {
    kAudioSystemOnly = 0,  // 仅系统声音
    kAudioMicOnly    = 1,  // 仅麦克风
    kAudioMix        = 2,  // 系统 + 麦克风混音
};

// 压缩档位
enum CompressLevel {
    kCompressFast     = 0, // 极速
    kCompressBalanced = 1, // 均衡
    kCompressLossless = 2, // 高清无损
};

// 返回码约定：0 = 成功，非 0 = 失败
constexpr int kOk = 0;
constexpr int kErr = -1;

} // namespace sp

#endif // SP_CONSTANTS_H
