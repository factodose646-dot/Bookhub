package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val BookHubLightColorScheme = lightColorScheme(
    primary = Emerald700,
    onPrimary = Cream50,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald950,
    secondary = Gold400,
    onSecondary = Emerald950,
    secondaryContainer = Gold100,
    onSecondaryContainer = Ink900,
    tertiary = Emerald600,
    onTertiary = Cream50,
    background = Cream100,
    onBackground = Ink900,
    surface = Cream50,
    onSurface = Ink900,
    surfaceVariant = Cream200,
    onSurfaceVariant = Ink500,
    outline = Cream300,
    outlineVariant = Emerald100,
)

private val BookHubDarkColorScheme = darkColorScheme(
    primary = Emerald600,
    onPrimary = Cream50,
    primaryContainer = Emerald800,
    onPrimaryContainer = Emerald100,
    secondary = Gold400,
    onSecondary = Emerald950,
    secondaryContainer = Emerald800,
    onSecondaryContainer = Gold100,
    tertiary = Emerald500,
    onTertiary = Emerald950,
    background = Emerald950,
    onBackground = Cream50,
    surface = Emerald900,
    onSurface = Cream50,
    surfaceVariant = Emerald800,
    onSurfaceVariant = Emerald100,
    outline = Emerald700,
)

val BookHubShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun BookHubTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) BookHubDarkColorScheme else BookHubLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BookHubTypography,
        shapes = BookHubShapes,
        content = content,
    )
}
