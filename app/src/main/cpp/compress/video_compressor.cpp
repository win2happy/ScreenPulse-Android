//
// compress/video_compressor.cpp - 三档压缩实现
//
#include "compress/video_compressor.h"
#include "common/log.h"
#include "common/constants.h"

#ifdef SP_USE_FFMPEG
extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavfilter/avfilter.h>
#include <libswscale/swscale.h>
}
#endif

namespace sp {

namespace {

// 三档压缩参数（码率/分辨率缩放）
struct CompressProfile {
    int bitrateFactor;   // 相对原始码率的比例（%）
    int scaleDiv;        // 分辨率缩放分母（1=不变,2=减半）
    bool highPreset;     // 无损档使用更高质量预设
};

constexpr CompressProfile kProfiles[3] = {
    { 40,  2, false }, // 极速：40% 码率，减半分辨率
    { 60,  1, false }, // 均衡：60% 码率，分辨率不变
    { 100, 1, true  }, // 无损：100% 码率，CRF 高质量
};

} // namespace

int VideoCompressor::compress(const std::string& inputPath,
                              const std::string& outputPath,
                              int level) {
    if (level < 0 || level > 2) level = kCompressBalanced;
    const CompressProfile& prof = kProfiles[level];
    LOGI("compress: %s -> %s level=%d", inputPath.c_str(), outputPath.c_str(), level);

#ifndef SP_USE_FFMPEG
    // 无 FFmpeg 时，暂返回失败（由上层保留原文件）；实际工程用 MediaCodec 硬转码。
    LOGE("FFmpeg not linked, compress unavailable");
    return kErr;
#else
    // ============ FFmpeg 软转码（兜底路径）============
    AVFormatContext* inFmt = nullptr;
    AVFormatContext* outFmt = nullptr;
    int ret = kErr;

    if (avformat_open_input(&inFmt, inputPath.c_str(), nullptr, nullptr) < 0) {
        LOGE("compress: cannot open input");
        return kErr;
    }
    if (avformat_find_stream_info(inFmt, nullptr) < 0) {
        LOGE("compress: cannot find stream info");
        goto done;
    }

    // 找视频流
    int vIdx = -1;
    for (unsigned i = 0; i < inFmt->nb_streams; ++i) {
        if (inFmt->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) { vIdx = i; break; }
    }
    if (vIdx < 0) { LOGE("compress: no video stream"); goto done; }

    const AVCodec* dec = avcodec_find_decoder(inFmt->streams[vIdx]->codecpar->codec_id);
    if (!dec) { LOGE("compress: no decoder"); goto done; }

    AVCodecContext* decCtx = avcodec_alloc_context3(dec);
    avcodec_parameters_to_context(decCtx, inFmt->streams[vIdx]->codecpar);
    if (avcodec_open2(decCtx, dec, nullptr) < 0) { LOGE("compress: open decoder"); goto done; }

    // 编码器（H.264）
    const AVCodec* enc = avcodec_find_encoder(AV_CODEC_ID_H264);
    if (!enc) { LOGE("compress: no h264 encoder"); goto done; }
    AVCodecContext* encCtx = avcodec_alloc_context3(enc);
    int outW = decCtx->width / prof.scaleDiv;
    int outH = decCtx->height / prof.scaleDiv;
    encCtx->width = outW;
    encCtx->height = outH;
    encCtx->time_base = (AVRational){ 1, 30 };
    encCtx->framerate = (AVRational){ 30, 1 };
    encCtx->pix_fmt = AV_PIX_FMT_YUV420P;
    encCtx->bit_rate = (int64_t)(decCtx->bit_rate * prof.bitrateFactor / 100);
    if (encCtx->bit_rate <= 0) encCtx->bit_rate = 4'000'000;
    encCtx->gop_size = 60;
    encCtx->max_b_frames = 0;
    if (prof.highPreset) encCtx->qmin = 2, encCtx->qmax = 18;
    else                 encCtx->qmin = 10, encCtx->qmax = 36;
    if (avcodec_open2(encCtx, enc, nullptr) < 0) { LOGE("compress: open encoder"); goto done; }

    // 输出容器
    if (avformat_alloc_output_context2(&outFmt, nullptr, nullptr, outputPath.c_str()) < 0) {
        LOGE("compress: alloc output"); goto done;
    }
    AVStream* outStream = avformat_new_stream(outFmt, nullptr);
    avcodec_parameters_from_context(outStream->codecpar, encCtx);
    outStream->time_base = encCtx->time_base;

    if (!(outFmt->oformat->flags & AVFMT_NOFILE)) {
        if (avio_open(&outFmt->pb, outputPath.c_str(), AVIO_FLAG_WRITE) < 0) {
            LOGE("compress: open output file"); goto done;
        }
    }
    if (avformat_write_header(outFmt, nullptr) < 0) { LOGE("compress: write header"); goto done; }

    // 像素转换 + 编解码循环
    SwsContext* sws = sws_getContext(decCtx->width, decCtx->height, decCtx->pix_fmt,
                                     outW, outH, AV_PIX_FMT_YUV420P,
                                     SWS_BILINEAR, nullptr, nullptr, nullptr);
    AVFrame* frame = av_frame_alloc();
    AVFrame* scaled = av_frame_alloc();
    scaled->format = AV_PIX_FMT_YUV420P;
    scaled->width = outW;
    scaled->height = outH;
    av_frame_get_buffer(scaled, 32);

    AVPacket* pkt = av_packet_alloc();
    int64_t pts = 0;
    while (av_read_frame(inFmt, pkt) >= 0) {
        if (pkt->stream_index == vIdx) {
            if (avcodec_send_packet(decCtx, pkt) == 0) {
                while (avcodec_receive_frame(decCtx, frame) == 0) {
                    sws_scale(sws, frame->data, frame->linesize, 0, decCtx->height,
                              scaled->data, scaled->linesize);
                    scaled->pts = pts++;
                    if (avcodec_send_frame(encCtx, scaled) == 0) {
                        AVPacket* ep = av_packet_alloc();
                        while (avcodec_receive_packet(encCtx, ep) == 0) {
                            av_packet_rescale_ts(ep, encCtx->time_base, outStream->time_base);
                            ep->stream_index = outStream->index;
                            av_interleaved_write_frame(outFmt, ep);
                        }
                        av_packet_free(&ep);
                    }
                }
            }
        }
        av_packet_unref(pkt);
    }
    // flush
    avcodec_send_frame(encCtx, nullptr);
    while (avcodec_receive_packet(encCtx, pkt) == 0) {
        av_packet_rescale_ts(pkt, encCtx->time_base, outStream->time_base);
        pkt->stream_index = outStream->index;
        av_interleaved_write_frame(outFmt, pkt);
        av_packet_unref(pkt);
    }

    av_write_trailer(outFmt);
    ret = kOk;

    // 释放
    av_packet_free(&pkt);
    av_frame_free(&scaled);
    av_frame_free(&frame);
    sws_freeContext(sws);
    avcodec_free_context(&encCtx);
    avcodec_free_context(&decCtx);

done:
    if (outFmt) {
        if (outFmt->pb && !(outFmt->oformat->flags & AVFMT_NOFILE)) avio_closep(&outFmt->pb);
        avformat_free_context(outFmt);
    }
    if (inFmt) avformat_close_input(&inFmt);
    return ret;
#endif
}

} // namespace sp
