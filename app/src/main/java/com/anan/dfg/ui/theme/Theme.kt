package com.anan.dfg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Dynamic colour is deliberately not used: the app should keep its own identity
 * rather than restyle itself around the wallpaper.
 *
 * The register is paper and ink — warm bone ground, near-black text — with three
 * working colours: moss for done, amber for open, clay for overdue.
 */

private val Bone = Color(0xFFF6F2EA)
private val Paper = Color(0xFFFFFFFF)
private val Linen = Color(0xFFEBE4D8)
private val Ink = Color(0xFF1B1915)
private val Graphite = Color(0xFF6E675B)

private val Moss = Color(0xFF2F6B4F)
private val MossSoft = Color(0xFFCBE6D6)
private val MossDeep = Color(0xFF0D2C1E)

private val Amber = Color(0xFF9A6412)
private val AmberSoft = Color(0xFFF7E2BE)

private val Clay = Color(0xFFA43C28)
private val ClaySoft = Color(0xFFF6DBD3)

private val LightScheme = lightColorScheme(
    primary = Moss,
    onPrimary = Color.White,
    primaryContainer = MossSoft,
    onPrimaryContainer = MossDeep,
    secondary = Graphite,
    onSecondary = Color.White,
    secondaryContainer = Linen,
    onSecondaryContainer = Ink,
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = Color(0xFF2E1C00),
    background = Bone,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Linen,
    onSurfaceVariant = Graphite,
    outline = Color(0xFFB6AC9B),
    outlineVariant = Color(0xFFDCD3C4),
    error = Clay,
    onError = Color.White,
    errorContainer = ClaySoft,
    onErrorContainer = Color(0xFF3B0F07),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF86CFA6),
    onPrimary = Color(0xFF0A2C1D),
    primaryContainer = Color(0xFF1F4A36),
    onPrimaryContainer = MossSoft,
    secondary = Color(0xFFB6AC9B),
    onSecondary = Color(0xFF2B2721),
    secondaryContainer = Color(0xFF2C2822),
    onSecondaryContainer = Color(0xFFE9E2D6),
    tertiary = Color(0xFFE8B765),
    onTertiary = Color(0xFF3A2600),
    tertiaryContainer = Color(0xFF54390B),
    onTertiaryContainer = AmberSoft,
    background = Color(0xFF13110E),
    onBackground = Color(0xFFEBE4D8),
    surface = Color(0xFF1C1915),
    onSurface = Color(0xFFEBE4D8),
    surfaceVariant = Color(0xFF2A251E),
    onSurfaceVariant = Color(0xFFAEA595),
    outline = Color(0xFF6E675B),
    outlineVariant = Color(0xFF3A342B),
    error = Color(0xFFE99181),
    onError = Color(0xFF4B1508),
    errorContainer = Color(0xFF6B2214),
    onErrorContainer = ClaySoft,
)

/**
 * The type deliberately mixes registers: names in a serif, the way a written list
 * gets a heading; times in monospace, so they read as instrument values; small
 * labels in letter-spaced caps. All three families ship with the system.
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = FontFamily.Serif,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            letterSpacing = (-0.3).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = FontFamily.Serif,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
        ),
        labelSmall = base.labelSmall.copy(letterSpacing = 1.2.sp),
    )
}

/** Semantic status colours, so done / open / overdue read the same on every screen. */
data class StatusPalette(
    val done: Color,
    val onDone: Color,
    val due: Color,
    val onDue: Color,
    val overdue: Color,
    val onOverdue: Color,
    val idle: Color,
    val onIdle: Color,
)

val LocalStatusPalette = staticCompositionLocalOf {
    StatusPalette(
        done = MossSoft,
        onDone = MossDeep,
        due = AmberSoft,
        onDue = Color(0xFF2E1C00),
        overdue = ClaySoft,
        onOverdue = Color(0xFF3B0F07),
        idle = Linen,
        onIdle = Graphite,
    )
}

object AppTheme {
    val status: StatusPalette
        @Composable @ReadOnlyComposable get() = LocalStatusPalette.current

    /** Monospace time style, shared by the timeline and the status panels. */
    val timeStyle: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.sp,
        )
}

@Composable
fun DfgTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val statusPalette = if (dark) {
        StatusPalette(
            done = Color(0xFF1F4A36),
            onDone = MossSoft,
            due = Color(0xFF54390B),
            onDue = AmberSoft,
            overdue = Color(0xFF6B2214),
            onOverdue = ClaySoft,
            idle = Color(0xFF2A251E),
            onIdle = Color(0xFFAEA595),
        )
    } else {
        LocalStatusPalette.current
    }
    CompositionLocalProvider(LocalStatusPalette provides statusPalette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
