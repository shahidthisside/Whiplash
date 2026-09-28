package com.whiplash.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * App-wide themes. Each one changes every surface, text and icon colour.
 * Themes marked [usesAccentChoice] take their highlight colour from the
 * separate "Accent colour" setting ([ThemeVariant]); the others (Catppuccin,
 * Nord, Rosé Pine Dawn) are fixed designs with their own accent.
 *
 * Whatever the source, [resolvePalette] passes every foreground colour
 * through [ensureContrast], so text and icons always stay readable.
 */
enum class AppTheme(
    val displayName: String,
    val description: String,
    val usesAccentChoice: Boolean,
) {
    DARK("Dark", "The original graphite look", true),
    OLED("OLED Black", "True black, easy on AMOLED batteries", true),
    LIGHT("Light", "Bright paper-white surfaces", true),
    LIQUID_GLASS("Liquid Glass", "See-through glass that bends what's behind it", true),
    CATPPUCCIN("Catppuccin Mocha", "Soft pastel on warm dark", false),
    NORD("Nord", "Cool arctic blue-grey", false),
    ROSE_PINE_DAWN("Rosé Pine Dawn", "Warm light with muted rose", false),
    CUSTOM("Custom", "Your own background and accent", false),
}

/**
 * User colours: the Custom theme's background and accent, plus the Liquid
 * Glass theme's background choice (and its custom colour).
 */
data class CustomThemeColors(
    val background: Color = Color(0xFF101418),
    val accent: Color = Color(0xFF7FB8FF),
    val glassBackground: GlassBackground = GlassBackground.NOW_PLAYING,
    val glassColor: Color = Color(0xFF1A2230),
    /** The "Custom" choice in the Accent colour setting (Dark, OLED, Light, Liquid Glass). */
    val accentColor: Color = Color(0xFF5AC8FA),
)

/**
 * What sits behind Liquid Glass. "Now playing" is the current cover, blurred
 * and dimmed like Apple Music's backdrop; the rest are calm solid tones (a
 * flat colour under clear glass reads as real glass, a busy gradient doesn't).
 */
enum class GlassBackground(val displayName: String, val color: Color?) {
    NOW_PLAYING("Now playing", null),
    GRAPHITE("Graphite", Color(0xFF0E0F12)),
    MIDNIGHT("Midnight", Color(0xFF0A1322)),
    FOREST("Forest", Color(0xFF0C1712)),
    PLUM("Plum", Color(0xFF170F1C)),
    PEARL("Pearl", Color(0xFFE9EAEE)),
    CUSTOM("Custom", null),
}

/** The solid base colour under Liquid Glass for [c]'s choice. */
fun glassBaseColor(c: CustomThemeColors): Color = when (c.glassBackground) {
    GlassBackground.NOW_PLAYING -> Color(0xFF101114)
    GlassBackground.CUSTOM -> readableBackground(c.glassColor.copy(alpha = 1f))
    else -> c.glassBackground.color!!
}

/** WCAG contrast ratio between two opaque colours (1..21). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/**
 * Returns [fg], moved just far enough toward black or white (whichever the
 * background is further from) to reach [minRatio] against [bg]. Colours that
 * already pass are returned unchanged, so designed palettes keep their hue.
 */
fun ensureContrast(fg: Color, bg: Color, minRatio: Float): Color {
    val opaqueFg = fg.copy(alpha = 1f)
    if (contrastRatio(opaqueFg, bg) >= minRatio) return fg
    val target = if (bg.luminance() > 0.4f) Color.Black else Color.White
    var lo = 0f
    var hi = 1f
    repeat(14) {
        val mid = (lo + hi) / 2f
        if (contrastRatio(lerp(opaqueFg, target, mid), bg) >= minRatio) hi = mid else lo = mid
    }
    return lerp(opaqueFg, target, hi).copy(alpha = fg.alpha)
}

/** Moves [c] away from [ink] (toward its opposite) until it reaches [minRatio] against it. */
private fun ensureContrastAway(c: Color, ink: Color, minRatio: Float): Color {
    val target = if (ink.luminance() > 0.4f) Color.Black else Color.White
    var lo = 0f
    var hi = 1f
    repeat(14) {
        val mid = (lo + hi) / 2f
        if (contrastRatio(lerp(c, target, mid), ink) >= minRatio) hi = mid else lo = mid
    }
    return lerp(c, target, hi)
}

/**
 * A user-picked background moved just enough toward black (or white, if it's
 * light) that body text can reach a comfortable 12:1 on it and on the slightly
 * raised surfaces derived from it. Mid-brightness colours such as a saturated
 * blue can't hold readable text in any colour, so they get deepened.
 */
