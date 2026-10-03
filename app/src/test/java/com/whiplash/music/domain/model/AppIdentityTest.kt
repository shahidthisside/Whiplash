// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentityTest {
    @Test fun identityIsStable() {
        assertEquals("Shahid Ansari", AppIdentity.author)
        assertEquals("wl-sa26-7f3c92", AppIdentity.TAG)
        assertTrue(AppIdentity.consistent())
    }

    @Test fun userAgentNamesTheAppAndItsHome() {
        assertEquals(
            "Whiplash/1.1.0 (by Shahid Ansari; https://github.com/shahidthisside/Whiplash)",
            AppIdentity.userAgent("1.1.0"),
        )
    }

    @Test fun creditIncludesTheTag() {
        assertEquals("Whiplash by Shahid Ansari · wl-sa26-7f3c92", AppIdentity.credit())
    }
}
