package com.example.naturequesthomescreen

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun FiltersScreen(
    onBack: () -> Unit,
    onApplyFiltersClick: () -> Unit
) {
    val filterOptions = listOf("Plant", "Mammal", "Bird", "Reptile", "Insect")
    var selectedFilters = remember { mutableStateListOf(false, false, false, false, false) }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        OutlinedButton(onClick = { onBack() }) {
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
                        onApplyFiltersClick()
                    },
                    enabled = selectedFilters.any { it }
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }
}
