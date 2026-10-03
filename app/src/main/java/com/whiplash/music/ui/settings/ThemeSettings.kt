// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whiplash.music.ui.theme.AppTheme
import com.whiplash.music.ui.theme.CustomThemeColors
import com.whiplash.music.ui.theme.GlassPalette
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.glassBlurSupported
import com.whiplash.music.ui.theme.glassRefractionSupported
import com.whiplash.music.ui.theme.resolvePalette
import kotlin.math.roundToInt

/**
 * Theme picker: one card per theme, each a small live preview drawn in that
 * theme's own resolved palette (so what you see is what you get, including
 * the contrast fixes), with its name under it.
 */
@Composable
internal fun AppThemeGrid(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    val accent = WhiplashColors.accentVariant
    val custom = WhiplashColors.customColors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(AppTheme.entries, key = { it.name }) { theme ->
            val palette = remember(theme, accent, custom) { resolvePalette(theme, accent, custom) }
            ThemePreviewCard(
                theme = theme,
                palette = palette,
                isSelected = theme == selected,
                onClick = { onSelect(theme) },
            )
        }
    }
}

@Composable
private fun ThemePreviewCard(theme: AppTheme, palette: GlassPalette, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(shape)
            .clickable(role = Role.RadioButton, onClickLabel = "Use ${theme.displayName}", onClick = onClick)
            .semantics(mergeDescendants = true) {
                selected = isSelected
                contentDescription = "${theme.displayName} theme. ${theme.description}"
            }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(shape)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) WhiplashColors.accent else WhiplashColors.glassBorderStrong,
                    shape = shape,
                )
                .padding(if (isSelected) 2.5.dp else 1.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background),
        ) {
            if (theme == AppTheme.LIQUID_GLASS) {
                // Hint of the glass wallpaper's light pools.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(
                                lerp(palette.accent, Color(0xFF3D7BFF), 0.6f).copy(alpha = 0.55f),
                                Color.Transparent,
                                lerp(palette.accent, Color(0xFFFF6FA8), 0.55f).copy(alpha = 0.45f),
                            ),
                        ),
                    ),
                )
            }
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // A cover tile, two text lines and a play button in the palette.
                Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(8.dp)).background(palette.surfaceElevated))
                Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(CircleShape).background(palette.textPrimary))
                Box(Modifier.fillMaxWidth(0.55f).height(5.dp).clip(CircleShape).background(palette.textSecondary))
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(palette.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) Icon(Icons.Filled.Check, null, tint = palette.onAccent, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.weight(1f).height(14.dp).clip(CircleShape)
                            .background(if (theme == AppTheme.LIQUID_GLASS) Color.White.copy(alpha = 0.22f) else palette.surfaceGlass)
                            .border(0.5.dp, palette.glassBorderStrong, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.displayName,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isSelected) WhiplashColors.textPrimary else WhiplashColors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** How see-through Liquid Glass is: Clear ... Frosted. Live while dragging, saved on release. */
@Composable
internal fun GlassOpacitySlider(
    value: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    startLabel: String = "Clear",
    endLabel: String = "Frosted",
    description: String = "Glass opacity",
    showSupportNote: Boolean = true,
    /** Where the default sits: marked under the track, with a Reset button when moved off it. */
    defaultValue: Float = WhiplashColors.DEFAULT_GLASS_OPACITY,
) {
    var pending by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(value) { pending = null }
    val shown = pending ?: value
    val label = "${(shown * 100).roundToInt()}%"
    val atDefault = kotlin.math.abs(shown - defaultValue) < 0.005f
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // Value on the left, Reset on the right (only when moved off default).
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(CircleShape).background(WhiplashColors.tone(0.10f)).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    if (atDefault) "$label · Default" else label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = WhiplashColors.textPrimary,
                )
            }
            Spacer(Modifier.weight(1f))
            androidx.compose.animation.AnimatedVisibility(
                visible = !atDefault,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(GlassTokens.animSlow)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(GlassTokens.animSlow)),
            ) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(WhiplashColors.accent)
                        .clickable(role = Role.Button, onClickLabel = "Reset $description to default") {
                            pending = null
                            onPreview(defaultValue)
                            onCommit(defaultValue)
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.RestartAlt, null, tint = WhiplashColors.onAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Reset",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = WhiplashColors.onAccent,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Default marker above the track: a caption and a notch pointing at it.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(24.dp)) {
            val inset = 10.dp
            val x = inset + (maxWidth - inset * 2) * defaultValue
            val w = 56.dp
            val left = (x - w / 2).coerceIn(0.dp, maxWidth - w)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(w).offset(x = left),
            ) {
                Text(
                    "Default",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (atDefault) WhiplashColors.accent else WhiplashColors.textTertiary,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = x - 1.dp)
                    .size(width = 2.dp, height = 7.dp)
                    .clip(CircleShape)
                    .background(if (atDefault) WhiplashColors.accent else WhiplashColors.textTertiary),
            )
        }
        Slider(
            value = shown,
            onValueChange = { pending = it; onPreview(it) },
            onValueChangeFinished = { pending?.let(onCommit) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = WhiplashColors.accent,
                activeTrackColor = WhiplashColors.accent,
                inactiveTrackColor = WhiplashColors.glassBorderStrong,
            ),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = description
                stateDescription = if (atDefault) "$label, default" else label
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
            Text(endLabel, style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
        }
        if (showSupportNote && !glassRefractionSupported) {
            Text(
                text = if (glassBlurSupported) {
                    "This Android version shows blurred glass without the lens bending (needs Android 13)."
                } else {
                    "This Android version can't blur behind glass, so glass is shown tinted."
                },
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textTertiary,
            )
        }
    }
}

