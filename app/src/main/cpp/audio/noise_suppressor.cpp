//
// audio/noise_suppressor.cpp - 轻量降噪实现
//
#include "audio/noise_suppressor.h"
#include <cmath>

namespace sp {

NoiseSuppressor::NoiseSuppressor(int sampleRate) : sampleRate_(sampleRate) {
    // 高通截止 ~80Hz：去除低频底噪、风声
    const double cutoff = 80.0;
    const double dt = 1.0 / (double)sampleRate_;
    const double rc = 1.0 / (2.0 * M_PI * cutoff);
    alpha_ = (float)(dt / (rc + dt));
    // 噪声门限（相对满幅的 0.5%）
    noiseFloor_ = 32768.0f * 0.005f;
    reset();
}

void NoiseSuppressor::reset() {
    x1_[0] = x1_[1] = 0;
    y1_[0] = y1_[1] = 0;
    prev_[0] = prev_[1] = 0;
    dcAcc_[0] = dcAcc_[1] = 0;
}

void NoiseSuppressor::process(int16_t* data, size_t frames) {
    for (size_t i = 0; i < frames; ++i) {
        for (int ch = 0; ch < 2; ++ch) {
            size_t idx = i * 2 + ch;
            float x = (float)data[idx];

            // 1) 去直流（滑动平均）
            dcAcc_[ch] += (x - dcAcc_[ch]) * 0.0005f;
            x -= dcAcc_[ch];

            // 2) 高通滤波
            float y = alpha_ * (y1_[ch] + x - x1_[ch]);
            x1_[ch] = x;
            y1_[ch] = y;

            // 3) 噪声门限（软门限，保留呼吸起伏）
            float mag = std::fabs(y);
            float g = 1.0f;
            if (mag < noiseFloor_) {
                // 弱于阈值的按比例衰减，不硬切避免"水声"
                g = mag / noiseFloor_;
            }
            float out = y * g;
            if (out > 32767.0f) out = 32767.0f;
            if (out < -32768.0f) out = -32768.0f;
            data[idx] = (int16_t)out;
        }
    }
}

} // namespace sp
