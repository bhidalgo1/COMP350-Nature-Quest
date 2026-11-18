package com.example.naturequesthomescreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.natureobservation.feature.posts.ObservationPost
import com.example.natureobservation.feature.posts.ObservationRepository
import com.example.natureobservation.feature.posts.PostInteractions
import com.example.natureobservation.feature.posts.rememberRemotePhotoBitmap
import kotlin.collections.listOf

class FiltersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val filterOptions = listOf("Plant", "Mammal", "Bird", "Reptile", "Insect")
            var selectedFilters = remember { mutableStateListOf(false, false, false, false, false) }

            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                OutlinedButton(onClick = {
                    val homeIntent =
                        Intent(this@FiltersActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(homeIntent)
                }) {
                    Text("Back")
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Filter posts by plant or animal",
                                style = MaterialTheme.typography.headlineLarge
                            )

                            selectedFilters.forEachIndexed { option, checked ->
                                Row {
                                    Text(
                                        text = filterOptions[option],
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .weight(1f)
                                            .align(Alignment.CenterVertically)
                                    )

                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { selectedFilters[option] = it }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ElevatedButton(
                            onClick = { for (i in selectedFilters.indices) selectedFilters[i] = false },
                            enabled = selectedFilters.any { it }
                        ) {
                            Text("Clear Filters")
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        ElevatedButton(
                            onClick = {
                                setContent {
                                    FilteredPostsScreen(
                                        selectedFilters,
                                        onBack = {
                                            val homeIntent =
                                                Intent(this@FiltersActivity, MainActivity::class.java)
                                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                            startActivity(homeIntent)
                                        }
                                    )
                                }
                            },
                            enabled = selectedFilters.any { it }
                        ) {
                            Text("Apply Filters")
                        }
                    }
                }
            }
        }
    }
}