/** Colours offered for a Custom theme's background and accent. */
private val CUSTOM_BACKGROUNDS = listOf(
    Color(0xFF101418), Color(0xFF000000), Color(0xFF1A1423), Color(0xFF0F1A14), Color(0xFF221612),
    Color(0xFF0D1B2A), Color(0xFF2B2D42), Color(0xFFF5F5F0), Color(0xFFFFF4E6), Color(0xFFEAF2FB),
    Color(0xFFF3EAF7), Color(0xFFE9F5EC),
)
private val CUSTOM_ACCENTS = listOf(
    Color(0xFF7FB8FF), Color(0xFFFF6B8B), Color(0xFFFFB454), Color(0xFF7BE0A1), Color(0xFFC792EA),
    Color(0xFF4DD0E1), Color(0xFFFF8A65), Color(0xFFE6E1D5), Color(0xFF1E88E5), Color(0xFFD81B60),
    Color(0xFF2E7D32), Color(0xFF6D4C41),
)

/**
 * Custom theme editor: pick a background and an accent (preset swatches,
 * plus hue / brightness sliders for anything in between). Every other
 * colour is derived and contrast-checked, so any pick stays readable.
 */
@Composable
internal fun CustomThemeEditor(
    colors: CustomThemeColors,
    onChange: (CustomThemeColors) -> Unit,
    onPreview: (CustomThemeColors) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        ColorRow("Background", colors.background, CUSTOM_BACKGROUNDS, onPreview = { onPreview(colors.copy(background = it)) }) { onChange(colors.copy(background = it)) }
        HueToneSliders(
            "Background", colors.background,
            onPreview = { onPreview(colors.copy(background = it)) },
        ) { onChange(colors.copy(background = it)) }
        Spacer(Modifier.height(GlassTokens.spaceXs))
        ColorRow("Accent", colors.accent, CUSTOM_ACCENTS, onPreview = { onPreview(colors.copy(accent = it)) }) { onChange(colors.copy(accent = it)) }
        HueToneSliders(
            "Accent", colors.accent,
            onPreview = { onPreview(colors.copy(accent = it)) },
        ) { onChange(colors.copy(accent = it)) }
        Text(
            "Text and icon colours are worked out for you and always kept readable.",
            style = MaterialTheme.typography.bodySmall,
            color = WhiplashColors.textTertiary,
        )
    }
}

