//
// video/video_encoder.cpp - AMediaCodec 硬编码 + AMediaMuxer MP4 封装
//
#include "video/video_encoder.h"
#include "common/log.h"
#include "common/constants.h"
#include "audio/audio_mixer.h"

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaMuxer.h>
#include <android/native_window.h>

#include <cstring>
#include <cstdlib>
#include <ctime>
#include <chrono>
#include <sstream>
#include <iomanip>

namespace sp {

VideoEncoder& VideoEncoder::instance() {
    static VideoEncoder inst;
    return inst;
}

bool VideoEncoder::init(const VideoParams& params, const RegionRect& region) {
    destroy();
    vparams_ = params;
    outputPath_.clear();

    // ============ 视频编码器（H.264 硬编码，surface 输入）============
    AMediaFormat* vfmt = AMediaFormat_new();
    AMediaFormat_setString(vfmt, AMEDIAFORMAT_KEY_MIME, "video/avc");
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_WIDTH, params.width);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_HEIGHT, params.height);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_FRAME_RATE, params.fps);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_BIT_RATE, params.bitrate);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 2);
    AMediaFormat_setInt32(vfmt, AMEDIAFORMAT_KEY_COLOR_FORMAT, 0x7F000789 /*COLOR_FormatSurface*/);
    videoCodec_ = AMediaCodec_createEncoderByType("video/avc");
    if (!videoCodec_) {
        LOGE("Failed to create video encoder");
        AMediaFormat_delete(vfmt);
        return false;
    }
    media_status_t st = AMediaCodec_configure(
        videoCodec_, vfmt, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaFormat_delete(vfmt);
    if (st != AMEDIA_OK) {
        LOGE("video configure failed: %d", st);
        return false;
    }
    inputSurface_ = AMediaCodec_createInputSurface(videoCodec_);
    if (!inputSurface_) {
        LOGE("create input surface failed");
        return false;
    }
    return true;
}

ANativeWindow* VideoEncoder::createInputSurface() {
    return inputSurface_;
}

void VideoEncoder::setAudioMode(const AudioMixState& audio) { audio_ = audio; }
void VideoEncoder::setWatermark(bool enabled, const std::string& text) {
    watermarkOn_ = enabled;
    watermarkText_ = text;
}

bool VideoEncoder::setupMuxer(const std::string& path) {
    // 生成文件名（若为空）
    std::string out = path;
    if (out.empty()) {
        std::time_t t = std::time(nullptr);
        std::tm tm{};
        localtime_r(&t, &tm);
        std::ostringstream ss;
        ss << "/sdcard/Movies/ScreenPulse/rec_"
           << std::put_time(&tm, "%Y%m%d_%H%M%S") << ".mp4";
        out = ss.str();
    }
    // 确保目录存在
    auto pos = out.find_last_of('/');
    if (pos != std::string::npos) {
        std::string dir = out.substr(0, pos);
        std::string cmd = "mkdir -p " + dir;
        ::system(cmd.c_str());
    }
    muxer_ = AMediaMuxer_new(out.c_str(), AMEDIA_MUXER_OUTPUT_FORMAT_MPEG_4);
    outputPath_ = out;
    return muxer_ != nullptr;
}

