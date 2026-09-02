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
    // skipPartiallyExpanded = true: real, reported bug — the sheet used
    // to always open in a half-height "partially expanded" state first
    // (Material3's own default), so a sheet with enough rows to exceed
    // that half-height (e.g. the 10-row song actions sheet for an
    // undownloaded YoutubeTrack from Quick Picks) required a manual drag
    // or scroll gesture just to see the rest — "have to scroll... full
    // sheet should be visible without scrolling." Skipping straight to
    // the fully-expanded state means every row is visible immediately on
    // open with no gesture required; a sheet whose content is shorter
    // than the full screen still only takes up as much height as it
    // needs; this doesn't force every sheet to visually cover the whole
    // screen.
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
        // NOTE: deliberately NOT Modifier.verticalScroll() here. A real,
        // reported crash: QueueContent (one of this composable's callers)
        // renders its own internal LazyColumn — nesting a LazyColumn
        // inside a verticalScroll(Column) is explicitly disallowed by
        // Compose ("Vertically scrollable component was measured with an
        // infinity maximum height constraints") and crashes immediately
        // on measure. skipPartiallyExpanded=true above already solves the
        // original "have to scroll to see all options" report for plain-
        // Column sheets (the song actions sheet) by making the sheet open
        // at full height immediately; LazyColumn-based sheet content
        // (Queue) already scrolls correctly on its own and never needed
        // this Column to do it too.
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
