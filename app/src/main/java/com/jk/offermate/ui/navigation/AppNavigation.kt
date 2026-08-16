package com.jk.offermate.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun OfferMateApp(container: AppContainer) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            NavigationBar {
                Screen.bottomItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeRoute(container, onOpenPost = { postId -> navController.navigate("questions/$postId") })
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

/** 尚未实现页面的占位。 */
@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title\n（建设中）", style = MaterialTheme.typography.titleMedium)
    }
}
