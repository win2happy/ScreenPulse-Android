//
// compress/video_compressor.h - 视频二次压缩（三档）
//
#ifndef SP_VIDEO_COMPRESSOR_H
#define SP_VIDEO_COMPRESSOR_H

#include <string>

namespace sp {

/**
 * 录制完成后由 WorkManager 在后台调用的视频压缩器。
 * 优先 MediaCodec 硬转码；硬件不支持时降级精简 FFmpeg 软转码。
 * 三档：极速 / 均衡 / 高清无损。
 */
class VideoCompressor {
public:
    /**
     * @param inputPath 源 MP4
     * @param outputPath 输出 MP4
     * @param level CompressLevel（0 极速 / 1 均衡 / 2 无损）
     * @return 0 成功，非 0 失败
     */
    static int compress(const std::string& inputPath,
                        const std::string& outputPath,
                        int level);
};

} // namespace sp

#endif // SP_VIDEO_COMPRESSOR_H
