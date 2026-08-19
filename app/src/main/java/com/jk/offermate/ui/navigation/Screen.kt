package com.jk.offermate.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航目的地（3 Tab：导入 / 题库&刷题 / AI 对话）。
 * “我的/设置”不再作为 Tab，改由 AI 对话页右上角入口进入。
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "主页", Icons.Filled.Home)
    data object Quiz : Screen("quiz", "题库", Icons.AutoMirrored.Filled.List)
    data object AiChat : Screen("aichat", "AI对话", Icons.Filled.Email)

    /** 设置页（原“我的”）：不再是 Tab，作为独立页面从 AI 对话页进入。 */
    data object Settings : Screen("profile", "设置", Icons.Filled.Email)

    companion object {
        // 用 lazy 延迟到首次访问再构造，避免 sealed class 与 data object 的类初始化顺序问题
        val bottomItems: List<Screen> by lazy { listOf(Home, Quiz, AiChat) }
    }
}
