package com.whiplash.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography scale for Whiplash.
 *
 * Uses the platform default font family deliberately: an elegant, restrained
 * type system per section 41 relies on weight/spacing/hierarchy rather than
 * a decorative custom typeface. A custom font can be swapped in later by
 * changing [WhiplashFontFamily] without touching call sites.
 */
private val WhiplashFontFamily = FontFamily.Default

val WhiplashTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    // Real, reported bug (UAT audit finding): headlineSmall/titleSmall
    // were referenced by several screens (LyricsContent.kt, SettingsScreen.kt,
    // AlbumDetailScreen.kt, ArtistDetailScreen.kt — e.g. an album/artist
    // detail page's own title, Settings' every SettingRow label) but were
    // never actually defined in this Typography scale. Material3 silently
    // substitutes its own built-in default for any unset slot — a
    // materially different size/weight/letter-spacing than this app's
    // custom scale — so every one of those call sites was quietly
    // falling off the design system's own type scale with no visible
    // error. Added here at the natural size step between the existing
    // headlineMedium/titleLarge and titleLarge/bodyLarge sizes,
    // following the same fontWeight convention each tier already uses
    // (headline* = SemiBold, title* = Medium).
    headlineSmall = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = WhiplashFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)
