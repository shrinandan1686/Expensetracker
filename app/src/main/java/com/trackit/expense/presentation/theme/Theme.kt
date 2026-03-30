package com.trackit.expense.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TrackItDarkColorScheme = darkColorScheme(
    primary            = TrackItPrimary,
    onPrimary          = DarkOnSurface,
    primaryContainer   = TrackItPrimaryDark,
    onPrimaryContainer = DarkOnSurface,
    secondary          = TrackItSecondary,
    tertiary           = TrackItTertiary,
    background         = DarkBackground,
    onBackground       = DarkOnSurface,
    surface            = DarkSurface,
    onSurface          = DarkOnSurface,
    surfaceVariant     = DarkSurfaceVariant,
    onSurfaceVariant   = DarkOnSurfaceVariant,
    error              = ErrorRed,
)

private val TrackItLightColorScheme = lightColorScheme(
    primary            = TrackItPrimary,
    onPrimary          = LightSurface,
    primaryContainer   = LightSurfaceVariant,
    onPrimaryContainer = LightOnSurface,
    secondary          = TrackItSecondary,
    tertiary           = TrackItTertiary,
    background         = LightBackground,
    onBackground       = LightOnSurface,
    surface            = LightSurface,
    onSurface          = LightOnSurface,
    surfaceVariant     = LightSurfaceVariant,
    onSurfaceVariant   = LightOnSurfaceVariant,
    error              = ErrorRedDark,
)

/**
 * TrackIt Material 3 theme.
 *
 * Supports:
 * - Dark / light mode based on system setting
 * - Dynamic color on Android 12+ (Material You)
 * - Edge-to-edge status bar color sync
 *
 * @param darkTheme Follow system dark mode by default.
 * @param dynamicColor Use Material You dynamic color on Android 12+.
 */
@Composable
fun TrackItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> TrackItDarkColorScheme
        else      -> TrackItLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = TrackItTypography,
        content     = content
    )
}
