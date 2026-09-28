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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
internal fun GlassOpacitySlider(value: Float, onPreview: (Float) -> Unit, onCommit: (Float) -> Unit) {
    var pending by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(value) { pending = null }
    val shown = pending ?: value
    val label = "${(shown * 100).roundToInt()}%"
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Clear", style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
            Text(label, style = MaterialTheme.typography.labelLarge, color = WhiplashColors.textPrimary)
            Text("Frosted", style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
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
                contentDescription = "Glass opacity"
                stateDescription = label
            },
        )
        if (!glassRefractionSupported) {
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
internal fun CustomThemeEditor(colors: CustomThemeColors, onChange: (CustomThemeColors) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        ColorRow("Background", colors.background, CUSTOM_BACKGROUNDS) { onChange(colors.copy(background = it)) }
        HueToneSliders("Background", colors.background) { onChange(colors.copy(background = it)) }
        Spacer(Modifier.height(GlassTokens.spaceXs))
        ColorRow("Accent", colors.accent, CUSTOM_ACCENTS) { onChange(colors.copy(accent = it)) }
        HueToneSliders("Accent", colors.accent) { onChange(colors.copy(accent = it)) }
        Text(
            "Text and icon colours are worked out for you and always kept readable.",
            style = MaterialTheme.typography.bodySmall,
            color = WhiplashColors.textTertiary,
        )
    }
}

@Composable
private fun ColorRow(label: String, selected: Color, options: List<Color>, onPick: (Color) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = WhiplashColors.textPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(options) { c ->
            val isSel = c.toHsv().let { a -> selected.toHsv().let { b -> a.zip(b).all { (x, y) -> kotlin.math.abs(x - y) < 0.01f } } }
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
}

/** Hue and brightness sliders for fine-tuning a colour. Saturation is kept from the current colour. */
@Composable
private fun HueToneSliders(label: String, color: Color, onChange: (Color) -> Unit) {
    val hsv = color.toHsv()
    val sat = if (hsv[1] < 0.05f && label == "Accent") 0.6f else hsv[1]
    var hue by remember(color) { mutableStateOf(hsv[0]) }
    var value by remember(color) { mutableStateOf(hsv[2]) }
    val sliderColors = SliderDefaults.colors(
        thumbColor = WhiplashColors.accent,
        activeTrackColor = WhiplashColors.accent,
        inactiveTrackColor = WhiplashColors.glassBorderStrong,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Hue", style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary, modifier = Modifier.width(76.dp))
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = { onChange(Color.hsv(hue, sat, value)) },
            valueRange = 0f..359f,
            colors = sliderColors,
            modifier = Modifier.weight(1f).semantics { contentDescription = "$label hue" },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Brightness", style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary, modifier = Modifier.width(76.dp))
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(Color.hsv(hue, sat, value)) },
            valueRange = 0f..1f,
            colors = sliderColors,
            modifier = Modifier.weight(1f).semantics { contentDescription = "$label brightness" },
        )
    }
}

private fun Color.toHsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt(), out)
    return out
}
