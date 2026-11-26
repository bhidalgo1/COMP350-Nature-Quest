package com.example.naturequesthomescreen

import android.widget.Toast
import com.example.useraccount.PublicProfileRepository
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.natureobservation.feature.posts.PhotoFeedScreen
import com.example.natureobservation.feature.posts.normalizeOrganisms
import com.example.viewmodel.AuthenticateVm

@Composable
fun NavigateToScreen(
    onMapClick: () -> Unit,
    onUploadClick: () -> Unit
) {
    val toScreen = rememberNavController()
    val context = LocalContext.current
    val authVm: AuthenticateVm = viewModel()
    var profileNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val startingScreen = remember {
        if (authVm.isUserLoggedIn()) "main screen" else "login"
    }

    fun finishAuthentication(destination: String) {
        authVm.resetState()
        toScreen.navigate(destination) {
            // Discard forms and the previous account's navigation history.
            popUpTo(toScreen.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = toScreen, startDestination = startingScreen) {
        composable("login") {
            val canGoBack = toScreen.previousBackStackEntry != null
            LoginScreen(
                onRegisterClick = {
                    authVm.resetState()
                    toScreen.navigate("sign up") { launchSingleTop = true }
                },
                onLoginSuccess = {
                    profileNotice = null
                    finishAuthentication("main screen")
                },
                viewModel = authVm,
                onBack = if (canGoBack) {
                    {
                        authVm.resetState()
                        toScreen.popBackStack()
                        //Unit
                    }
                } else null
            )
        }

        composable("sign up") {
            RegisterScreen(
                onLoginClick = {
                    authVm.resetState()
                    if (!toScreen.popBackStack("login", inclusive = false)) {
                        toScreen.navigate("login") {
                            popUpTo("sign up") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onRegisterSuccess = {
                    profileNotice = authVm.authState.value.notice
                        ?: "Account created successfully!"
                    Toast.makeText(context, "Account created successfully!", Toast.LENGTH_LONG).show()
                    finishAuthentication("profile")
                },
                viewModel = authVm,
                onBack = {
                    authVm.resetState()
                    toScreen.popBackStack()
                }
            )
        }

        composable("main screen") {
            // Backfill this account's author details when an existing user returns.
            LaunchedEffect(Unit) {
                try {
                    PublicProfileRepository().syncCurrentUser()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // My profile exposes the error and provides a Refresh action.
                }
            }
            HomeScreen(
                onCameraClick = { toScreen.navigate("camera") },
                onMapClick = onMapClick,
                onUploadClick = onUploadClick,
                onLikedPostsClick = { toScreen.navigate("liked posts") },
                onProfileClick = { toScreen.navigate("profile") },
                onFiltersClick = { toScreen.navigate("filters") }
            )
        }

        composable("profile") {
            ProfileScreen(
                notice = profileNotice,
                onViewPosts = { toScreen.navigate("my posts") { launchSingleTop = true } },
                onDismissNotice = { profileNotice = null },
                onBack = {
                    if (!toScreen.popBackStack()) {
                        toScreen.navigate("main screen") {
                            popUpTo(toScreen.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onRegisterClick = {
                    authVm.resetState()
                    toScreen.navigate("sign up")
                },
                onLoginClick = {
                    authVm.resetState()
                    toScreen.navigate("login")
                },
                onLogout = {
                    authVm.logout()
                    profileNotice = null
                    toScreen.navigate("login") {
                        popUpTo(toScreen.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("my posts") {
            ProfileLayout(
                onBack = { toScreen.popBackStack() },
                scrollContent = false
            ) {
                PhotoFeedScreen(ownPostsOnly = true)
            }
        }

        composable("filters") {
            FiltersScreen(
                onBack = { toScreen.popBackStack() },
                onApplyFiltersClick = { categories ->
                    val selected = normalizeOrganisms(categories)
                    toScreen.navigate("filtered posts/" + selected.joinToString(","))
                }
            )
        }

        composable("filtered posts/{categories}") { entry ->
            val selected = normalizeOrganisms(
                entry.arguments?.getString("categories").orEmpty().split(',').filter { it.isNotBlank() }
            )
            FilteredPostsScreen(
                selectedCategories = selected,
                onBack = { toScreen.popBackStack() }
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