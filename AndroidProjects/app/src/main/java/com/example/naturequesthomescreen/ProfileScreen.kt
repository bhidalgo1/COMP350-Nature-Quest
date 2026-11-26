package com.example.naturequesthomescreen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.useraccount.PublicProfileRepository
import com.example.naturequesthomescreen.ui.components.ProfileAvatar
import com.example.naturequesthomescreen.ui.components.rememberAuthorProfile
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.useraccount.AuthenticateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
    onViewPosts: () -> Unit,
    notice: String? = null,
    onDismissNotice: () -> Unit = {}
) {
    val repository = remember { AuthenticateRepository() }
    val user = repository.currentUser()
    val userId = user?.uid
    val isGuest = user?.isAnonymous == true
    val context = LocalContext.current
    val publicRepository = remember { PublicProfileRepository() }
    val publicProfile by rememberAuthorProfile(userId.orEmpty())
    val scope = rememberCoroutineScope()
    var photoBusy by remember(userId) { mutableStateOf(false) }
    var photoError by remember(userId) { mutableStateOf<String?>(null) }
    var publicProfileError by remember(userId) { mutableStateOf<String?>(null) }

    BackHandler(enabled = photoBusy) { }
    BackHandler(enabled = !photoBusy, onBack = onBack)

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !photoBusy) {
            photoBusy = true
            photoError = null
            scope.launch {
                try {
                    publicRepository.changePhoto(context, uri)
                    publicProfileError = null
                    Toast.makeText(context, "Profile picture saved", Toast.LENGTH_SHORT).show()
                } catch (failure: Exception) {
                    if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                    photoError = "Could not save your picture. " +
                            (failure.localizedMessage ?: "Please try again.")
                } finally {
                    photoBusy = false
                }
            }
        }
    }
    var username by remember(userId) { mutableStateOf(user?.displayName.orEmpty()) }
    var usernameLoading by remember(userId) { mutableStateOf(userId != null && !isGuest) }
    var usernameError by remember(userId) { mutableStateOf<String?>(null) }
    var postCount by remember(userId) { mutableStateOf<Long?>(null) }
    var countLoading by remember(userId) { mutableStateOf(userId != null) }
    var countError by remember(userId) { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var confirmSwitchAccount by remember { mutableStateOf(false) }

    LaunchedEffect(userId, refreshKey) {
        val uid = userId ?: return@LaunchedEffect
        usernameError = null
        countError = null
        if (!isGuest) {
            usernameLoading = true
            launch {
                try {
                    val storedName = repository.loadUsername(uid)
                    username = storedName ?: user?.displayName.orEmpty()
                    if (username.isBlank()) {
                        usernameError = "No username is saved for this account yet."
                    }
                } catch (failure: Exception) {
                    if (failure is CancellationException && failure !is TimeoutCancellationException) {
                        throw failure
                    }
                    usernameError = "Could not refresh your username. " +
                            (failure.localizedMessage ?: "Check your connection and try again.")
                } finally {
                    usernameLoading = false
                }
            }
        }
        publicProfileError = null
        launch {
            try {
                publicRepository.syncCurrentUser()
            } catch (failure: Exception) {
                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                publicProfileError = "Could not publish your author details. " +
                        (failure.localizedMessage ?: "Please try Refresh.")
            }
        }
        countLoading = true
        launch {
            try {
                postCount = repository.countPosts(uid)
            } catch (failure: Exception) {
                if (failure is CancellationException && failure !is TimeoutCancellationException) {
                    throw failure
                }
                postCount = null
                countError = "Could not load your post count. " +
                        (failure.localizedMessage ?: "Check your connection and try again.")
            } finally {
                countLoading = false
            }
        }
    }

    if (confirmSwitchAccount) {
        AlertDialog(
            onDismissRequest = { confirmSwitchAccount = false },
            title = { Text("Log in to another account?") },
            text = {
                Text("Your guest posts and likes will not move to an existing account. " +
                        "Choose Create an account instead to keep them with a new account.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSwitchAccount = false
                    onLoginClick()
                }) { Text("Continue to login") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSwitchAccount = false }) { Text("Cancel") }
            }
        )
    }

    ProfileLayout(
        onBack = onBack,
        backLabel = "Home",
        backEnabled = !photoBusy,
        actions = {
            FilledTonalButton(
                onClick = { refreshKey++ },
                enabled = userId != null && !photoBusy && !usernameLoading && !countLoading
            ) { Text("Refresh") }
        }
    ) {
        ProfileHeading("My profile")

        if (notice != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(notice)
                    TextButton(onClick = onDismissNotice) { Text("Dismiss") }
                }
            }
        }

        ProfileCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileAvatar(photoPath = publicProfile.photoPath, size = 80.dp, refreshKey = refreshKey)
                Text(
                    text = when {
                        userId == null -> "Please log in"
                        isGuest -> "Guest"
                        username.isNotBlank() -> username
                        usernameLoading -> "Loading username..."
                        else -> "Username unavailable"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (userId != null) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !photoBusy,
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Text(if (publicProfile.photoPath.isBlank()) "Add profile picture" else "Change profile picture")
                }
                if (publicProfile.photoPath.isNotBlank()) {
                    TextButton(
                        enabled = !photoBusy,
                        onClick = {
                            photoBusy = true
                            photoError = null
                            scope.launch {
                                try {
                                    publicRepository.removePhoto()
                                    publicProfileError = null
                                } catch (failure: Exception) {
                                    if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                                    photoError = "Could not remove your picture. " +
                                            (failure.localizedMessage ?: "Please try again.")
                                } finally {
                                    photoBusy = false
                                }
                            }
                        }
                    ) { Text("Remove picture") }
                }
                if (photoBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Saving picture...")
                }
                photoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                publicProfileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        usernameError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (userId != null) {
            ProfileCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Posts", style = MaterialTheme.typography.titleMedium)
                    if (countLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = postCount?.toString() ?: "Unavailable",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onViewPosts,
                    enabled = !photoBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View my posts")
                }
            }
            countError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        if (userId != null) OwnSocialSection(userId, publicProfile.bio)

        when {
            userId == null -> Button(onClick = onLoginClick) { Text("Log in") }
            isGuest -> {
                Text("Create an account to keep your guest posts and likes and log in again later.")
                Button(onClick = onRegisterClick, enabled = !photoBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Create an account")
                }
                OutlinedButton(
                    onClick = { confirmSwitchAccount = true },
                    enabled = !photoBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Log in to an existing account") }
            }
            else -> OutlinedButton(onClick = onLogout, enabled = !photoBusy, modifier = Modifier.fillMaxWidth()) {
                Text("Log out")
            }
        }
    }
}