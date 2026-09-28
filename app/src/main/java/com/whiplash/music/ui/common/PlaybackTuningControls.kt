package com.whiplash.music.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.whiplash.music.domain.model.PlaybackTuning
import com.whiplash.music.ui.theme.GlassChip
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import kotlin.math.roundToInt

/**
 * Crossfade length picker: a stepped slider from Off to 12 seconds in
 * one-second steps.
 *
 * The value shown while dragging is held locally and only committed on
 * release. Committing on every drag frame would write DataStore dozens of
 * times per second, and because the stored value comes back
 * asynchronously the thumb would fight the finger. The local value is
 * kept after release until the stored value catches up, so the thumb
 * never snaps back to the old setting for a frame.
 */
@Composable
fun CrossfadeSlider(selectedMs: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var pendingMs by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedMs) { pendingMs = null }
    val shownMs = pendingMs ?: selectedMs
    val label = crossfadeLabel(shownMs)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Off", style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
            Text(label, style = MaterialTheme.typography.labelLarge, color = WhiplashColors.textPrimary)
            Text("${PlaybackTuning.CROSSFADE_MAX_MS / 1000}s", style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
        }
        Slider(
            value = shownMs / 1000f,
            onValueChange = { pendingMs = PlaybackTuning.normalizeCrossfadeMs((it * 1000f).roundToInt()) },
            onValueChangeFinished = { pendingMs?.let(onSelect) },
            valueRange = 0f..(PlaybackTuning.CROSSFADE_MAX_MS / 1000f),
            steps = PlaybackTuning.CROSSFADE_MAX_MS / PlaybackTuning.CROSSFADE_STEP_MS - 1,
            colors = tuningSliderColors(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Crossfade duration"
                    stateDescription = label
                },
        )
    }
}

private fun crossfadeLabel(ms: Int): String = if (ms <= 0) "Off" else "${ms / 1000}s"

/**
 * Playback speed picker: a 0.5x-2.0x slider in 0.05x steps, plus one-tap
 * presets for the common speeds. Same commit-on-release behaviour as
 * [CrossfadeSlider]; presets commit immediately.
 */
@Composable
fun PlaybackSpeedControl(
    selected: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Called on every drag step so the song speeds up/slows down live; [onSelect] saves on release. */
    onPreview: (Float) -> Unit = {},
) {
    var pending by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(selected) { pending = null }
    val shown = pending ?: PlaybackTuning.normalizeSpeed(selected)
    val label = PlaybackTuning.formatSpeed(shown)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(PlaybackTuning.formatSpeed(PlaybackTuning.SPEED_MIN), style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
            Text(label, style = MaterialTheme.typography.labelLarge, color = WhiplashColors.textPrimary)
            Text(PlaybackTuning.formatSpeed(PlaybackTuning.SPEED_MAX), style = MaterialTheme.typography.labelMedium, color = WhiplashColors.textSecondary)
        }
        Slider(
            value = shown,
            onValueChange = { v ->
                val s = PlaybackTuning.normalizeSpeed(v)
                if (s != pending) {
                    pending = s
                    onPreview(s)
                }
            },
            onValueChangeFinished = { pending?.let(onSelect) },
            valueRange = PlaybackTuning.SPEED_MIN..PlaybackTuning.SPEED_MAX,
            steps = ((PlaybackTuning.SPEED_MAX - PlaybackTuning.SPEED_MIN) / PlaybackTuning.SPEED_STEP).roundToInt() - 1,
            colors = tuningSliderColors(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Playback speed"
                    stateDescription = label
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
        ) {
            PlaybackTuning.SPEED_PRESETS.forEach { preset ->
                GlassChip(
                    text = PlaybackTuning.formatSpeed(preset),
                    selected = preset == shown,
                    onClick = {
                        pending = preset
                        onSelect(preset)
                    },
                )
            }
        }
    }
}

/** Accent thumb/track; tick marks hidden because 30 speed steps would read as a dotted line. */
@Composable
private fun tuningSliderColors() = SliderDefaults.colors(
    thumbColor = WhiplashColors.accent,
    activeTrackColor = WhiplashColors.accent,
    inactiveTrackColor = WhiplashColors.textSecondary.copy(alpha = 0.25f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)
