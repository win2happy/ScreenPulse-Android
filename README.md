# ScreenPulse 瞬录（ScreenPulse-Android）

一款 **全功能免费、纯净零广告、极致流畅、音画优质、低存储占用、轻量化稳定** 的安卓录屏工具。

- 项目：`screen-pulse-android`
- 版本：V1.1（新增绿色主题 / 浅色 / 深色 / 跟随系统）
- 语言：Kotlin（UI）+ C++/NDK（音视频核心）
- UI：Jetpack Compose Material3
- 最低系统：Android 8.0（API 26）

---

## 一、功能总览（对照 PRD-V1.1）

| 模块 | 说明 | 落地位置 |
|------|------|----------|
| 绿色 M3 主题 | 主色 #2E7D32，浅色/深色/跟随系统即时切换 | `ui/theme/` |
| 全屏/区域录制 | MediaProjection + C++ 区域裁剪（EGL） | `service/` + `cpp/video/` |
| 三音频模式 | 仅系统 / 仅麦克风 / 混音 + 人声降噪 + 音量平衡 | `cpp/audio/` |
| 参数自定义 | 分辨率/帧率/码率/水印/倒计时 | `ui/settings/` |
| 悬浮窗 | 原生 View，拖动/控制/隐藏，跟随主题 | `floatingwindow/` |
| 快捷键 | 物理按键启停（扩展点） | `shortcut/` |
| 后台压缩 | WorkManager + 三档压缩（硬编→FFmpeg兜底） | `util/` + `cpp/compress/` |
| 文件管理 | 预览/重命名/删除/一键分享 | `ui/videolist/` |
| 隐私合规 | 仅录屏/麦克风/存储权限，纯本地，无网络无广告 | 全局 |

## 二、技术架构分层（对齐技术落地文档）

```
┌─────────────────────────────────────────────┐
│ UI 层：Kotlin + Jetpack Compose（悬浮窗原生View）│
├─────────────────────────────────────────────┤
│ 业务层：Kotlin + Jetpack（ViewModel/Lifecycle/ │
│         WorkManager / DataStore）            │
├─────────────────────────────────────────────┤
│ JNI 桥接层：只传控制参数，DirectBuffer 零拷贝音频 │
├─────────────────────────────────────────────┤
│ 音视频核心 C++/NDK：AMediaCodec 硬编 + AMediaMuxer │
│   + PCM 混音降噪 + 区域裁剪 + 三档压缩          │
└─────────────────────────────────────────────┘
```

## 三、如何编译

### 0) 环境要求
- Android Studio（Ladybug 或更新）
- JDK 17
- Android SDK 35
- NDK r26+、CMake 3.22+

### 1) 准备 NDK + FFmpeg
本工程默认开启 FFmpeg 兜底转码（`SP_USE_FFMPEG=ON`）。
- 执行 `scripts/build_ffmpeg.sh` 编译精简 FFmpeg，产出
  `app/src/main/jniLibs/<ABI>/libffmpeg_minimal.so` 及 `libav*.so`。
- **如果暂时不想编译 FFmpeg**：把 `app/src/main/cpp/CMakeLists.txt` 中
  `set(SP_USE_FFMPEG ON ...)` 改为 `OFF`，`video_compressor.cpp` 走 MediaCodec 硬转码路径
  （压缩档位仅硬编，功能降级但工程可编译）。

### 2) 构建
```bash
./gradlew assembleDebug
```
安装包：`app/build/outputs/apk/debug/app-debug.apk`

> 说明：`gradle/wrapper/gradle-wrapper.jar` 由 Android Studio 首次 Sync 自动生成，
> 或用 `gradle wrapper --gradle-version 8.9` 生成。

## 四、运行时权限（一次授权，纯本地）
1. 悬浮窗权限（悬浮窗控制）
2. 录屏授权（MediaProjection，系统弹窗）
3. 麦克风权限（仅麦克风/混音模式）
4. 通知权限（Android 13+）

## 五、关键设计说明
- **音视频数据不过 Kotlin 层**：帧与 PCM 全部在 C++ 层处理，规避 GC 卡顿（技术文档硬性约束）。
- **硬编码优先**：AMediaCodec H.264/AAC，硬编失败降级 FFmpeg 软转码。
- **零广告零网络**：不引入任何网络库/广告 SDK/埋点，仅本地存储。

## 六、目录结构
```
app/src/main/
├── java/com/screenpulse/
│   ├── ui/                 # Compose UI（theme/settings/videolist）
│   ├── service/            # 前台录屏服务
│   ├── viewmodel/          # ViewModel（主题/参数/文件列表）
│   ├── repository/         # DataStore + 视频仓库
│   ├── floatingwindow/     # 原生悬浮窗
│   ├── shortcut/           # 快捷键
│   ├── jni/                # JNI 接口
│   └── util/               # 权限/压缩 Worker
├── cpp/                    # NDK 音视频核心（audio/video/compress/ffmpeg-wrapper）
└── res/                    # 颜色/主题/悬浮窗布局/图标
```

## 七、测试重点（PRD §九）
1. 3 小时连续录制内存曲线平稳
2. 三种音频模式（Android 10+ 完整验证，8-9 自动降级仅麦克风）
3. 区域录制画面裁剪正确性
4. 三档压缩体积与画质
5. 主题三模式所有页面 + 悬浮窗即时切换
6. 各权限拒绝场景异常处理
