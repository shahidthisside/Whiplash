package com.whiplash.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Whiplash Liquid Glass theme.
 *
 * Dark mode is the sole/default theme (section 42), but the specific dark
 * palette is user-selectable (section 59 Appearance) via [ThemeVariant] —
 * see [WhiplashColors.applyVariant]. Tonal layering comes from
 * [WhiplashColors]; typography and shapes come from [WhiplashTypography]
 * and [WhiplashShapes]. Because [WhiplashColors]'s properties are backed by
 * Compose `State`, this color scheme (built here, inside a @Composable)
 * automatically recomposes whenever the selected theme changes — no
 * explicit recomposition wiring needed beyond calling `applyVariant`.
 * Reduced-motion / accessibility preferences are threaded through consuming
 * composables via [GlassTokens] animation durations rather than hardcoded
 * values, so a future settings-driven "reduce motion" toggle (section 55)
 * can scale them down globally.
 */
@Composable
private fun whiplashColorScheme() = darkColorScheme(
    primary = WhiplashColors.accent,
    onPrimary = WhiplashColors.onAccent,
    secondary = WhiplashColors.textSecondary,
    onSecondary = WhiplashColors.background,
    background = WhiplashColors.background,
    onBackground = WhiplashColors.textPrimary,
    surface = WhiplashColors.surfaceGlass,
    onSurface = WhiplashColors.textPrimary,
    surfaceVariant = WhiplashColors.surfaceElevated,
    onSurfaceVariant = WhiplashColors.textSecondary,
    error = WhiplashColors.error,
    onError = WhiplashColors.background,
    outline = WhiplashColors.glassBorder,
    outlineVariant = WhiplashColors.glassBorderStrong,
    scrim = WhiplashColors.scrim,
)

@Composable
fun WhiplashTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = whiplashColorScheme(),
        typography = WhiplashTypography,
        shapes = WhiplashShapes,
        content = content,
    )
}
