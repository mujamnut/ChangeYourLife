package com.changeyourlife.cyl.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.changeyourlife.cyl.domain.repository.AuthState
import com.changeyourlife.cyl.presentation.auth.AuthRoute
import com.changeyourlife.cyl.presentation.home.HomeRoute
import com.changeyourlife.cyl.presentation.home.HomeSearchRoute
import com.changeyourlife.cyl.presentation.home.HomeViewModel
import com.changeyourlife.cyl.presentation.page.PageEditorRoute

private object Routes {
    const val Auth = "auth"
    const val Home = "home"
    const val HomeSearch = "home/search"
    const val PageEditor = "page"

    fun pageEditor(
        pageId: String,
        targetType: String = "",
        targetId: String = "",
    ): String {
        val baseRoute = "$PageEditor/${Uri.encode(pageId)}"
        val args = listOf(
            "targetType" to targetType,
            "targetId" to targetId,
        ).filter { (_, value) -> value.isNotBlank() }
        if (args.isEmpty()) return baseRoute
        return args.joinToString(
            prefix = "$baseRoute?",
            separator = "&",
        ) { (key, value) -> "$key=${Uri.encode(value)}" }
    }

}

@Composable
fun CylNavHost(
    viewModel: AuthGateViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val startDestination = when (authState) {
        AuthState.Loading -> null
        is AuthState.SignedIn -> Routes.Home
        AuthState.SignedOut -> Routes.Auth
    }

    if (startDestination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.Auth) {
            AuthRoute(
                onAuthenticated = {
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Auth) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.Home) {
            HomeRoute(
                onCreatePage = {},
                onOpenPage = { pageId, targetType, targetId ->
                    navController.navigate(Routes.pageEditor(pageId, targetType, targetId))
                },
                onSearch = {
                    navController.navigate(Routes.HomeSearch) {
                        launchSingleTop = true
                    }
                },
                onLoggedOut = {
                    navController.navigate(Routes.Auth) {
                        popUpTo(Routes.Home) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                viewModel = homeViewModel,
            )
        }
        composable(Routes.HomeSearch) {
            HomeSearchRoute(
                onBack = {
                    navController.popBackStack()
                },
                onOpenPage = { pageId, targetType, targetId ->
                    navController.navigate(Routes.pageEditor(pageId, targetType, targetId))
                },
                viewModel = homeViewModel,
            )
        }
        composable(
            route = "${Routes.PageEditor}/{pageId}?targetType={targetType}&targetId={targetId}",
            arguments = listOf(
                navArgument("pageId") {
                    type = NavType.StringType
                },
                navArgument("targetType") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("targetId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            PageEditorRoute(
                onBack = {
                    navController.popBackStack()
                },
                homeAiState = homeUiState,
                initialSearchTargetType = it.arguments?.getString("targetType").orEmpty(),
                initialSearchTargetId = it.arguments?.getString("targetId").orEmpty(),
                onOpenPage = { pageId, targetType, targetId ->
                    navController.navigate(Routes.pageEditor(pageId, targetType, targetId))
                },
                onSendAiMessage = homeViewModel::sendChatMessage,
                onUndoAiAction = homeViewModel::undoAiAction,
                onHomeAiModeChange = homeViewModel::updateAiChatMode,
                onClearHomeAiHistory = homeViewModel::clearChatHistory,
                onCreateHomeChatSession = homeViewModel::createNewChatSession,
                onDismissHomeAiError = homeViewModel::clearAiChatError,
            )
        }
    }
}
