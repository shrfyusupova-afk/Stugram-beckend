package com.stugram.app.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stugram.app.core.storage.TokenManager
import com.stugram.app.ui.auth.AuthScreen
import com.stugram.app.ui.auth.components.LoadingOverlay
import com.stugram.app.ui.home.CameraScreen
import com.stugram.app.ui.home.ChatDetailScreen
import com.stugram.app.ui.home.GroupChatDetailScreen
import com.stugram.app.ui.home.HomeScreen
import com.stugram.app.ui.home.PostDetailScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Messages : Screen("messages")
    object ChatDetail : Screen("chat_detail/{conversationId}/{userName}/{isRequest}") {
        fun createRoute(conversationId: String, userName: String, isRequest: Boolean = false) =
            "chat_detail/${Uri.encode(conversationId)}/${Uri.encode(userName)}/$isRequest"
    }
    object GroupChatDetail : Screen("group_chat_detail/{groupId}/{groupName}") {
        fun createRoute(groupId: String, groupName: String) = "group_chat_detail/${Uri.encode(groupId)}/${Uri.encode(groupName)}"
    }
    object PostDetail : Screen("post_detail/{postId}") {
        fun createRoute(postId: String) = "post_detail/${Uri.encode(postId)}"
    }
}

@Composable
fun AuthNavGraph(
    navController: NavHostController = rememberNavController(),
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        // Global silliq o'tish animatsiyalari
        enterTransition = { fadeIn(tween(400)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(400)) },
        exitTransition = { fadeOut(tween(400)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(400)) },
        popEnterTransition = { fadeIn(tween(400)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(400)) },
        popExitTransition = { fadeOut(tween(400)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(400)) }
    ) {
        composable(route = Screen.Splash.route) {
            SplashGate(
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Auth.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(400)) }
        ) {
            AuthScreen(onNavigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Auth.route) { inclusive = true }
                }
            })
        }

        composable(
            route = Screen.Home.route
        ) {
            HomeScreen(
                isDarkMode = isDarkMode,
                onThemeChange = onThemeChange,
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToChat = { conversationId, userName, isRequest ->
                    navController.navigate(Screen.ChatDetail.createRoute(conversationId, userName, isRequest))
                },
                onNavigateToGroupChat = { groupId, groupName ->
                    navController.navigate(Screen.GroupChatDetail.createRoute(groupId, groupName))
                },
                onNavigateToPost = { postId ->
                    navController.navigate(Screen.PostDetail.createRoute(postId))
                }
            )
        }

        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("userName") { type = NavType.StringType },
                navArgument("isRequest") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val userName = backStackEntry.arguments?.getString("userName") ?: ""
            val isRequest = backStackEntry.arguments?.getBoolean("isRequest") ?: false
            
            // Back handler: ChatDetail -> Messages (Xabarlar) bo'limi
            BackHandler {
                navController.popBackStack()
            }

            ChatDetailScreen(
                conversationId = conversationId,
                userName = userName, 
                isRequest = isRequest, 
                onBack = { navController.popBackStack() }, 
                isDarkMode = isDarkMode,
                onThemeChange = onThemeChange,
                onNavigateToChat = { targetConversationId, targetUserName, isRequestTarget ->
                    navController.navigate(Screen.ChatDetail.createRoute(targetConversationId, targetUserName, isRequestTarget))
                },
                onNavigateToGroupChat = { targetGroupId, targetGroupName ->
                    navController.navigate(Screen.GroupChatDetail.createRoute(targetGroupId, targetGroupName))
                }
            )
        }

        composable(
            route = Screen.GroupChatDetail.route,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType },
                navArgument("groupName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val groupName = backStackEntry.arguments?.getString("groupName") ?: ""
            
            BackHandler {
                navController.popBackStack()
            }

            GroupChatDetailScreen(
                groupId = groupId,
                groupName = groupName, 
                onBack = { navController.popBackStack() }, 
                isDarkMode = isDarkMode,
                onThemeChange = onThemeChange,
                onNavigateToChat = { targetConversationId, targetUserName, isRequestTarget ->
                    navController.navigate(Screen.ChatDetail.createRoute(targetConversationId, targetUserName, isRequestTarget))
                },
                onNavigateToGroupChat = { targetGroupId, targetGroupName ->
                    navController.navigate(Screen.GroupChatDetail.createRoute(targetGroupId, targetGroupName))
                }
            )
        }

        composable(
            route = Screen.PostDetail.route,
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            
            PostDetailScreen(
                postId = postId,
                onBack = { navController.popBackStack() },
                isDarkMode = isDarkMode,
                onThemeChange = onThemeChange
            )
        }
    }
}

@Composable
private fun SplashGate(
    onNavigateToAuth: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val tokenManager = remember { TokenManager(context) }
    val sessions by produceState<List<TokenManager.StoredSession>?>(initialValue = null, tokenManager) {
        tokenManager.sessions.collect { value = it }
    }
    var minimumDelayComplete by remember { mutableStateOf(false) }
    var navigationDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2800)
        minimumDelayComplete = true
    }

    LaunchedEffect(minimumDelayComplete, sessions) {
        if (!minimumDelayComplete || navigationDone || sessions == null) return@LaunchedEffect
        navigationDone = true
        if (sessions!!.isNotEmpty()) onNavigateToHome() else onNavigateToAuth()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        LoadingOverlay(fullScreen = true, message = "Loading your account")
    }
}