fun readableBackground(bg: Color): Color {
    val toward = if (bg.luminance() > 0.4f) Color.White else Color.Black
    var lo = 0f
    var hi = 1f
    if (contrastRatio(inkOn(bg), bg) >= 12f) return bg
    repeat(14) {
        val mid = (lo + hi) / 2f
        val c = lerp(bg, toward, mid)
        if (contrastRatio(inkOn(c), c) >= 12f) hi = mid else lo = mid
    }
    return lerp(bg, toward, hi)
}

/** Black or white, whichever reads better on [bg]. */
fun inkOn(bg: Color): Color =
    if (contrastRatio(Color.Black, bg) >= contrastRatio(Color.White, bg)) Color(0xFF111114) else Color.White

/** Accent for light backgrounds: neutral accents become ink; coloured ones deepen slightly. */
private fun lightAccent(v: ThemeVariant, custom: CustomThemeColors): Color = when (v) {
    ThemeVariant.CLASSIC, ThemeVariant.PURE_MONO -> Color(0xFF1C1C1E)
    // The user's own colour, used as picked; the contrast pass deepens it only if needed.
    ThemeVariant.CUSTOM -> custom.accentColor.copy(alpha = 1f)
    else -> lerp(v.palette.accent, Color.Black, 0.35f)
}

/** Builds a full palette from a background, accent and base text colour. */
private fun derivePalette(
    background: Color,
    accent: Color,
    text: Color = inkOn(background),
    surfaceStep: Float = 0.05f,
): GlassPalette {
    val light = background.luminance() > 0.4f
    // Light themes step surfaces toward white-ish ink shading, dark toward text.
    val shade = if (light) Color.Black else Color.White
    val edge = if (light) Color.Black else Color.White
    return GlassPalette(
        background = background,
        surfaceGlass = lerp(background, shade, surfaceStep),
        surfaceElevated = lerp(background, shade, surfaceStep * 1.7f),
        surfaceSheet = if (light) lerp(background, Color.White, 0.6f) else lerp(background, shade, surfaceStep * 2.4f),
        textPrimary = text,
        textSecondary = lerp(text, background, 0.30f),
        textTertiary = lerp(text, background, 0.48f),
        textDisabled = lerp(text, background, 0.66f),
        accent = accent,
        onAccent = inkOn(accent),
        glassBorder = edge.copy(alpha = if (light) 0.10f else 0.12f),
        glassHighlight = edge.copy(alpha = if (light) 0.06f else 0.08f),
        glassBorderStrong = edge.copy(alpha = if (light) 0.18f else 0.20f),
        error = if (light) Color(0xFFC0392B) else Color(0xFFE5877E),
        success = if (light) Color(0xFF2E7D4F) else Color(0xFF8FBF9A),
        warning = if (light) Color(0xFF9A6A00) else Color(0xFFD8B778),
        scrim = Color.Black.copy(alpha = if (light) 0.35f else 0.6f),
    )
}

/**
 * The palette for [theme]. [accentVariant] supplies the accent for themes
 * that use the Accent colour setting; [custom] feeds the Custom theme.
 */
