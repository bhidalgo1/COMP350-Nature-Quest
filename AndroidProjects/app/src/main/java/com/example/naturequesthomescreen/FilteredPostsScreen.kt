package com.example.naturequesthomescreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.natureobservation.feature.posts.PhotoFeedScreen

/** Reuse the current post cards, map links, interactions, and live deletion updates. */
@Composable
fun FilteredPostsScreen(selectedCategories: List<String>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Back to filters")
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PhotoFeedScreen(organismFilters = selectedCategories)
        }
    }
}
