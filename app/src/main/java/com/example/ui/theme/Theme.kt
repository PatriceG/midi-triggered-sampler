package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MpcAmber,
    onPrimary = Color.Black,
    secondary = CyanLed,
    onSecondary = Color.Black,
    tertiary = PanicRed,
    background = ChassisBackground,
    onBackground = Color(0xFFF1F3F5),
    surface = ChassisSurface,
    onSurface = Color(0xFFF1F3F5),
    surfaceVariant = ChassisSurfaceHigh,
    onSurfaceVariant = Color(0xFFB0B4C0),
    outline = ChassisBorder,
    outlineVariant = ChassisBorderBright
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = ChassisBackground.toArgb()
                window.navigationBarColor = ChassisBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
