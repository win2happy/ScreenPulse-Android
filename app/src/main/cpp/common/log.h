//
// common/log.h - 轻量原生日志封装
//
#ifndef SP_LOG_H
#define SP_LOG_H

#include <android/log.h>

#define SP_TAG "ScreenPulseCore"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  SP_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  SP_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SP_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, SP_TAG, __VA_ARGS__)

#endif // SP_LOG_H
