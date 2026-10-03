// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.data.download.DownloadProgress
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Original work of Shahid Ansari (-SA). Not licensed for reuse.
/**
 * This one song's download progress (null when it isn't downloading).
 * Read per row, so a download's progress ticks redraw only the row showing
 * it; collecting the whole map at screen level redrew every row on every tick.
 */
@Composable
fun rememberDownloadProgress(trackId: String?): State<DownloadProgress?> {
    val app = LocalContext.current.applicationContext as WhiplashApplication
    val flow = remember(trackId) {
        val progress = app.downloadManager.progress
        if (trackId == null) {
            kotlinx.coroutines.flow.flowOf(null)
        } else {
            progress.map { it[trackId] }.distinctUntilChanged()
        }
    }
    return flow.collectAsState(initial = trackId?.let { app.downloadManager.progress.value[it] })
}
