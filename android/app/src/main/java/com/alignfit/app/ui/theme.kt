package com.alignfit.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ─── AlignFit monochrome palette ──────────────────────────────────────────────
// Black on white. Hierarchy comes from weight, size and gray steps, not hue.

internal val Ink = Color(0xFF111111)
internal val InkSoft = Color(0xFF6B6B6B)
internal val InkFaint = Color(0xFF9E9E9E)
internal val Paper = Color(0xFFFFFFFF)
internal val Tint = Color(0xFFF4F4F4)      // card / field background
internal val Hairline = Color(0xFFE6E6E6)  // dividers, borders

private val AlignFitColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Tint,
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Tint,
    onSecondaryContainer = Ink,
    tertiary = Ink,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Tint,
    onSurfaceVariant = InkSoft,
    surfaceContainer = Paper,
    surfaceContainerLow = Tint,
    surfaceContainerHigh = Tint,
    outline = Color(0xFFCFCFCF),
    outlineVariant = Hairline,
    error = Ink,
    onError = Paper,
    errorContainer = Tint,
    onErrorContainer = Ink
)

private val AlignFitShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun AlignFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AlignFitColors,
        shapes = AlignFitShapes,
        content = content
    )
}
