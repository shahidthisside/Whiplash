package com.whiplash.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass modal bottom sheet (section 43: `GlassSheet`, section 51:
 * "queue, song actions, playlist actions, lyrics, playback options,
 * filters"). A thin wrapper around Material3's [ModalBottomSheet] — that
 * already provides the gesture/focus/accessibility support section 51
 * requires, styled to match the rest of the design system (fully opaque
 * sheet surface, drag handle, rounded top corners only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = WhiplashColors.surfaceSheet,
        contentColor = WhiplashColors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier.padding(vertical = GlassTokens.spaceSm),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(WhiplashShapes.extraLarge)
                        .background(WhiplashColors.glassBorderStrong),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = GlassTokens.spaceMd),
        ) {
            content()
        }
    }
}
