package com.example.naturequesthomescreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.naturequesthomescreen.ui.theme.LocalAppearanceSettings
import com.example.naturequesthomescreen.ui.theme.currentAppearanceDarkMode

private data class BackgroundChoice(
    val name: String,
    val color: Color
)

private val backgroundChoices = listOf(
    BackgroundChoice("Red", Color(0xFFFF0000)),
    BackgroundChoice("Yellow", Color(0xFFFFFF00)),
    BackgroundChoice("Blue", Color(0xFF0000FF)),
    BackgroundChoice("Orange", Color(0xFFFF8000)),
    BackgroundChoice("Green", Color(0xFF00FF00)),
    BackgroundChoice("Purple", Color(0xFF8000FF)),
    BackgroundChoice("White", Color.White),
    BackgroundChoice("Gray", Color(0xFF808080)),
    BackgroundChoice("Black", Color.Black)
)

@Composable
fun AppearanceControls() {
    val settings = LocalAppearanceSettings.current
    val darkMode = currentAppearanceDarkMode()

    var showColorPicker by rememberSaveable {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Dark mode")

                Switch(
                    checked = darkMode,
                    onCheckedChange = settings::setDarkMode
                )
            }

            TextButton(
                onClick = { showColorPicker = true }
            ) {
                Text("Background color")
            }
        }
    }

    if (showColorPicker) {
        BackgroundColorDialog(
            initialColor = settings.backgroundArgb?.let { Color(it) }
                ?: MaterialTheme.colorScheme.background,
            onDismiss = {
                showColorPicker = false
            },
            onApply = { color ->
                settings.setBackground(color)
                showColorPicker = false
            },
            onReset = {
                settings.resetBackground()
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun BackgroundColorDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
    onReset: () -> Unit
) {
    var selectedArgb by rememberSaveable {
        mutableStateOf(initialColor.toArgb())
    }

    val selectedName = backgroundChoices
        .firstOrNull { it.color.toArgb() == selectedArgb }
        ?.name ?: "Current color"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Background color")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tap a color, then choose Apply.")

                // Display three color choices per row.
                backgroundChoices.chunked(3).forEach { choices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        choices.forEach { choice ->
                            val selected =
                                selectedArgb == choice.color.toArgb()

                            OutlinedButton(
                                onClick = {
                                    selectedArgb = choice.color.toArgb()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                ),
                                contentPadding =
                                    androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 2.dp,
                                        vertical = 12.dp
                                    )
                            ) {
                                Column(
                                    horizontalAlignment =
                                        Alignment.CenterHorizontally,
                                    verticalArrangement =
                                        Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(choice.color)
                                        )
                                    }

                                    Text(
                                        text = choice.name,
                                        style =
                                            MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                Text("Selected: $selectedName")

                TextButton(onClick = onReset) {
                    Text("Default background")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(Color(selectedArgb))
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}