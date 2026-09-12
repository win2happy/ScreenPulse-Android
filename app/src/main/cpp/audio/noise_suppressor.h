//
// audio/noise_suppressor.h - 人声降噪（轻量、低延迟）
//
#ifndef SP_NOISE_SUPPRESSOR_H
#define SP_NOISE_SUPPRESSOR_H

#include <cstdint>
#include <cstddef>

namespace sp {

/**
 * 轻量降噪处理器，用于麦克风人声：
 *  - 去直流偏置
 *  - 简单高通滤波（去除低频风声/底噪）
 *  - 噪声门限（低于阈值的弱信号静音）
 * 保持低 CPU 与低延迟，适合实时录屏。
 */
class NoiseSuppressor {
public:
    NoiseSuppressor(int sampleRate = 48000);

    // 就地处理一路立体声 PCM16
    void process(int16_t* data, size_t frames);

    void reset();

private:
    int sampleRate_;
    // 每声道高通滤波状态
    float x1_[2] = {0, 0};
    float y1_[2] = {0, 0};
    // 直流移除状态
    float prev_[2] = {0, 0};
    float dcAcc_[2] = {0, 0};
    float alpha_;      // 高通系数
    float noiseFloor_; // 噪声门限
};

} // namespace sp

#endif // SP_NOISE_SUPPRESSOR_H
