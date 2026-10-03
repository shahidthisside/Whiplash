// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.whiplash.music.ui.common.PlaybackSpeedControl
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Playback speed sheet for the full player — a 1-tap shortcut to the same
 * setting as Settings > Playback > Playback Speed (both read/write the
 * same [com.whiplash.music.data.repository.SettingsRepository.playbackSpeed],
 * so either surface always reflects the other immediately). Uses the same
 * [PlaybackSpeedControl] as Settings so the two can never offer different
 * ranges. The sheet stays open after a change so the slider can be nudged
 * repeatedly while listening; dismissing it is a normal swipe/tap outside.
 */
@Composable
fun PlaybackSpeedContent(selected: Float, onSelect: (Float) -> Unit, onPreview: (Float) -> Unit = {}) {
    Column {
        Text(
            text = "Playback speed",
            style = MaterialTheme.typography.titleMedium,
            color = WhiplashColors.textPrimary,
            modifier = Modifier.padding(bottom = GlassTokens.spaceMd),
        )
        PlaybackSpeedControl(selected = selected, onSelect = onSelect, onPreview = onPreview)
        Spacer(Modifier.padding(top = GlassTokens.spaceSm))
    }
}
