package com.whiplash.music.playback.service

import androidx.media3.session.MediaSession

/**
 * Session callback controlling controller connections.
 *
 * Kept minimal for now: accepts connections from the app's own UI. Queue
 * persistence, custom commands (e.g. sleep timer, repeat-one toggles beyond
 * what Player already exposes) are added in later phases as those features
 * are built, per the "no unused features" principle.
 */
class WhiplashSessionCallback : MediaSession.Callback
