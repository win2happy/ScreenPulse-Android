//
// ffmpeg-wrapper/ffmpeg_api.h - 裁剪 FFmpeg 封装接口
//
// 统一封装 FFmpeg 的转码/裁剪能力，供 video_compressor 调用。
// 依赖 scripts/build_ffmpeg.sh 产出的 libffmpeg_minimal.so。
//
#ifndef SP_FFMPEG_API_H
#define SP_FFMPEG_API_H

#include <string>

namespace sp {

/**
 * FFmpeg 二次转码：输入 MP4 → 输出 MP4。
 * 与 MediaCodec 硬转码互为补充：硬编不可用时降级到本路径。
 */
struct FfmpegTranscodeOptions {
    int outWidth = 0;      // 0 = 保持原分辨率
    int outHeight = 0;
    int fps = 0;           // 0 = 保持原帧率
    int bitrate = 0;       // bps，0 = 自动
    bool highQuality = false; // 无损档高质量预设
};

int ffmpegTranscode(const std::string& inputPath,
                    const std::string& outputPath,
                    const FfmpegTranscodeOptions& opts);

} // namespace sp

#endif // SP_FFMPEG_API_H
