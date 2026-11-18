package com.example.naturequesthomescreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.natureobservation.feature.posts.ORGANISM_CATEGORIES

/** The teammate's category selection, hosted in the existing app's navigation. */
@Composable
fun FiltersScreen(onBack: () -> Unit, onApplyFiltersClick: (List<String>) -> Unit) {
    BackHandler(onBack = onBack)
    var selectedText by rememberSaveable { mutableStateOf("") }
    val selected = selectedText.split(',').filter { it in ORGANISM_CATEGORIES }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Filter posts", style = MaterialTheme.typography.headlineMedium)
        Text("Choose one or more plant and animal categories.")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                ORGANISM_CATEGORIES.forEach { category ->
                    val checked = category in selected
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { enabled ->
                                val next = selected.toMutableSet()
                                if (enabled) next.add(category) else next.remove(category)
                                selectedText = ORGANISM_CATEGORIES.filter { it in next }.joinToString(",")
                            }
                        ).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category, modifier = Modifier.weight(1f))
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = { selectedText = "" }, enabled = selected.isNotEmpty()) {
                Text("Clear filters")
            }
            Button(onClick = { onApplyFiltersClick(selected) }, enabled = selected.isNotEmpty()) {
                Text("Apply filters")
            }
        }
        Text(
            "Older posts without a category remain in the regular feed.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
