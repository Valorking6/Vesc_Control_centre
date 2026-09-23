package com.example.vesccontrolcentre.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val VescCyan = Color(0xFF00E5FF)
private val VescGreen = Color(0xFF00E676)
private val VescDarkSurface = Color(0xFF121824)
private val VescDarkSurfaceVariant = Color(0xFF1B2433)
private val VescDarkBackground = Color(0xFF0D1117)

private val DarkColorScheme = darkColorScheme(
    primary = VescCyan,
    secondary = VescCyan,
    tertiary = VescGreen,
    background = VescDarkBackground,
    surface = VescDarkSurface,
    surfaceVariant = VescDarkSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00ACC1),
    secondary = Color(0xFF00ACC1),
    tertiary = Color(0xFF00C853),
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECEF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF121824),
    onSurface = Color(0xFF121824),
    onSurfaceVariant = Color(0xFF1B2433)
)

@Composable
fun VescControlCentreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}