@Composable
private fun ColorRow(
    label: String,
    selected: Color,
    options: List<Color>,
    /** Live recolour while the colour wheel is open; the wheel swatch shows when set. */
    onPreview: ((Color) -> Unit)? = null,
    onPick: (Color) -> Unit,
) {
    fun same(a: Color, b: Color) =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue) < 0.012f
    var wheelOpen by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelLarge, color = WhiplashColors.textPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onPreview != null) {
            // First, so it's always visible without scrolling the row.
            item(key = "wheel") {
                ColorWheelSwatch(label, isCustom = options.none { same(it, selected) }, current = selected) { wheelOpen = true }
            }
        }
        items(options) { c ->
            val isSel = same(c, selected)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(if (isSel) 2.5.dp else 1.dp, if (isSel) WhiplashColors.accent else WhiplashColors.glassBorderStrong, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(c)
                    .clickable(role = Role.RadioButton, onClickLabel = "Use this $label colour") { onPick(c) }
                    .semantics { this.selected = isSel },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Filled.Check, null, tint = com.whiplash.music.ui.theme.inkOn(c), modifier = Modifier.size(18.dp))
            }
        }
    }
    if (wheelOpen && onPreview != null) {
        ColorWheelSheet(
            title = "$label colour",
            initial = selected,
            onPreview = onPreview,
            onApply = onPick,
            onDismiss = { wheelOpen = false },
        )
    }
}

/**
 * Hue, saturation and brightness sliders for fine-tuning a colour. The app
 * recolours live on every drag step ([onPreview]); [onChange] saves once on
 * release, so storage isn't written dozens of times a second.
 */
@Composable
private fun HueToneSliders(
    label: String,
    color: Color,
    onPreview: (Color) -> Unit = {},
    onChange: (Color) -> Unit,
) {
    // Local H/S/V while dragging. Re-seeded only when the saved colour becomes
    // something other than what the sliders show (a swatch tap), so saving
    // our own value never snaps a thumb (hue is undefined for greys).
    val seed = remember { color.toHsv() }
    val accentLike = label.contains("accent", ignoreCase = true)
    var hue by remember { mutableStateOf(seed[0]) }
    var sat by remember { mutableStateOf(if (seed[1] < 0.05f && accentLike) 0.6f else seed[1]) }
    var value by remember { mutableStateOf(seed[2]) }
    fun current() = Color.hsv(hue.coerceIn(0f, 359.9f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    LaunchedEffect(color) {
        val c = current()
        val same = kotlin.math.abs(c.red - color.red) + kotlin.math.abs(c.green - color.green) + kotlin.math.abs(c.blue - color.blue) < 0.012f
        if (!same) {
            val h = color.toHsv()
            hue = h[0]
            sat = if (h[1] < 0.05f && accentLike) 0.6f else h[1]
            value = h[2]
        }
    }
    val sliderColors = SliderDefaults.colors(
        thumbColor = WhiplashColors.accent,
        activeTrackColor = WhiplashColors.accent,
        inactiveTrackColor = WhiplashColors.glassBorderStrong,
    )
    @Composable
    fun row(name: String, v: Float, range: ClosedFloatingPointRange<Float>, set: (Float) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary, modifier = Modifier.width(76.dp))
            Slider(
                value = v,
                onValueChange = { set(it); onPreview(current()) },
                onValueChangeFinished = { onChange(current()) },
                valueRange = range,
                colors = sliderColors,
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label ${name.lowercase()}" },
            )
        }
    }
    row("Hue", hue, 0f..359f) { hue = it }
    row("Saturation", sat, 0f..1f) { sat = it }
    row("Brightness", value, 0f..1f) { value = it }
}

