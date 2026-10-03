// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.playback.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class StreamLoadErrorPolicyTest {

    @Test
    fun expiredOrMissingLinkIsRefused() {
        assertTrue(StreamLoadErrorPolicy.isRefused(403))
        assertTrue(StreamLoadErrorPolicy.isRefused(404))
        assertTrue(StreamLoadErrorPolicy.isRefused(410))
    }

    @Test
    fun statusesThatCanPassAreRetried() {
        assertFalse(StreamLoadErrorPolicy.isRefused(408))
        assertFalse(StreamLoadErrorPolicy.isRefused(429))
        assertFalse(StreamLoadErrorPolicy.isRefused(500))
        assertFalse(StreamLoadErrorPolicy.isRefused(503))
        assertFalse(StreamLoadErrorPolicy.isRefused(206))
    }

    @Test
    fun lostConnectionIsNotARefusal() {
        assertNull(StreamLoadErrorPolicy.refusedStatus(SocketTimeoutException()))
        assertNull(StreamLoadErrorPolicy.refusedStatus(IOException(SocketTimeoutException())))
        assertNull(StreamLoadErrorPolicy.refusedStatus(null))
    }
}
