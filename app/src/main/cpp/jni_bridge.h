//
// jni_bridge.h - JNI 桥接层声明
//
#ifndef SP_JNI_BRIDGE_H
#define SP_JNI_BRIDGE_H

#include <jni.h>

namespace sp {

// 注册所有 native 方法（在 JNI_OnLoad 调用）
int registerNativeMethods(JNIEnv* env);

}

#endif // SP_JNI_BRIDGE_H
