# JNI 类，禁止混淆（被 C++ 通过方法签名调用）
-keep class com.screenpulse.jni.ScreenRecordNative { *; }

# 保持枚举（DataStore 序列化用到）
-keepclassmembers enum com.screenpulse.repository.** { *; }

# 前台服务与悬浮窗相关的系统 API，禁止混淆
-keep class com.screenpulse.service.** { *; }
-keep class com.screenpulse.floatingwindow.** { *; }
