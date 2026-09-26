package com.whiplash.music.ui.common

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
/**
 * The in-app "Reduce animations" setting, provided once at the root of the
 * composition (MainActivity) so every screen sees it without threading a
 * parameter through. Defaults to false for previews and tests.
 */
val LocalAppReduceMotion = compositionLocalOf { false }

@Composable
fun isReducedMotionEnabled(): Boolean {
    if (LocalAppReduceMotion.current) return true
    val context = LocalContext.current
    return try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Settings.SettingNotFoundException) {
        false
    }
}
