package com.whiplash.music.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-wide, in-app toast/snackbar feed (section: user feedback for silent
 * actions — favoriting, pinning, playlist changes, queue changes, etc.).
 *
 * A plain singleton object rather than something threaded through
 * ViewModel constructors: the whole point is that *any* ViewModel or
 * repository-adjacent call site (SongActionsViewModel, PlaylistsViewModel,
 * PlayerViewModel, PlaybackController, SettingsViewModel, ...) can report
 * a one-line result the moment an action completes, without every one of
 * them needing a shared dependency injected in just for this. The actual
 * *rendering* of these messages is a single composable
 * ([com.whiplash.music.ui.theme.GlassToastHost]) mounted once at the root
 * of the UI tree — this object is purely the event pipe between the two.
 *
 * [MutableSharedFlow] with a small replay-less buffer (not a StateFlow):
 * a StateFlow would drop back-to-back identical messages (no state change
 * to trigger a new emission) and would also automatically "replay" its
 * last value to a newly-subscribed collector, both wrong for a one-shot
 * toast. `extraBufferCapacity` covers the case of several actions firing
 * in the same frame (e.g. a batch operation) before the host has had a
 * chance to collect — those messages queue rather than silently drop.
 */
object ToastController {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    /** Posts a short, user-facing message to be shown as a toast for a few seconds. */
    fun show(message: String) {
        _events.tryEmit(message)
    }
}