/** Colours offered for the Accent colour setting's Custom choice. */
private val ACCENT_PRESETS = listOf(
    Color(0xFF5AC8FA), Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color(0xFFFF375F),
    Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158), Color(0xFF64D2FF),
    Color(0xFF66D4CF), Color(0xFFAC8E68),
)

/**
 * Editor for the Accent colour setting's Custom choice: preset swatches plus
 * live hue / saturation / brightness sliders. The contrast pass keeps text on
 * buttons and the accent itself readable on the current theme.
 */
@Composable
internal fun CustomAccentEditor(
    colors: CustomThemeColors,
    onChange: (CustomThemeColors) -> Unit,
    onPreview: (CustomThemeColors) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        ColorRow("Custom accent", colors.accentColor, ACCENT_PRESETS, onPreview = { onPreview(colors.copy(accentColor = it)) }) { onChange(colors.copy(accentColor = it)) }
        HueToneSliders(
            "Custom accent", colors.accentColor,
            onPreview = { onPreview(colors.copy(accentColor = it)) },
        ) { onChange(colors.copy(accentColor = it)) }
        Text(
            "Adjusted only as much as needed to stay readable on this theme.",
            style = MaterialTheme.typography.bodySmall,
            color = WhiplashColors.textTertiary,
        )
    }
}

private fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt(), out)
    return out
}

/**
 * Liquid Glass background: the current cover (blurred, like Apple Music),
 * a few calm solid tones, or a custom colour with hue/brightness sliders.
 */
@Composable
internal fun GlassBackgroundPicker(
    colors: CustomThemeColors,
    onChange: (CustomThemeColors) -> Unit,
    onPreview: (CustomThemeColors) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(com.whiplash.music.ui.theme.GlassBackground.entries, key = { it.name }) { option ->
                val isSel = option == colors.glassBackground
                val fill = when (option) {
                    com.whiplash.music.ui.theme.GlassBackground.CUSTOM -> colors.glassColor
                    com.whiplash.music.ui.theme.GlassBackground.NOW_PLAYING -> Color(0xFF2A2230)
                    else -> option.color!!
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton, onClickLabel = "Use ${option.displayName} glass background") {
                            onChange(colors.copy(glassBackground = option))
                        }
                        .semantics(mergeDescendants = true) { selected = isSel }
                        .padding(vertical = 4.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(if (isSel) 2.5.dp else 1.dp, if (isSel) WhiplashColors.accent else WhiplashColors.glassBorderStrong, CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        val ink = com.whiplash.music.ui.theme.inkOn(fill)
                        when {
                            isSel -> Icon(Icons.Filled.Check, null, tint = ink, modifier = Modifier.size(20.dp))
                            option == com.whiplash.music.ui.theme.GlassBackground.NOW_PLAYING ->
                                Icon(Icons.Filled.MusicNote, null, tint = ink, modifier = Modifier.size(20.dp))
                            option == com.whiplash.music.ui.theme.GlassBackground.CUSTOM ->
                                Icon(Icons.Filled.Palette, null, tint = ink, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        option.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) WhiplashColors.textPrimary else WhiplashColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (colors.glassBackground == com.whiplash.music.ui.theme.GlassBackground.CUSTOM) {
            ColorRow(
                "Glass background", colors.glassColor, emptyList(),
                onPreview = { onPreview(colors.copy(glassColor = it)) },
            ) { onChange(colors.copy(glassColor = it)) }
            HueToneSliders(
                "Glass background", colors.glassColor,
                onPreview = { onPreview(colors.copy(glassColor = it)) },
            ) { onChange(colors.copy(glassColor = it)) }
        }
        if (colors.glassBackground == com.whiplash.music.ui.theme.GlassBackground.NOW_PLAYING) {
            Text(
                "Uses the cover of the song that's playing, softly blurred behind the glass.",
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textTertiary,
            )
        }
    }
}