fun resolvePalette(theme: AppTheme, accentVariant: ThemeVariant, custom: CustomThemeColors): GlassPalette {
    val chosen = if (accentVariant == ThemeVariant.CUSTOM) {
        custom.accentColor.copy(alpha = 1f).let { c -> ThemeVariant.CLASSIC.palette.copy(accent = c, onAccent = inkOn(c)) }
    } else {
        accentVariant.palette
    }
    val raw = when (theme) {
        // Dark keeps the Accent colour setting's full tinted palette, exactly as before.
        AppTheme.DARK -> chosen
        AppTheme.OLED -> chosen.copy(
            background = Color.Black,
            surfaceGlass = Color(0xFF0E0E0F),
            surfaceElevated = Color(0xFF161618),
            surfaceSheet = Color(0xFF1B1B1D),
        )
        // The neutral accents (a pale cream/white meant for dark pages) become
        // near-black ink on light, instead of being darkened into mud grey.
        AppTheme.LIGHT -> derivePalette(Color(0xFFF4F4F6), lightAccent(accentVariant, custom), Color(0xFF15151A), 0.045f)
            .copy(surfaceSheet = Color(0xFFFFFFFF))
        // Deep blue-black base; the glass layers and wallpaper sit over it.
        AppTheme.LIQUID_GLASS -> glassBaseColor(custom).let { base ->
            val light = base.luminance() > 0.4f
            derivePalette(base, if (light) lightAccent(accentVariant, custom) else chosen.accent, surfaceStep = 0.07f)
        }
        AppTheme.CATPPUCCIN -> GlassPalette(
            background = Color(0xFF1E1E2E), surfaceGlass = Color(0xFF26263A), surfaceElevated = Color(0xFF313244),
            surfaceSheet = Color(0xFF2A2B3C), textPrimary = Color(0xFFCDD6F4), textSecondary = Color(0xFFBAC2DE),
            textTertiary = Color(0xFF9399B2), textDisabled = Color(0xFF6C7086), accent = Color(0xFFCBA6F7),
            onAccent = Color(0xFF1E1E2E), glassBorder = Color(0x26CDD6F4), glassHighlight = Color(0x14CDD6F4),
            glassBorderStrong = Color(0x40CDD6F4), error = Color(0xFFF38BA8), success = Color(0xFFA6E3A1),
            warning = Color(0xFFF9E2AF),
        )
        AppTheme.NORD -> GlassPalette(
            background = Color(0xFF2E3440), surfaceGlass = Color(0xFF353C4A), surfaceElevated = Color(0xFF3B4252),
            surfaceSheet = Color(0xFF3B4252), textPrimary = Color(0xFFECEFF4), textSecondary = Color(0xFFD8DEE9),
            textTertiary = Color(0xFFA3ACBD), textDisabled = Color(0xFF6B7488), accent = Color(0xFF88C0D0),
            onAccent = Color(0xFF2E3440), glassBorder = Color(0x26ECEFF4), glassHighlight = Color(0x14ECEFF4),
            glassBorderStrong = Color(0x40ECEFF4), error = Color(0xFFBF616A), success = Color(0xFFA3BE8C),
            warning = Color(0xFFEBCB8B),
        )
        AppTheme.ROSE_PINE_DAWN -> GlassPalette(
            background = Color(0xFFFAF4ED), surfaceGlass = Color(0xFFF4EDE6), surfaceElevated = Color(0xFFF2E9E1),
            surfaceSheet = Color(0xFFFFFAF3), textPrimary = Color(0xFF575279), textSecondary = Color(0xFF6E6A86),
            textTertiary = Color(0xFF797593), textDisabled = Color(0xFF9893A5), accent = Color(0xFFB4637A),
            onAccent = Color(0xFFFFFAF3), glassBorder = Color(0x1F575279), glassHighlight = Color(0x10575279),
            glassBorderStrong = Color(0x33575279), error = Color(0xFFB4637A), success = Color(0xFF286983),
            warning = Color(0xFFEA9D34), scrim = Color(0x59000000),
        )
        AppTheme.CUSTOM -> derivePalette(readableBackground(custom.background.copy(alpha = 1f)), custom.accent.copy(alpha = 1f))
    }
    return raw.withGuaranteedContrast()
}

/**
 * Nudges every foreground token until it is readable on the darkest/lightest
 * surface it can be drawn on: body text 4.5:1, secondary 3:1 and the accent
 * 3:1 (it's used for icons and large controls), and text on the accent 4.5:1.
 */
fun GlassPalette.withGuaranteedContrast(): GlassPalette {
    // Check against whichever surface is closest to the text (worst case).
    val surfaces = listOf(background, surfaceGlass, surfaceElevated, surfaceSheet)
    fun fix(fg: Color, ratio: Float): Color =
        surfaces.fold(fg) { c, s -> ensureContrast(c, s, ratio) }
    var accentFixed = fix(accent, 3f)
    var onAccentFixed = ensureContrast(onAccent, accentFixed, 4.5f)
    if (contrastRatio(onAccentFixed, accentFixed) < 4.5f) {
        // Mid-tone accent: no ink colour reaches 4.5:1, so move the accent
        // itself away from its ink (darker under white text, lighter under black).
        // Ink on the background's side, so pushing the accent away from it
        // also pushes it away from the background (keeps the 3:1 above).
        onAccentFixed = if (background.luminance() > 0.4f) Color.White else Color(0xFF111114)
        accentFixed = ensureContrastAway(accentFixed, onAccentFixed, 4.5f)
    }
    return copy(
        textPrimary = fix(textPrimary, 7f),
        textSecondary = fix(textSecondary, 4.5f),
        textTertiary = fix(textTertiary, 3f),
        textDisabled = fix(textDisabled, 1.8f),
        accent = accentFixed,
        onAccent = onAccentFixed,
        error = fix(error, 3f),
        success = fix(success, 3f),
        warning = fix(warning, 3f),
    )
}

/** True when [palette]'s background is light (dark icons in the status bar, etc.). */
val GlassPalette.isLight: Boolean get() = background.luminance() > 0.4f

/**
 * [c] adjusted (only if needed) to stay visible as an icon or label colour
 * on the current page and card surfaces, e.g. pastel section tints in a
 * light theme.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun readableTint(c: Color, minRatio: Float = 3f): Color =
    ensureContrast(ensureContrast(c, WhiplashColors.background, minRatio), WhiplashColors.surfaceElevated, minRatio)
