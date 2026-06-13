package com.alignfit.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ─── AlignFit color palette ───────────────────────────────────────────────────
// Calm, modern healthcare/fitness tone built around a soft teal primary with a
// muted blue secondary. Light, low-contrast surfaces keep the app from feeling
// clinical or childish.

private val Teal = Color(0xFF3F8E8C)
private val TealContainer = Color(0xFFCFE9E6)
private val OnTealContainer = Color(0xFF0C3B39)
private val Blue = Color(0xFF52718F)
private val BlueContainer = Color(0xFFDCE8F1)
private val OnBlueContainer = Color(0xFF18313F)
private val BgGray = Color(0xFFF5F8F8)
private val SurfaceWhite = Color(0xFFFFFFFF)
private val SurfaceVar = Color(0xFFE7EDEC)
private val OnSurfaceColor = Color(0xFF1A2322)
private val OnSurfaceVar = Color(0xFF4C5957)
private val OutlineColor = Color(0xFFB2C0BE)
private val OutlineVar = Color(0xFFD4DDDB)
private val ErrorColor = Color(0xFFB3261E)
private val ErrorContainerColor = Color(0xFFF9DEDC)
private val OnErrorContainerColor = Color(0xFF410E0B)

private val AlignFitLightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = Blue,
    onSecondary = Color.White,
    secondaryContainer = BlueContainer,
    onSecondaryContainer = OnBlueContainer,
    background = BgGray,
    onBackground = OnSurfaceColor,
    surface = SurfaceWhite,
    onSurface = OnSurfaceColor,
    surfaceVariant = SurfaceVar,
    onSurfaceVariant = OnSurfaceVar,
    outline = OutlineColor,
    outlineVariant = OutlineVar,
    error = ErrorColor,
    onError = Color.White,
    errorContainer = ErrorContainerColor,
    onErrorContainer = OnErrorContainerColor
)

private val AlignFitShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun AlignFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AlignFitLightColors,
        shapes = AlignFitShapes,
        content = content
    )
}
