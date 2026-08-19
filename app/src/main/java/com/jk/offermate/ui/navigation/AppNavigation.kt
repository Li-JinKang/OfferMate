package com.jk.offermate.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jk.offermate.di.AppContainer
import com.jk.offermate.ui.aichat.AiChatRoute
import com.jk.offermate.ui.home.HomeRoute
import com.jk.offermate.ui.profile.ProfileRoute
import com.jk.offermate.ui.questions.QuestionsRoute
import com.jk.offermate.ui.quiz.QuizCategoryRoute
import com.jk.offermate.ui.quiz.QuizRoute

/**
 * 应用根 UI：底部导航 + NavHost。依赖通过 [container] 向下传递（构造注入）。
 */
@Composable
fun OfferMateApp(
    container: AppContainer,
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    // 从帖子/题目卡片“追问”跳转到 AI 对话 Tab 时携带的题目 id（一次性）
    var pendingChatQuestionId by remember { mutableStateOf<String?>(null) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // 全局浮动底栏只在 Home/Quiz 展示；AI 对话页在其底部 dock 里自绘 Tab（与输入条堆叠）。
    val showBottomBar = currentDestination?.route in setOf(Screen.Home.route, Screen.Quiz.route)

    // Tab 切换动作（AI 对话 dock 内的 TabPill 也复用）
    val navigateTab: (Screen) -> Unit = { screen ->
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                FloatingBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = navigateTab
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeRoute(
                    container = container,
                    onOpenPost = { postId -> navController.navigate("questions/$postId") },
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    sharedText = sharedText,
                    onSharedTextConsumed = onSharedTextConsumed
                )
            }
            composable(Screen.Quiz.route) {
                QuizRoute(container, onOpenCategory = { name ->
                    navController.navigate("quizCategory/${android.net.Uri.encode(name)}")
                })
            }
            composable(
                route = "quizCategory/{category}",
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { entry ->
                QuizCategoryRoute(
                    container = container,
                    category = entry.arguments?.getString("category").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AiChat.route) {
                AiChatRoute(
                    container = container,
                    currentDestination = currentDestination,
                    onNavigateTab = navigateTab,
                    pendingQuestionId = pendingChatQuestionId,
                    onPendingConsumed = { pendingChatQuestionId = null }
                )
            }
            composable(Screen.Settings.route) {
                ProfileRoute(container, onBack = { navController.popBackStack() })
            }
            composable(
                route = "questions/{postId}",
                arguments = listOf(navArgument("postId") { type = NavType.StringType })
            ) { entry ->
                QuestionsRoute(
                    container = container,
                    postId = entry.arguments?.getString("postId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    // 追问不再单独维护对话页：携带题目 id 跳到 AI 对话 Tab
                    onFollowUp = { questionId ->
                        pendingChatQuestionId = questionId
                        navController.navigate(Screen.AiChat.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

/**
 * 浮动式底部导航栏：圆角、半透明、悬浮于内容之上（用于 Home/Quiz Tab）。
 */
@Composable
private fun FloatingBottomBar(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        TabPill(currentDestination = currentDestination, onNavigate = onNavigate)
    }
}

/** 尚未实现页面的占位。 */
@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title\n（建设中）", style = MaterialTheme.typography.titleMedium)
    }
}
