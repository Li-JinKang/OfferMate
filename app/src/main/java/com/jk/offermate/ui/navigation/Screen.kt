package com.jk.offermate.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.jk.offermate.R

/**
 * 底部导航目的地（3 Tab：导入 / 题库&刷题 / AI 对话）。
 * “我的/设置”不再作为 Tab，改由 AI 对话页右上角入口进入。
 *
 * 图标二选一：[icon] 为单色矢量（随选中态着色）；[iconRes] 为多色 drawable（[coloredIcon]=true 时不着色）。
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector? = null,
    @param:DrawableRes val iconRes: Int? = null,
    val coloredIcon: Boolean = false
) {
    data object Home : Screen("home", "主页", iconRes = R.drawable.ic_home_circle, coloredIcon = true)
    data object Quiz : Screen("quiz", "题库", iconRes = R.drawable.ic_category_grid, coloredIcon = true)
    data object AiChat : Screen("aichat", "AI对话", iconRes = R.drawable.ic_ai_chat, coloredIcon = true)

    /** 设置页（原“我的”）：不再是 Tab，作为独立页面从 AI 对话页进入。 */
    data object Settings : Screen("profile", "设置", icon = Icons.Filled.Settings)

    companion object {
        // 用 lazy 延迟到首次访问再构造，避免 sealed class 与 data object 的类初始化顺序问题
        val bottomItems: List<Screen> by lazy { listOf(Home, Quiz, AiChat) }
    }
}
