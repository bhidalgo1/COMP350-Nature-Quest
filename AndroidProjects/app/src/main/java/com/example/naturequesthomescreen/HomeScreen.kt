package com.example.naturequesthomescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.natureobservation.feature.posts.PhotoFeedScreen

@Composable
fun HomeScreen(
    onCameraClick: () -> Unit,
    onMapClick: () -> Unit,
    onUploadClick: () -> Unit,
    onLikedPostsClick: () -> Unit,
) {
    // These font changes apply to the home screen and its feed.
    val homeTypography = MaterialTheme.typography.copy(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        ),
        headlineSmall = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.SansSerif
        ),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.SansSerif
        )
    )

    MaterialTheme(typography = homeTypography) {
        val colors = MaterialTheme.colorScheme

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,

            // These actions stay visible while the feed scrolls.
            bottomBar = {
                BottomAppBar(
                    containerColor = colors.surfaceVariant,
                    contentColor = colors.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onCameraClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Camera",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(
                            onClick = onMapClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Map",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(
                            onClick = onUploadClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "New Post",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.primaryContainer,
                                colors.background
                            )
                        )
                    )
                    .padding(innerPadding)
            ) {
                // App heading and existing liked-posts menu.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 12.dp,
                            top = 20.dp,
                            bottom = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Nature Quest",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.onPrimaryContainer
                        )

                        Text(
                            text = "Explore. Capture. Share.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onPrimaryContainer
                        )
                    }

                    HomeMenu(
                        onLikedPostsClick = onLikedPostsClick
                    )
                }

                // Give the feed the remaining screen height.
                // Its own LazyColumn handles scrolling.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    PhotoFeedScreen()
                }
            }
        }
    }
}