package com.jk.offermate.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
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

    // AI 对话页登记进来的输入胶囊内容；非空即当前在对话页，dock 显示堆叠输入胶囊。
    var chatInputContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    // dock 中输入胶囊是否在前（探头切换用），提升到 app 级，切页时保持不重建。
    var dockInputInFront by remember { mutableStateOf(true) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // 底部 dock 只在三个 Tab 页显示（Home/Quiz/AI 对话）；详情页（题目/设置等）不显示。
    val showDock = currentDestination?.route in
        setOf(Screen.Home.route, Screen.Quiz.route, Screen.AiChat.route)
    val onAiChat = currentDestination?.route == Screen.AiChat.route

    // Tab 切换动作
    val navigateTab: (Screen) -> Unit = { screen ->
        // 离开对话页时输入胶囊归位到前台，下次进入默认显示输入
        dockInputInFront = true
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        // dock 高度（含导航栏内边距），作为内容底部留白：让列表能滚到胶囊下方而非被截断。
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val tabDockReserve = navBottom + 80.dp   // 12(下) + 56(胶囊) + 12(上)
        val aiDockReserve = navBottom + 84.dp    // 12(下) + 72(输入/Tab 堆叠)

        // 内容全屏铺开，dock 作为悬浮 overlay 叠在其上（真正浮动，不占布局、不截断内容）。
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(220)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) }
            ) {
                composable(Screen.Home.route) {
                    HomeRoute(
                        container = container,
                        onOpenPost = { postId -> navController.navigate("questions/$postId") },
                        onOpenSettings = { navController.navigate(Screen.Settings.route) },
                        sharedText = sharedText,
                        onSharedTextConsumed = onSharedTextConsumed,
                        contentBottomPadding = tabDockReserve
                    )
                }
                composable(Screen.Quiz.route) {
                    QuizRoute(
                        container,
                        onOpenCategory = { name ->
                            navController.navigate("quizCategory/${android.net.Uri.encode(name)}")
                        },
                        contentBottomPadding = tabDockReserve
                    )
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
                        pendingQuestionId = pendingChatQuestionId,
                        onPendingConsumed = { pendingChatQuestionId = null },
                        registerInputContent = { chatInputContent = it },
                        onResetInputFront = { dockInputInFront = true },
                        contentBottomPadding = aiDockReserve
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

            // 唯一常驻的悬浮 dock：叠在内容之上，仅胶囊本身可见/可点，空白区触摸穿透到下层内容。
            if (showDock) {
                BottomDock(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    currentDestination = currentDestination,
                    onNavigate = navigateTab,
                    // 仅在对话页把输入胶囊叠上来；其余页面 dock 只有 Tab 胶囊。
                    inputContent = if (onAiChat) chatInputContent else null,
                    inputInFront = dockInputInFront,
                    onInputInFrontChange = { dockInputInFront = it }
                )
            }
        }
    }
}

/** 尚未实现页面的占位。 */
@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title\n（建设中）", style = MaterialTheme.typography.titleMedium)
    }
}
