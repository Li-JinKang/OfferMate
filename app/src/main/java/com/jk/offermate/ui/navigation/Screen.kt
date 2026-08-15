package com.jk.offermate.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航目的地（精简 3 Tab：导入 / 题库&刷题 / 我的；设置并入"我的"）。
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "导入", Icons.Filled.Home)
    data object Quiz : Screen("quiz", "题库", Icons.AutoMirrored.Filled.List)
    data object Profile : Screen("profile", "我的", Icons.Filled.Person)

    companion object {
        val bottomItems = listOf(Home, Quiz, Profile)
    }
}
