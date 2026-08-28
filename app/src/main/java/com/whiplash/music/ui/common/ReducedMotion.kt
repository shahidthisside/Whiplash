package com.whiplash.music.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Reads the real system "Remove animations" / reduced-motion preference
 * (CLAUDE.md section 55: "when reduced-motion settings are enabled,
 * reduce nonessential animations"). Most one-shot Compose animations
 * ([androidx.compose.animation.core.tween], [androidx.compose.animation.core.spring])
 * already scale automatically with [Settings.Global.ANIMATOR_DURATION_SCALE]
 * at the platform level, but continuously-repeating animations built on
 * [androidx.compose.animation.core.rememberInfiniteTransition] (e.g. the
 * search skeleton shimmer) are not guaranteed to — this reads the same
 * real system setting directly so those specific "nonessential" animations
 * can be skipped/frozen outright rather than relying on unconfirmed
 * platform behavior.
 */
@Composable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Settings.SettingNotFoundException) {
        false
    }
}