bool VideoEncoder::start() {
    if (!videoCodec_) return false;

    // 输出路径（停止时由上层传入，这里先用时间戳临时占位）
    setupMuxer(outputPath_);

    if (AMediaCodec_start(videoCodec_) != AMEDIA_OK) {
        LOGE("video codec start failed");
        return false;
    }

    // ============ 音频编码器（AAC）============
    // 混音模式才启用音频编码；纯系统/麦克风也走该编码器
    AMediaFormat* afmt = AMediaFormat_new();
    AMediaFormat_setString(afmt, AMEDIAFORMAT_KEY_MIME, "audio/mp4a-latm");
    AMediaFormat_setInt32(afmt, AMEDIAFORMAT_KEY_SAMPLE_RATE, kSampleRate);
    AMediaFormat_setInt32(afmt, AMEDIAFORMAT_KEY_CHANNEL_COUNT, kChannels);
    AMediaFormat_setInt32(afmt, AMEDIAFORMAT_KEY_AAC_PROFILE, 2 /*AAC LC*/);
    AMediaFormat_setInt32(afmt, AMEDIAFORMAT_KEY_BIT_RATE, 128'000);
    audioCodec_ = AMediaCodec_createEncoderByType("audio/mp4a-latm");
    if (audioCodec_) {
        if (AMediaCodec_configure(audioCodec_, afmt, nullptr, nullptr,
                                  AMEDIACODEC_CONFIGURE_FLAG_ENCODE) != AMEDIA_OK ||
            AMediaCodec_start(audioCodec_) != AMEDIA_OK) {
            AMediaCodec_delete(audioCodec_);
            audioCodec_ = nullptr;
        }
    }
    AMediaFormat_delete(afmt);

    running_ = true;
    done_ = false;
    paused_ = false;
    videoThread_ = std::thread([this] { videoLoop(); });
    audioThread_ = std::thread([this] { audioLoop(); });
    return true;
}

void VideoEncoder::videoLoop() {
    // 从编码器输出缓冲区取 H.264 数据，写入 muxer
    while (running_) {
        AMediaCodecBufferInfo info{};
        ssize_t idx = AMediaCodec_dequeueOutputBuffer(videoCodec_, &info, 10'000);
        if (idx >= 0) {
            // 先读数据再释放，避免悬垂指针
            uint8_t* buf = info.size > 0 ? AMediaCodec_getOutputBuffer(videoCodec_, idx) : nullptr;

            if (info.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) {
                // codec specific 数据：注册视频 track
                if (!hasVideoTrack_) {
                    videoTrack_ = AMediaMuxer_addTrack(muxer_, AMediaCodec_getOutputFormat(videoCodec_));
                    hasVideoTrack_ = true;
                    tryStartMuxer();
                }
            } else if (info.size > 0 && buf) {
                if (!hasVideoTrack_) {
                    videoTrack_ = AMediaMuxer_addTrack(muxer_, AMediaCodec_getOutputFormat(videoCodec_));
                    hasVideoTrack_ = true;
                }
                if (muxerStarted_ && videoTrack_ >= 0) {
                    AMediaMuxer_writeSampleData(muxer_, videoTrack_, buf, &info);
                }
            }
            AMediaCodec_releaseOutputBuffer(videoCodec_, idx, false);
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) break;
        }
    }
    LOGI("video loop exit");
}

void VideoEncoder::audioLoop() {
    // 从两路 PCM 缓冲取数据 → 混音 + 降噪 → 送入音频编码器 → 写 muxer
    constexpr int kFrameBatch = 1024; // 每批采样帧
    std::vector<int16_t> outBuf(kFrameBatch * kChannels);
    while (running_) {
        if (paused_) { std::this_thread::sleep_for(std::chrono::milliseconds(5)); continue; }

        std::vector<int16_t> sys, mic;
        {
            std::lock_guard<std::mutex> lk(bufMutex_);
            auto take = [&](std::deque<int16_t>& q, std::vector<int16_t>& dst) {
                size_t need = size_t(kFrameBatch) * kChannels;
                size_t have = std::min(need, q.size());
                dst.resize(have);
                if (have > 0) {
                    std::copy_n(q.begin(), have, dst.begin());
                    q.erase(q.begin(), q.begin() + long(have));
                }
            };
            take(sysBuf_, sys);
            take(micBuf_, mic);
        }

        // 混音（含音量平衡）
        int got = AudioMixer::mix(sys, mic, outBuf, audio_, kFrameBatch * kChannels);

        if (got > 0 && audioCodec_) {
            ssize_t inIdx = AMediaCodec_dequeueInputBuffer(audioCodec_, 5'000);
            if (inIdx >= 0) {
                size_t bufSize = 0;
                uint8_t* dst = AMediaCodec_getInputBuffer(audioCodec_, inIdx, &bufSize);
                if (dst && got * 2 <= (int)bufSize) {
                    std::memcpy(dst, outBuf.data(), got * 2);
                    AMediaCodec_queueInputBuffer(audioCodec_, inIdx, 0, got * 2,
                                                 getNowUs(), 0);
                }
            }
            // 取编码输出（先读后释放）
            AMediaCodecBufferInfo info{};
            ssize_t oIdx = AMediaCodec_dequeueOutputBuffer(audioCodec_, &info, 0);
            while (oIdx >= 0) {
                uint8_t* obuf = info.size > 0 ? AMediaCodec_getOutputBuffer(audioCodec_, oIdx) : nullptr;
                if (info.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) {
                    if (!hasAudioTrack_) {
                        audioTrack_ = AMediaMuxer_addTrack(muxer_, AMediaCodec_getOutputFormat(audioCodec_));
                        hasAudioTrack_ = true;
                        tryStartMuxer();
                    }
                } else if (info.size > 0 && obuf && muxerStarted_ && audioTrack_ >= 0) {
                    AMediaMuxer_writeSampleData(muxer_, audioTrack_, obuf, &info);
                }
                AMediaCodec_releaseOutputBuffer(audioCodec_, oIdx, false);
                oIdx = AMediaCodec_dequeueOutputBuffer(audioCodec_, &info, 0);
            }
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    LOGI("audio loop exit");
}

void VideoEncoder::tryStartMuxer() {
    if (muxerStarted_ || !hasVideoTrack_ || !hasAudioTrack_) return;
    if (AMediaMuxer_start(muxer_) == AMEDIA_OK) muxerStarted_ = true;
}

int64_t VideoEncoder::getNowUs() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

void VideoEncoder::pushSystemPcm(const int16_t* data, size_t frames) {
    if (!data || frames == 0) return;
    std::lock_guard<std::mutex> lk(bufMutex_);
    sysBuf_.insert(sysBuf_.end(), data, data + frames * kChannels);
    // 防止长时间无人消费导致内存膨胀：截断到 2 秒
    size_t maxFrames = size_t(kSampleRate) * 2 * kChannels;
    if (sysBuf_.size() > maxFrames) sysBuf_.erase(sysBuf_.begin(), sysBuf_.begin() + (sysBuf_.size() - maxFrames));
}

void VideoEncoder::pushMicPcm(const int16_t* data, size_t frames) {
    if (!data || frames == 0) return;
    std::lock_guard<std::mutex> lk(bufMutex_);
    micBuf_.insert(micBuf_.end(), data, data + frames * kChannels);
    size_t maxFrames = size_t(kSampleRate) * 2 * kChannels;
    if (micBuf_.size() > maxFrames) micBuf_.erase(micBuf_.begin(), micBuf_.begin() + (micBuf_.size() - maxFrames));
}

bool VideoEncoder::pause() {
    paused_ = true;
    return true;
}

bool VideoEncoder::resume() {
    paused_ = false;
    return true;
}

std::string VideoEncoder::stop() {
    running_ = false;
    if (videoThread_.joinable()) videoThread_.join();
    if (audioThread_.joinable()) audioThread_.join();

    // 发送 EOS 并 flush 剩余编码数据
    if (videoCodec_) {
        ssize_t idx = AMediaCodec_dequeueInputBuffer(videoCodec_, 5'000);
        if (idx >= 0) {
            AMediaCodec_queueInputBuffer(videoCodec_, idx, 0, 0, getNowUs(),
                                        AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
        }
        // 再次循环取完剩余输出
        AMediaCodecBufferInfo info{};
        ssize_t oIdx;
        while ((oIdx = AMediaCodec_dequeueOutputBuffer(videoCodec_, &info, 5'000)) >= 0) {
            if (info.size > 0 && muxerStarted_ && videoTrack_ >= 0)
                AMediaMuxer_writeSampleData(muxer_, videoTrack_, AMediaCodec_getOutputBuffer(videoCodec_, oIdx), &info);
            AMediaCodec_releaseOutputBuffer(videoCodec_, oIdx, false);
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) break;
        }
    }
    if (audioCodec_) {
        ssize_t idx = AMediaCodec_dequeueInputBuffer(audioCodec_, 5'000);
        if (idx >= 0)
            AMediaCodec_queueInputBuffer(audioCodec_, idx, 0, 0, getNowUs(),
                                        AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
    }

    if (muxerStarted_) {
        AMediaMuxer_stop(muxer_);
        muxerStarted_ = false;
    }
    return outputPath_;
}

void VideoEncoder::destroy() {
    running_ = false;
    done_ = true;
    if (videoThread_.joinable()) videoThread_.join();
    if (audioThread_.joinable()) audioThread_.join();

    if (muxerStarted_) { AMediaMuxer_stop(muxer_); muxerStarted_ = false; }
    if (muxer_) { AMediaMuxer_delete(muxer_); muxer_ = nullptr; }
    if (videoCodec_) { AMediaCodec_stop(videoCodec_); AMediaCodec_delete(videoCodec_); videoCodec_ = nullptr; }
    if (audioCodec_) { AMediaCodec_stop(audioCodec_); AMediaCodec_delete(audioCodec_); audioCodec_ = nullptr; }
    if (inputSurface_) { ANativeWindow_release(inputSurface_); inputSurface_ = nullptr; }

    videoTrack_ = audioTrack_ = -1;
    hasVideoTrack_ = hasAudioTrack_ = false;
    std::lock_guard<std::mutex> lk(bufMutex_);
    sysBuf_.clear();
    micBuf_.clear();
    outputPath_.clear();
}

} // namespace sp
