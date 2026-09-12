//
// jni_bridge.cpp - JNI 桥接：Kotlin ScreenRecordNative <-> C++ 核心
//
// 遵守技术文档 §2.4：只传递控制参数，不传递大块图像/音频原始数据。
// 音频 PCM 通过 DirectByteBuffer 零拷贝推送（见 pushSystemPcm/pushMicPcm）。
//
#include "jni_bridge.h"
#include "common/log.h"
#include "common/constants.h"
#include "video/video_encoder.h"
#include "video/frame_processor.h"
#include "compress/video_compressor.h"
#include "audio/noise_suppressor.h"

#include <android/native_window_jni.h>

namespace sp {

namespace {

// 全局状态
VideoParams gVideoParams;
RegionRect gRegion;
AudioMixState gAudioState{};
bool gWatermark = false;
std::string gWatermarkText;
FrameProcessor gFrameProcessor;
NoiseSuppressor gNoise(48000);

// 输出路径缓存（stop 时返回）
std::string gOutputPath;

} // namespace

// ---------- 辅助：JNI 字符串 ----------
static std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    env->ReleaseStringUTFChars(s, c);
    return r;
}

// ---------- 方法实现 ----------

static jint nativeInitRecorder(JNIEnv*, jobject) {
    LOGI("nativeInitRecorder");
    gVideoParams = VideoParams{};
    gRegion = RegionRect{};
    gAudioState = AudioMixState{};
    gNoise.reset();
    return kOk;
}

static jint nativeSetVideoParam(JNIEnv*, jobject, jint w, jint h, jint fps, jint bitrate) {
    gVideoParams = VideoParams{ w, h, fps, bitrate };
    return kOk;
}

static jint nativeSetRegionCrop(JNIEnv*, jobject, jint l, jint t, jint r, jint b) {
    if (r > l && b > t) {
        gRegion = RegionRect{ l, t, r, b, true };
    } else {
        gRegion.enabled = false;
    }
    // 同步到画面处理器（区域录制在 GPU 管线裁剪）
    if (gRegion.enabled) {
        gFrameProcessor.setRegion(FrameRect{ l, t, r, b });
    } else {
        gFrameProcessor.clearRegion();
    }
    return kOk;
}

static jint nativeSetAudioMode(JNIEnv*, jobject, jint mode, jint micVol, jint sysVol) {
    gAudioState = AudioMixState{ mode, micVol, sysVol };
    return kOk;
}

static jobject nativeCreateInputSurface(JNIEnv* env, jobject) {
    // 初始化编码器并创建输入 Surface
    if (!VideoEncoder::instance().init(gVideoParams, gRegion)) {
        LOGE("video encoder init failed");
        return nullptr;
    }
    VideoEncoder::instance().setAudioMode(gAudioState);
    VideoEncoder::instance().setWatermark(gWatermark, gWatermarkText);

    ANativeWindow* win = VideoEncoder::instance().createInputSurface();
    if (!win) return nullptr;
    return ANativeWindow_toSurface(env, win);
}

static jint nativeStartEncode(JNIEnv*, jobject, jobject surface) {
    bool ok = VideoEncoder::instance().start();
    return ok ? kOk : kErr;
}

static jint nativePauseEncode(JNIEnv*, jobject) {
    return VideoEncoder::instance().pause() ? kOk : kErr;
}

static jint nativeResumeEncode(JNIEnv*, jobject) {
    return VideoEncoder::instance().resume() ? kOk : kErr;
}

static jstring nativeStopEncode(JNIEnv* env, jobject) {
    gOutputPath = VideoEncoder::instance().stop();
    return env->NewStringUTF(gOutputPath.c_str());
}

static jint nativeDoCompress(JNIEnv* env, jobject, jstring in, jstring out, jint level) {
    std::string inPath = jstr(env, in);
    std::string outPath = jstr(env, out);
    return VideoCompressor::compress(inPath, outPath, level);
}

static jint nativeReleaseRecorder(JNIEnv*, jobject) {
    VideoEncoder::instance().destroy();
    gFrameProcessor.release();
    return kOk;
}

static jboolean nativeLoadSuccess(JNIEnv*, jobject) {
    return JNI_TRUE;
}

// ---------- DirectByteBuffer 零拷贝音频推送 ----------
// Kotlin: pushSystemPcm(java.nio.ByteBuffer buf, int size)
static void nativePushSystemPcm(JNIEnv* env, jobject, jobject buffer) {
    if (!buffer) return;
    void* addr = env->GetDirectBufferAddress(buffer);
    if (!addr) return;
    jlong cap = env->GetDirectBufferCapacity(buffer);
    size_t frames = (size_t)cap / (2 * kBytesPerSample); // 立体声 PCM16
    VideoEncoder::instance().pushSystemPcm((const int16_t*)addr, frames);
}

static void nativePushMicPcm(JNIEnv* env, jobject, jobject buffer) {
    if (!buffer) return;
    void* addr = env->GetDirectBufferAddress(buffer);
    if (!addr) return;
    jlong cap = env->GetDirectBufferCapacity(buffer);
    size_t frames = (size_t)cap / (2 * kBytesPerSample);
    // 麦克风先过轻量降噪
    gNoise.process((int16_t*)addr, frames);
    VideoEncoder::instance().pushMicPcm((const int16_t*)addr, frames);
}

// ---------- 方法表 ----------
static const JNINativeMethod kMethods[] = {
    { "initRecorder",        "()I",                  (void*)nativeInitRecorder },
    { "setVideoParam",       "(IIII)I",              (void*)nativeSetVideoParam },
    { "setRegionCrop",       "(IIII)I",              (void*)nativeSetRegionCrop },
    { "setAudioMode",        "(III)I",               (void*)nativeSetAudioMode },
    { "createInputSurface",  "()Landroid/view/Surface;", (void*)nativeCreateInputSurface },
    { "startEncode",         "(Landroid/view/Surface;)I", (void*)nativeStartEncode },
    { "pauseEncode",         "()I",                  (void*)nativePauseEncode },
    { "resumeEncode",        "()I",                  (void*)nativeResumeEncode },
    { "stopEncode",          "()Ljava/lang/String;", (void*)nativeStopEncode },
    { "doCompress",          "(Ljava/lang/String;Ljava/lang/String;I)I", (void*)nativeDoCompress },
    { "releaseRecorder",     "()I",                  (void*)nativeReleaseRecorder },
    { "loadSuccess",         "()Z",                  (void*)nativeLoadSuccess },
    { "pushSystemPcm",       "(Ljava/nio/ByteBuffer;)V", (void*)nativePushSystemPcm },
    { "pushMicPcm",          "(Ljava/nio/ByteBuffer;)V", (void*)nativePushMicPcm },
};

int registerNativeMethods(JNIEnv* env) {
    jclass clazz = env->FindClass("com/screenpulse/jni/ScreenRecordNative");
    if (!clazz) {
        LOGE("cannot find ScreenRecordNative class");
        return JNI_ERR;
    }
    jint n = env->RegisterNatives(clazz, kMethods, sizeof(kMethods) / sizeof(kMethods[0]));
    env->DeleteLocalRef(clazz);
    return n;
}

} // namespace sp

// ---------- 库加载入口 ----------
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;
    if (sp::registerNativeMethods(env) != JNI_OK)
        return JNI_ERR;
    return JNI_VERSION_1_6;
}
