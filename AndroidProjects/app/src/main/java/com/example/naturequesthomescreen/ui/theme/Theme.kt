package com.example.naturequesthomescreen.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun NatureQuestHomeScreenTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val settings = rememberAppearanceSettings()
    val systemDarkMode = isSystemInDarkTheme()

    val useDarkMode =
        darkTheme ?: settings.darkModeOverride ?: systemDarkMode

    val baseColors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current

            if (useDarkMode) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        useDarkMode -> DarkColorScheme
        else -> LightColorScheme
    }

    val customBackground = if (useDarkMode) null else settings.backgroundArgb?.let { Color(it) }

    val colors = if (customBackground != null) {
        // Select readable text for the chosen background.
        val foreground =
            if (customBackground.luminance() > 0.179f) {
                Color.Black
            } else {
                Color.White
            }

        baseColors.copy(
            background = customBackground,
            onBackground = foreground
        )
    } else {
        baseColors
    }

    val view = LocalView.current
    val window = remember(view) {
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
            ?.window
    }
    if (window != null && !view.isInEditMode) {
        SideEffect {
            val useDarkIcons = colors.background.luminance() > 0.179f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = useDarkIcons
                isAppearanceLightNavigationBars = useDarkIcons
            }
        }
    }

    CompositionLocalProvider(
        LocalAppearanceSettings provides settings,
        LocalAppearanceDarkMode provides useDarkMode
    ) {
        MaterialTheme(colorScheme = colors, typography = Typography) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colors.background,
                contentColor = colors.onBackground,
                content = content
            )
        }
    }
}