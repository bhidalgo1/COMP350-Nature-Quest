package com.example.naturequesthomescreen.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

class AppearanceSettings(
    private val preferences: SharedPreferences
) {
    var darkModeOverride by mutableStateOf<Boolean?>(null)
        private set

    var backgroundArgb by mutableStateOf<Int?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        darkModeOverride =
            if (preferences.contains("dark_mode")) {
                preferences.getBoolean("dark_mode", false)
            } else {
                null
            }

        backgroundArgb =
            if (preferences.contains("background_color")) {
                preferences.getInt("background_color", 0)
            } else {
                null
            }
    }

    fun setDarkMode(enabled: Boolean) {
        darkModeOverride = enabled
        preferences.edit()
            .putBoolean("dark_mode", enabled)
            .apply()
    }

    fun setBackground(color: Color) {
        backgroundArgb = color.toArgb()
        preferences.edit()
            .putInt("background_color", color.toArgb())
            .apply()
    }

    fun resetBackground() {
        backgroundArgb = null
        preferences.edit()
            .remove("background_color")
            .apply()
    }
}

val LocalAppearanceSettings = compositionLocalOf<AppearanceSettings> {
    error("AppearanceSettings must be provided by NatureQuestHomeScreenTheme")
}

@Composable
internal fun rememberAppearanceSettings(): AppearanceSettings {
    val context = LocalContext.current.applicationContext

    val preferences = remember(context) {
        context.getSharedPreferences(
            "nature_quest_appearance",
            Context.MODE_PRIVATE
        )
    }

    val settings = remember(preferences) {
        AppearanceSettings(preferences)
    }

    DisposableEffect(preferences, settings) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                settings.refresh()
            }

        preferences.registerOnSharedPreferenceChangeListener(listener)
        settings.refresh()

        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return settings
}

@Composable
fun currentAppearanceDarkMode(): Boolean {
    val settings = LocalAppearanceSettings.current
    return settings.darkModeOverride ?: isSystemInDarkTheme()
}