package com.whiplash.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.whiplash.music.playback.controller.SleepTimerMode
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/**
 * Sleep timer sheet (section 60): a fixed duration, or one of two
 * "stop at the next natural boundary" modes. Selecting an option applies
 * it immediately and closes the sheet — matching the same one-tap pattern
 * used by [SongActionsContent].
 */
@Composable
fun SleepTimerContent(
    current: SleepTimerMode?,
    remainingMs: Long?,
    onSelect: (SleepTimerMode?) -> Unit,
) {
    Column(modifier = Modifier.padding(GlassTokens.spaceMd)) {
        Text(
            text = "Sleep Timer",
            style = MaterialTheme.typography.titleMedium,
            color = WhiplashColors.textPrimary,
        )
        if (current != null) {
            Text(
                text = when (current) {
                    is SleepTimerMode.Duration -> "Stopping in ${formatRemaining(remainingMs ?: current.totalMs)}"
                    SleepTimerMode.EndOfSong -> "Stopping at the end of this song"
                    SleepTimerMode.EndOfQueue -> "Stopping at the end of the queue"
                },
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

        val options: List<Pair<String, SleepTimerMode?>> = listOf(
            "Off" to null,
            "5 minutes" to SleepTimerMode.Duration(5 * 60_000L),
            "10 minutes" to SleepTimerMode.Duration(10 * 60_000L),
            "15 minutes" to SleepTimerMode.Duration(15 * 60_000L),
            "30 minutes" to SleepTimerMode.Duration(30 * 60_000L),
            "45 minutes" to SleepTimerMode.Duration(45 * 60_000L),
            "60 minutes" to SleepTimerMode.Duration(60 * 60_000L),
            "End of song" to SleepTimerMode.EndOfSong,
            "End of queue" to SleepTimerMode.EndOfQueue,
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { (label, mode) ->
                val isSelected = modesMatch(current, mode)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(WhiplashRadius.small))
                        .background(if (isSelected) WhiplashColors.surfaceElevated else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = Role.Button) { onSelect(mode) }
                        .padding(horizontal = GlassTokens.spaceSm, vertical = GlassTokens.spaceMd),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = label, style = MaterialTheme.typography.bodyLarge, color = WhiplashColors.textPrimary)
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = WhiplashColors.accent)
                    }
                }
            }
        }
    }
}

private fun modesMatch(a: SleepTimerMode?, b: SleepTimerMode?): Boolean = when {
    a == null && b == null -> true
    a is SleepTimerMode.Duration && b is SleepTimerMode.Duration -> a.totalMs == b.totalMs
    a is SleepTimerMode.EndOfSong && b is SleepTimerMode.EndOfSong -> true
    a is SleepTimerMode.EndOfQueue && b is SleepTimerMode.EndOfQueue -> true
    else -> false
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
