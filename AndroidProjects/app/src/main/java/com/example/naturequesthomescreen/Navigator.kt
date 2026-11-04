package com.example.naturequesthomescreen

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.viewmodel.AuthenticateVm
import com.example.naturequesthomescreen.RegisterScreen
import com.example.naturequesthomescreen.LoginScreen

@Composable
fun NavigateToScreen() {
    val toScreen = rememberNavController()
    val authVm: AuthenticateVm = viewModel()
    val startingScreen = if (authVm.isUserLoggedIn()) "main screen" else "login"

    NavHost(
        navController = toScreen,
        startDestination = startingScreen
    ) {
        composable("login") {
            LoginScreen(
                onRegisterClick = {
                    authVm.resetState()
                    toScreen.navigate("sign up")
                },
                onLoginSuccess = {
                    authVm.resetState()
                    toScreen.navigate("main screen") // To the main screen
                }
            )
        }

        composable("sign up") {
            RegisterScreen(
                onLoginClick = {
                    authVm.resetState()
                    toScreen.navigate("login")
                },
                onRegisterSuccess = {
                    authVm.resetState()
                    toScreen.navigate("main screen")
                }
            )
        }

        composable("main screen") {
            // Move screens and stuff here
        }
    }
}