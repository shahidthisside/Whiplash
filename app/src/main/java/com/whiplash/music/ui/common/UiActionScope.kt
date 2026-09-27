package com.whiplash.music.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * For work a menu or dialog starts and that must finish after it closes
 * (loading an album's songs, removing downloads). rememberCoroutineScope()
 * is cancelled the moment its sheet leaves the screen, which silently
 * dropped these actions.
 */
internal object UiActionScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
