package com.whiplash.music.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.inkOn
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** The hue order a sweep gradient needs, starting at 3 o'clock and going clockwise. */
private val HUE_STOPS = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF),
    Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/**
 * The swatch at the end of a colour row that opens [ColorWheelSheet]: a
 * rainbow ring, showing a check when the current colour isn't one of the
 * presets (i.e. it came from the wheel or sliders).
 */
@Composable
internal fun ColorWheelSwatch(label: String, isCustom: Boolean, current: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient(HUE_STOPS))
            .clickable(role = Role.Button, onClickLabel = "Pick any $label colour") { onClick() }
            .semantics { contentDescription = "$label colour wheel" }
            .padding(if (isCustom) 5.dp else 9.dp)
            .clip(CircleShape)
            .background(if (isCustom) current else WhiplashColors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isCustom) Icons.Filled.Check else Icons.Filled.Colorize,
            contentDescription = null,
            tint = if (isCustom) inkOn(current) else WhiplashColors.textPrimary,
            modifier = Modifier.size(if (isCustom) 16.dp else 13.dp),
        )
    }
}

/**
 * Full colour picker: a hue/saturation wheel, a brightness slider and a hex
 * field. The app recolours live as you drag ([onPreview]); Apply saves
 * ([onApply]), Cancel or dismissing puts the original colour back.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ColorWheelSheet(
    title: String,
    initial: Color,
    onPreview: (Color) -> Unit,
    onApply: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember { initial.hsv() }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2].coerceAtLeast(0.05f)) }
    var hex by remember { mutableStateOf(initial.hex()) }
    fun color() = Color.hsv(hue.coerceIn(0f, 359.9f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    fun changed(fromHex: Boolean = false) {
        if (!fromHex) hex = color().hex()
        onPreview(color())
    }
    val cancel = {
        onPreview(initial)
        onDismiss()
    }

    GlassSheet(onDismissRequest = cancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = GlassTokens.spaceLg)
                .navigationBarsPadding()
                .padding(bottom = GlassTokens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = WhiplashColors.textPrimary, modifier = Modifier.fillMaxWidth())

            // Wheel: angle = hue, distance from centre = saturation.
            Box(
                Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics {
                        contentDescription = "$title wheel"
                        stateDescription = "Hue ${hue.roundToInt()}, saturation ${(sat * 100).roundToInt()}%"
                    }
                    .pointerInput(Unit) {
                        fun pick(p: Offset) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = size.width / 2f
                            val d = p - c
                            var deg = Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat()
                            if (deg < 0f) deg += 360f
                            hue = deg
                            sat = (hypot(d.x, d.y) / r).coerceIn(0f, 1f)
                            changed()
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            pick(down.position)
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                pick(ch.position)
                                ch.consume()
                            }
                        }
                    },
            ) {
                Canvas(Modifier.matchParentSizeCompat()) {
                    val r = size.minDimension / 2f
                    // Always drawn at full brightness so hues stay easy to pick
                    // even for very dark backgrounds; the thumb and the preview
                    // chip show the real colour with brightness applied.
                    drawCircle(Brush.sweepGradient(HUE_STOPS, center), r)
                    drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
                    drawCircle(Color.White.copy(alpha = 0.25f), r, style = Stroke(1.dp.toPx()))
                    val a = Math.toRadians(hue.toDouble())
                    val t = Offset(center.x + cos(a).toFloat() * sat * r, center.y + sin(a).toFloat() * sat * r)
                    drawCircle(color(), 13.dp.toPx(), t)
                    drawCircle(Color.White, 13.dp.toPx(), t, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.35f), 14.5.dp.toPx(), t, style = Stroke(1.dp.toPx()))
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Brightness", style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary, modifier = Modifier.width(80.dp))
                Slider(
                    value = value,
                    onValueChange = { value = it; changed() },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = WhiplashColors.accent,
                        activeTrackColor = WhiplashColors.accent,
                        inactiveTrackColor = WhiplashColors.glassBorderStrong,
                    ),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "$title brightness" },
                )
            }

            // Preview chip and hex entry.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color())
                        .border(1.dp, WhiplashColors.glassBorderStrong, RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Text("Hex", style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
                Spacer(Modifier.width(8.dp))
                val valid = parseHex(hex) != null
                BasicTextField(
                    value = hex,
                    onValueChange = { raw ->
                        hex = raw.uppercase().filter { it.isLetterOrDigit() || it == '#' }.take(7)
                        parseHex(hex)?.let { c ->
                            val h = c.hsv()
                            hue = h[0]; sat = h[1]; value = h[2]
                            changed(fromHex = true)
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (valid) WhiplashColors.textPrimary else WhiplashColors.error,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(WhiplashColors.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!valid) hex = color().hex() }),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(WhiplashColors.tone(0.08f))
                        .border(1.dp, if (valid) WhiplashColors.glassBorderStrong else WhiplashColors.error, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .semantics { contentDescription = "$title hex code" },
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetButton("Cancel", filled = false, modifier = Modifier.weight(1f), onClick = cancel)
                SheetButton("Apply", filled = true, modifier = Modifier.weight(1f)) {
                    onApply(color())
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (filled) WhiplashColors.accent else WhiplashColors.tone(0.10f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (filled) WhiplashColors.onAccent else WhiplashColors.textPrimary,
        )
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().aspectRatio(1f))

private fun Color.hsv(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt(), out)
    return out
}

private fun Color.hex(): String =
    "#%02X%02X%02X".format((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())

/** "#RRGGBB" or "RRGGBB" (also 3-digit "#RGB"), else null. */
internal fun parseHex(s: String): Color? {
    val h = s.removePrefix("#")
    val full = when (h.length) {
        6 -> h
        3 -> h.map { "$it$it" }.joinToString("")
        else -> return null
    }
    return full.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}
