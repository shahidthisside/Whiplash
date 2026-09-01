package com.whiplash.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/**
 * Playback speed sheet for the full player — a 1-tap shortcut to the same
 * setting as Settings > Playback > Playback Speed (both read/write the
 * same [com.whiplash.music.data.repository.SettingsRepository.playbackSpeed],
 * so either surface always reflects the other immediately), so changing
 * speed no longer requires leaving the now-playing screen. Same segmented-
 * pill visual treatment as Settings' own selector for consistency.
 */
@Composable
fun PlaybackSpeedContent(selected: Float, onSelect: (Float) -> Unit) {
    Column {
        Text(
            text = "Playback speed",
            style = MaterialTheme.typography.titleMedium,
            color = WhiplashColors.textPrimary,
            modifier = Modifier.padding(bottom = GlassTokens.spaceMd),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WhiplashRadius.pill))
                .background(WhiplashColors.surfaceGlass)
                .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, RoundedCornerShape(WhiplashRadius.pill))
                .padding(3.dp),
        ) {
            SPEED_OPTIONS.forEach { speed ->
                val isSelected = speed == selected
                val bg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) WhiplashColors.accent else Color.Transparent,
                    label = "speedSegmentBg",
                )
                val fg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) WhiplashColors.onAccent else WhiplashColors.textSecondary,
                    label = "speedSegmentFg",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(WhiplashRadius.pill))
                        .background(bg)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelect(speed) }
                        .padding(vertical = GlassTokens.spaceSm),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(text = "${speed}x", style = MaterialTheme.typography.labelMedium, color = fg)
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
    }
}

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

