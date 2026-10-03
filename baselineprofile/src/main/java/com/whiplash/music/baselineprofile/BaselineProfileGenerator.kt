// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code the app runs on its busiest paths, so Android can
 * compile it ahead of time: starting up to Home, scrolling Home, and
 * opening the other tabs. Uses whatever library is on the device; with
 * nothing played yet Home still has its Quick Picks and shelves.
 *
 * Note: generating reinstalls the app on the device and then removes it,
 * which wipes its data, so run it on a test device or restore a backup after.
 * (No startup profile: that only helps together with R8, which is off.)
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Home")), 15_000)

        // Home: scroll down through Speed dial, Quick Picks and the shelves, and back.
        device.findObject(By.scrollable(true))?.let { list ->
            repeat(3) { list.fling(Direction.DOWN); device.waitForIdle() }
            repeat(3) { list.fling(Direction.UP); device.waitForIdle() }
        }

        // The other main tabs.
        for (tab in listOf("Search", "Library", "Favorites", "Playlists", "Home")) {
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
            device.wait(Until.hasObject(By.text(tab)), 5_000)
        }
    }

    private companion object {
        const val PACKAGE = "com.whiplash.music"
    }
}
