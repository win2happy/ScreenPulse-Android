package com.screenpulse.ui.theme

import androidx.compose.ui.graphics.Color

// ================= 绿色主题色彩规范（PRD-V1.1 §3.7.2）=================
// 所有颜色集中在此定义，禁止在页面中硬编码，保证主题统一与对比度达标。

// ---- 品牌绿体系 ----
val BrandGreen = Color(0xFF2E7D32)      // 主色（深绿）：主按钮、高亮操作
val AccentGreen = Color(0xFF4CAF50)     // 辅助绿：开关激活、次要按钮、进度条
val LightGreenBg = Color(0xFFE8F5E9)    // 淡绿：浅色模式卡片选中背景

// ---- 浅色模式 ----
val LightPrimary = Color(0xFF2E7D32)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightSecondary = Color(0xFF4CAF50)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)     // 白底
val LightSurfaceVariant = Color(0xFFE8F5E9) // 卡片淡绿
val LightOnSurface = Color(0xFF1B1B1B)   // 深灰文字
val LightOnSurfaceVariant = Color(0xFF37474F)

// ---- 深色模式（降低绿色饱和度，防止刺眼）----
val DarkPrimary = Color(0xFF66BB6A)
val DarkOnPrimary = Color(0xFF0B1F0C)
val DarkSecondary = Color(0xFF81C784)
val DarkOnSecondary = Color(0xFF0B1F0C)
val DarkSurface = Color(0xFF121212)      // 深暗背景
val DarkSurfaceVariant = Color(0xFF1B311D) // 卡片深绿灰
val DarkOnSurface = Color(0xFFECEFF1)    // 浅白文字
val DarkOnSurfaceVariant = Color(0xFF9FB6A0)

// ---- 通用 ----
val ErrorRed = Color(0xFFB00020)          // 错误提示标准红，不与绿色冲突
val DarkErrorRed = Color(0xFFCF6679)
