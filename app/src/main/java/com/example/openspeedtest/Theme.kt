package com.example.openspeedtest

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Whether the app is currently dark, and whether to use pure-black OLED colors. */
data class AppLook(val dark: Boolean, val oled: Boolean) {
    val oledBlack: Boolean get() = dark && oled
}

val LocalAppLook = compositionLocalOf { AppLook(dark = false, oled = false) }

@Composable
fun SpeedTestTheme(
    dark: Boolean = isSystemInDarkTheme(),
    oled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val colors = if (dark && oled) {
        // Pure black surfaces: OLED pixels switch off completely.
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF0B0B0B),
            surfaceContainerHigh = Color(0xFF151515),
            surfaceContainerHighest = Color(0xFF1E1E1E),
        )
    } else {
        base
    }
    CompositionLocalProvider(LocalAppLook provides AppLook(dark, oled)) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

// Colors for the two floating buttons at the top (servers / history).
@Composable
fun pillContainerColor(): Color {
    val look = LocalAppLook.current
    return when {
        look.oledBlack -> Color.Black
        look.dark -> Color(0xEB262626)
        else -> Color(0xF2FFFFFF)
    }
}

@Composable
fun pillContentColor(): Color = if (LocalAppLook.current.dark) Color.White else Color(0xFF1C1C1C)

@Composable
fun pillBorder(): BorderStroke? =
    if (LocalAppLook.current.dark) BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)) else null
