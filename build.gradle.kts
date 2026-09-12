// 顶层构建文件：配置对所有子模块的通用依赖版本
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
