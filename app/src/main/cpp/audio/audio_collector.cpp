//
// audio/audio_collector.cpp - 转发两路 PCM 到编码器缓冲
//
#include "audio/audio_collector.h"
#include "video/video_encoder.h"
#include "common/constants.h"

namespace sp {

void AudioCollector::pushSystem(const int16_t* data, size_t frames) {
    VideoEncoder::instance().pushSystemPcm(data, frames);
}

void AudioCollector::pushMic(const int16_t* data, size_t frames) {
    VideoEncoder::instance().pushMicPcm(data, frames);
}

void AudioCollector::reset() {
    // 由 VideoEncoder::destroy 统一清理缓冲
}

} // namespace sp
