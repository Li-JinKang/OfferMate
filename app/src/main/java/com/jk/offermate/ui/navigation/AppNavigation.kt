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
import androidx.compose.runtime.remember
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
import com.jk.offermate.ui.followup.FollowUpRoute
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            FloatingBottomBar(
                currentDestination = currentDestination,
                onNavigate = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
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
            composable(Screen.Profile.route) { ProfileRoute(container) }
            composable(
                route = "questions/{postId}",
                arguments = listOf(navArgument("postId") { type = NavType.StringType })
            ) { entry ->
                QuestionsRoute(
                    container = container,
                    postId = entry.arguments?.getString("postId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onFollowUp = { questionId ->
                        navController.navigate("followup/${android.net.Uri.encode(questionId)}")
                    }
                )
            }
            composable(
                route = "followup/{questionId}",
                arguments = listOf(navArgument("questionId") { type = NavType.StringType })
            ) { entry ->
                FollowUpRoute(
                    container = container,
                    questionId = entry.arguments?.getString("questionId").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * 浮动式底部导航栏：圆角、半透明、悬浮于内容之上，整体高度更低。
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
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Screen.bottomItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    FloatingBarItem(
                        screen = screen,
                        selected = selected,
                        onClick = { onNavigate(screen) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingBarItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navItemColor"
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        label = "navItemPill"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 16.dp else 12.dp,
        label = "navItemPadding"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pillColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .height(40.dp)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = screen.label,
            tint = contentColor
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = screen.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
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
