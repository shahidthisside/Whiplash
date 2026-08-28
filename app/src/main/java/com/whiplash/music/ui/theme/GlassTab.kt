package com.whiplash.music.ui.theme

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Row of [GlassChip]s acting as a simple tab switcher.
 *
 * Horizontally scrollable rather than a fixed equal-width Row: with 4+
 * items (e.g. Songs/Albums/Artists/Playlists, each with a real result
 * count appended), a non-scrolling Row squeezes every chip to fit the
 * available width, which can shrink a chip's Text below its natural
 * width and force it to wrap character-by-character vertically — a real
 * bug this fix addresses, not just a preventative measure.
 */
@Composable
fun <T> GlassTabRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
    ) {
        items.forEach { item ->
            GlassChip(
                text = label(item),
                selected = item == selected,
                onClick = { onSelect(item) },
            )
        }
    }
}
