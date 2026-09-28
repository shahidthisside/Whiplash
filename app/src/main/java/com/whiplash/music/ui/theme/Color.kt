package com.whiplash.music.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Liquid Glass color tokens.
 *
 * Per CLAUDE.md section 41, the visual identity must come from dark surfaces,
 * transparency, blur, depth, light/reflection, typography and motion —
 * explicitly NOT from purple/blue/pink/rainbow "AI-style" gradients. This
 * constraint applies to every [ThemeVariant] below, not just the default:
 * each variant is a different dark, neutral tonal palette with a single
 * restrained accent color, never a gradient system.
 *
 * Every property here is backed by Compose [androidx.compose.runtime.State]
 * (via `mutableStateOf`) rather than being a plain `val` constant. This
 * means every one of the ~80 existing call sites across the app that read
 * `WhiplashColors.xxx` (e.g. `WhiplashColors.background`, in FullPlayerScreen,
 * GlassMiniPlayer, QueueContent, etc.) automatically becomes reactive to
 * theme changes with ZERO call-site changes required — Compose's snapshot
 * system recomposes any composable that reads a `State` when it changes.
 * [applyVariant] is the single function that mutates all of these at once;
 * it is called once at app startup (from the persisted setting) and again
 * any time the user picks a new theme in Settings > Appearance.
 */
/** Non-null inside a subtree that must use a specific palette (e.g. the always-dark full player). */
val LocalPaletteOverride = androidx.compose.runtime.staticCompositionLocalOf<GlassPalette?> { null }

object WhiplashColors {

    /**
     * The app-wide palette. Composables read colours through the getters
     * below, which honour [LocalPaletteOverride] first, so one subtree (the
     * full player) can render in a different palette than the rest.
     */
    var palette by mutableStateOf(ThemeVariant.CLASSIC.palette)
        private set

    /** The palette in effect at this point of the composition. */
    val current: GlassPalette
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalPaletteOverride.current ?: palette

    val background: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.background
    val surfaceGlass: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.surfaceGlass
    val surfaceElevated: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.surfaceElevated
    val surfaceSheet: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.surfaceSheet
    val textPrimary: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.textPrimary
    val textSecondary: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.textSecondary
    val textTertiary: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.textTertiary
    val textDisabled: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.textDisabled
    val accent: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.accent
    val onAccent: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.onAccent
    val glassBorder: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.glassBorder
    val glassHighlight: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.glassHighlight
    val glassBorderStrong: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.glassBorderStrong
    val error: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.error
    val success: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.success
    val warning: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.warning
    val scrim: Color
        @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
        get() = current.scrim

    // ---- Theme state (see Themes.kt) ----
    /** The app-wide theme. Accent choice and custom colours feed into it. */
    var theme by mutableStateOf(AppTheme.DARK)
        private set
    var accentVariant by mutableStateOf(ThemeVariant.CLASSIC)
        private set
    var customColors by mutableStateOf(CustomThemeColors())
        private set
    /** Liquid Glass tint strength, 0 = clear glass, 1 = frosted and nearly solid. */
    var glassOpacity by mutableStateOf(DEFAULT_GLASS_OPACITY)
        private set
    /** True when the current background is light (dark status bar icons, etc.). */
    var isLight by mutableStateOf(false)
        private set
    val isGlass: Boolean get() = theme == AppTheme.LIQUID_GLASS

    /** Sets whichever parts are given and re-resolves the whole palette. */
    fun applyTheme(
        theme: AppTheme = this.theme,
        accent: ThemeVariant = this.accentVariant,
        custom: CustomThemeColors = this.customColors,
        glassOpacity: Float = this.glassOpacity,
    ) {
        this.theme = theme
        this.accentVariant = accent
        this.customColors = custom
        this.glassOpacity = glassOpacity.coerceIn(0f, 1f)
        applyPalette(resolvePalette(theme, accent, custom))
    }

    /** Kept for existing callers: changes only the accent colour. */
    fun applyVariant(variant: ThemeVariant) = applyTheme(accent = variant)

    private fun applyPalette(p: GlassPalette) {
        isLight = p.isLight
        palette = p
    }

    /**
     * A surface [amount] of the way from the background toward the text
     * colour. Use instead of lerp(background, Color.White, x): in light
     * themes that would make "raised" cards invisible white-on-white.
     */
    @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
    fun tone(amount: Float): Color = androidx.compose.ui.graphics.lerp(background, textPrimary, amount)

    const val DEFAULT_GLASS_OPACITY: Float = 0.35f
}

