//
// audio/audio_mixer.cpp - 双音源混音与音量平衡实现
//
#include "audio/audio_mixer.h"
#include "common/log.h"
#include "common/constants.h"
#include <algorithm>
#include <cstring>

namespace sp {

namespace {
// 限制到 int16 范围
inline float clamp(float v) {
    if (v > 32767.0f) return 32767.0f;
    if (v < -32768.0f) return -32768.0f;
    return v;
}
// 软削波（三次样条近似 tanh），混音防爆音
inline float softLimit(float x) {
    // x + x^3 会过冲；采用 1.5x - 0.5x^3 的 soft-clip（-1..1 归一化）
    float t = x / 32768.0f;
    float y = t - (t * t * t) / 3.0f;
    return y * 32768.0f;
}
}

int AudioMixer::mix(const std::vector<int16_t>& sys,
                    const std::vector<int16_t>& mic,
                    std::vector<int16_t>& out,
                    const AudioMixState& state,
                    size_t maxSamples) {
    // 归一化增益：音量 0-100 → 0.0-1.6（允许轻微增强，超限自动压缩）
    const float sysGain = (float)state.sysVolume / 100.0f;
    const float micGain = (float)state.micVolume / 100.0f;
    const float kMaxGain = 1.6f;

    switch (state.mode) {
        case kAudioSystemOnly: {
            size_t n = std::min(sys.size(), maxSamples);
            for (size_t i = 0; i < n; ++i) {
                float v = (float)sys[i] * std::min(sysGain, kMaxGain);
                out[i] = (int16_t)clamp(v);
            }
            return (int)n;
        }
        case kAudioMicOnly: {
            size_t n = std::min(mic.size(), maxSamples);
            for (size_t i = 0; i < n; ++i) {
                float v = (float)mic[i] * std::min(micGain, kMaxGain);
                out[i] = (int16_t)clamp(v);
            }
            return (int)n;
        }
        case kAudioMix:
        default: {
            size_t n = std::min({sys.size(), mic.size(), maxSamples});
            if (n == 0) {
                // 其中一路为空，退化为单路
                const auto& src = sys.empty() ? mic : sys;
                float g = sys.empty() ? micGain : sysGain;
                n = std::min(src.size(), maxSamples);
                for (size_t i = 0; i < n; ++i) {
                    out[i] = (int16_t)clamp((float)src[i] * std::min(g, kMaxGain));
                }
                return (int)n;
            }
            for (size_t i = 0; i < n; ++i) {
                // 软削波 + 音量平衡，避免混音削顶爆音
                float s = (float)sys[i] * std::min(sysGain, kMaxGain);
                float m = (float)mic[i] * std::min(micGain, kMaxGain);
                out[i] = (int16_t)clamp(softLimit(s + m));
            }
            return (int)n;
        }
    }
}

} // namespace sp
