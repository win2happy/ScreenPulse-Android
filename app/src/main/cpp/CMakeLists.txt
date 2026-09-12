cmake_minimum_required(VERSION 3.22.1)
project("screenpulse_core")

# ============ 原生音视频核心 ============
# 依赖预编译裁剪 FFmpeg（见 scripts/build_ffmpeg.sh 与 jniLibs 目录）
# 若暂时不编译 FFmpeg，可移除 ffmpeg_api 并设置 SP_USE_FFMPEG=OFF 仅用 MediaCodec 硬编。

set(SP_USE_FFMPEG ON CACHE BOOL "Use prebuilt minimal FFmpeg for transcode fallback")

add_library(screenpulse_core SHARED
        common/log.h
        common/constants.h
        audio/audio_collector.h
        audio/audio_collector.cpp
        audio/audio_mixer.h
        audio/audio_mixer.cpp
        audio/noise_suppressor.h
        audio/noise_suppressor.cpp
        video/video_encoder.h
        video/video_encoder.cpp
        video/frame_processor.h
        video/frame_processor.cpp
        compress/video_compressor.h
        compress/video_compressor.cpp
        jni_bridge.h
        jni_bridge.cpp
)

if (SP_USE_FFMPEG)
    add_library(ffmpeg_minimal SHARED IMPORTED)
    set_target_properties(ffmpeg_minimal PROPERTIES
            IMPORTED_LOCATION
            ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libffmpeg_minimal.so)
    target_compile_definitions(screenpulse_core PRIVATE SP_USE_FFMPEG=1)
    target_link_libraries(screenpulse_core ffmpeg_minimal)
endif()

find_library(log-lib log)
find_library(android-lib android)
find_library(OpenMAXAL-lib OpenMAXAL)

target_include_directories(screenpulse_core PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg-wrapper)

target_link_libraries(screenpulse_core
        ${log-lib}
        ${android-lib}
        ${OpenMAXAL-lib})

# 链接顺序：libs 必须放在依赖库之后，保证 jni 引用解析
target_compile_options(screenpulse_core PRIVATE -O2 -ffast-math)