/** Immutable palette definition backing one [ThemeVariant]. */
data class GlassPalette(
    val background: Color,
    val surfaceGlass: Color,
    val surfaceElevated: Color,
    val surfaceSheet: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val accent: Color,
    val onAccent: Color,
    val glassBorder: Color = Color(0x1FFFFFFF),
    val glassHighlight: Color = Color(0x14FFFFFF),
    val glassBorderStrong: Color = Color(0x33FFFFFF),
    val error: Color = Color(0xFFE5877E),
    val success: Color = Color(0xFF8FBF9A),
    val warning: Color = Color(0xFFD8B778),
    val scrim: Color = Color(0x99000000),
)

/**
 * Selectable Appearance themes (CLAUDE.md section 59 Appearance settings).
 * All are dark, neutral-toned Liquid Glass palettes per section 41/42 — the
 * differences are the base tonal temperature and the single accent color,
 * never a gradient or saturated multi-color scheme.
 */
enum class ThemeVariant(val displayName: String, val palette: GlassPalette) {
    CLASSIC(
        displayName = "Classic Graphite",
        palette = GlassPalette(
            background = Color(0xFF0A0A0B),
            surfaceGlass = Color(0xFF151517),
            surfaceElevated = Color(0xFF1D1D20),
            surfaceSheet = Color(0xFF242428),
            textPrimary = Color(0xFFF2F2F4),
            textSecondary = Color(0xFFB4B4BA),
            textTertiary = Color(0xFF7C7C84),
            textDisabled = Color(0xFF4C4C52),
            accent = Color(0xFFE4E1D8),
            onAccent = Color(0xFF161512),
        ),
    ),
    MIDNIGHT_BLUE(
        displayName = "Midnight Blue",
        palette = GlassPalette(
            background = Color(0xFF06090D),
            surfaceGlass = Color(0xFF0F1620),
            surfaceElevated = Color(0xFF16202C),
            surfaceSheet = Color(0xFF1C2836),
            textPrimary = Color(0xFFEFF3F8),
            textSecondary = Color(0xFFA9B4C0),
            textTertiary = Color(0xFF6E7A88),
            textDisabled = Color(0xFF43505E),
            accent = Color(0xFF8FB6D9),
            onAccent = Color(0xFF07131E),
        ),
    ),
    CRIMSON_NOIR(
        displayName = "Crimson Noir",
        palette = GlassPalette(
            background = Color(0xFF0B0708),
            surfaceGlass = Color(0xFF181113),
            surfaceElevated = Color(0xFF221619),
            surfaceSheet = Color(0xFF2B1B1F),
            textPrimary = Color(0xFFF5EEEE),
            textSecondary = Color(0xFFBFACAE),
            textTertiary = Color(0xFF87747A),
            textDisabled = Color(0xFF544348),
            accent = Color(0xFFD98899),
            onAccent = Color(0xFF200D12),
        ),
    ),
    FOREST_SAGE(
        displayName = "Forest Sage",
        palette = GlassPalette(
            background = Color(0xFF080A08),
            surfaceGlass = Color(0xFF121712),
            surfaceElevated = Color(0xFF19201A),
            surfaceSheet = Color(0xFF212A22),
            textPrimary = Color(0xFFEFF3EE),
            textSecondary = Color(0xFFAEB9AB),
            textTertiary = Color(0xFF748076),
            textDisabled = Color(0xFF48504A),
            accent = Color(0xFFA3C29A),
            onAccent = Color(0xFF0E170F),
        ),
    ),
    AMBER_DUSK(
        displayName = "Amber Dusk",
        palette = GlassPalette(
            background = Color(0xFF0B0907),
            surfaceGlass = Color(0xFF171310),
            surfaceElevated = Color(0xFF201A15),
            surfaceSheet = Color(0xFF29211A),
            textPrimary = Color(0xFFF6F1EA),
            textSecondary = Color(0xFFC2B4A2),
            textTertiary = Color(0xFF8B7C6C),
            textDisabled = Color(0xFF554A3F),
            accent = Color(0xFFDCA860),
            onAccent = Color(0xFF1D1206),
        ),
    ),
    PURE_MONO(
        displayName = "Pure Monochrome",
        palette = GlassPalette(
            background = Color(0xFF000000),
            surfaceGlass = Color(0xFF121212),
            surfaceElevated = Color(0xFF1A1A1A),
            surfaceSheet = Color(0xFF222222),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFB8B8B8),
            textTertiary = Color(0xFF7E7E7E),
            textDisabled = Color(0xFF4A4A4A),
            accent = Color(0xFFFFFFFF),
            onAccent = Color(0xFF000000),
        ),
    ),
}
