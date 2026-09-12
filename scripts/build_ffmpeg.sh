#
# FFmpeg-minimal 裁剪编译脚本（Android NDK）
#
# 目标：只编译录屏所需模块，剔除无用功能，控制 so 体积与内存。
# 产物：app/src/main/jniLibs/<ABI>/libffmpeg_minimal.so
#
# 用法：
#   export ANDROID_NDK=/path/to/android-ndk-r27
#   bash scripts/build_ffmpeg.sh arm64-v8a
#
# 需要先下载 FFmpeg 源码（4.4 或 5.x）并解压到 FFMPEG_SRC 目录。
#

set -e

FFMPEG_SRC="${FFMPEG_SRC:-$HOME/ffmpeg}"
ANDROID_NDK="${ANDROID_NDK:?请设置 ANDROID_NDK 路径}"
API=26

# 目标 ABI（支持 arm64-v8a / armeabi-v7a / x86_64）
ABI="${1:-arm64-v8a}"

case "$ABI" in
  arm64-v8a)
    ARCH=arm64
    CPU=armv8-a
    TRIPLE=aarch64-linux-android
    ;;
  armeabi-v7a)
    ARCH=arm
    CPU=armv7-a
    TRIPLE=armv7a-linux-androideabi
    ;;
  x86_64)
    ARCH=x86_64
    CPU=x86-64
    TRIPLE=x86_64-linux-android
    ;;
  *) echo "不支持的 ABI: $ABI"; exit 1 ;;
esac

TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"
SYSROOT="$TOOLCHAIN/sysroot"
CC="$TOOLCHAIN/bin/${TRIPLE}${API}-clang"
CXX="$TOOLCHAIN/bin/${TRIPLE}${API}-clang++"
STRIP="$TOOLCHAIN/bin/llvm-strip"

OUT_DIR="$(pwd)/app/src/main/jniLibs/$ABI"
mkdir -p "$OUT_DIR"

cd "$FFMPEG_SRC"

make distclean >/dev/null 2>&1 || true

./configure \
  --target-os=android \
  --arch="$ARCH" \
  --cpu="$CPU" \
  --cc="$CC" \
  --cxx="$CXX" \
  --strip="$STRIP" \
  --enable-cross-compile \
  --sysroot="$SYSROOT" \
  --prefix="$OUT_DIR" \
  --enable-shared \
  --disable-static \
  --disable-programs \
  --disable-doc \
  --disable-avdevice \
  --disable-postproc \
  --disable-network \
  --disable-everything \
  --enable-avcodec \
  --enable-avformat \
  --enable-avfilter \
  --enable-swresample \
  --enable-swscale \
  --enable-decoder=h264,aac,hevc,mp3,aac_latm \
  --enable-encoder=h264,aac \
  --enable-parser=h264,aac,hevc \
  --enable-demuxer=mov,mp4,m4a,3gp \
  --enable-muxer=mp4 \
  --enable-protocol=file \
  --enable-filter=crop,scale,overlay,drawtext,aresample,volume,anullsrc \
  --enable-avcodec \
  --disable-asm

make -j"$(nproc)"
make install

# 链接器脚本确保依赖顺序正确
echo "FFmpeg-minimal 已编译到 $OUT_DIR"
echo "请将 libffmpeg_minimal.so 与 libav*.so 放入对应 ABI 目录，并在 app/build.gradle 的 jniLibs 中配置"
