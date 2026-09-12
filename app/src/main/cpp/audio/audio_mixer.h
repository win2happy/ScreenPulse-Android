//
// audio/audio_mixer.h - 双音源混音与音量平衡
//
#ifndef SP_AUDIO_MIXER_H
#define SP_AUDIO_MIXER_H

#include <cstdint>
#include <vector>
#include "video/video_encoder.h"

namespace sp {

/**
 * 混音器：把系统音频 + 麦克风两路 PCM16 立体声，
 * 按音频模式与音量平衡合成一路输出。
 * - 仅系统：只取系统路，应用系统音量
 * - 仅麦克风：只取麦克风路，应用人声音量
 * - 混音：两路叠加，自动归一化防削顶，各自音量增益
 */
class AudioMixer {
public:
    /**
     * @param sys 系统 PCM（可为空）
     * @param mic 麦克风 PCM（可为空）
     * @param out 输出缓冲（预分配）
     * @param state 音频模式与音量
     * @param maxSamples 输出最大采样数（帧数 × 声道数）
     * @return 实际输出采样数
     */
    static int mix(const std::vector<int16_t>& sys,
                   const std::vector<int16_t>& mic,
                   std::vector<int16_t>& out,
                   const AudioMixState& state,
                   size_t maxSamples);
};

} // namespace sp

#endif // SP_AUDIO_MIXER_H
