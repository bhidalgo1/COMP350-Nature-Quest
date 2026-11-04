package com.example.naturequesthomescreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.viewmodel.AuthenticateVm

@Composable
fun NavigateToScreen(
    onMapClick: () -> Unit,
    onUploadClick: () -> Unit
) {
    val toScreen = rememberNavController()
    // Share the same authentication state with both forms.
    val authVm: AuthenticateVm = viewModel()
    val startingScreen = remember {
        if (authVm.isUserLoggedIn()) "main screen" else "login"
    }

    fun openHome() {
        authVm.resetState()
        toScreen.navigate("main screen") {
            // Back from home should not reopen a completed sign-in form.
            popUpTo("login") { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = toScreen,
        startDestination = startingScreen
    ) {
        composable("login") {
            LoginScreen(
                onRegisterClick = {
                    authVm.resetState()
                    toScreen.navigate("sign up") { launchSingleTop = true }
                },
                onLoginSuccess = { openHome() },
                viewModel = authVm
            )
        }

        composable("sign up") {
            RegisterScreen(
                onLoginClick = {
                    authVm.resetState()
                    toScreen.popBackStack("login", inclusive = false)
                },
                onRegisterSuccess = { openHome() },
                viewModel = authVm
            )
        }

        composable("main screen") {
            HomeScreen(
                onCameraClick = { toScreen.navigate("camera") },
                onMapClick = onMapClick,
                onUploadClick = onUploadClick,
                onLikedPostsClick = { toScreen.navigate("liked posts") }
            )
        }

        composable("camera") {
            CameraScreen(onBack = { toScreen.popBackStack() })
        }

        composable("liked posts") {
            LikedPostsScreen(onBack = { toScreen.popBackStack() })
        }
    }
}
