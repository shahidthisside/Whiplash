@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.whiplash.music.playback.service

import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * How the player retries a failed read of a song's audio.
 *
 * A dropped or slow connection (a tunnel, switching between Wi-Fi and
 * mobile data) is retried more times than Media3's default of 3, with the
 * default growing wait (up to 5 s), so a short outage is covered while the
 * buffer plays instead of stopping the song.
 *
 * A refused link (HTTP 4xx: googlevideo answers 403 once a link expires)
 * never starts working again, so it fails at once and the controller looks
 * up a fresh link straight away, instead of after three pointless retries.
 */
class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(MIN_RETRIES) {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (refusedStatus(loadErrorInfo.exception) != null) return C.TIME_UNSET
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    companion object {
        const val MIN_RETRIES = 6

        /** A 4xx status, except 408 (timeout) and 429 (busy), which can pass and are retried. */
        fun isRefused(status: Int): Boolean = status in 400..499 && status != 408 && status != 429

        /** The refused HTTP status behind [error], if any (see [isRefused]). */
        fun refusedStatus(error: Throwable?): Int? {
            var e = error
            while (e != null) {
                if (e is HttpDataSource.InvalidResponseCodeException) return e.responseCode.takeIf(::isRefused)
                e = e.cause
            }
            return null
        }
    }
}
