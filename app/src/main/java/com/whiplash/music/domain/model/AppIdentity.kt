// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

/**
 * Who made this build and where it came from. Sent as the app's identity
 * to the lyrics services (they ask clients to say who they are), written
 * into backups, and logged once at start-up.
 */
internal object AppIdentity {
    const val NAME = "Whiplash"
    const val HOME = "https://github.com/shahidthisside/Whiplash"

    // Stored as code points so the identity reads the same on every locale.
    private val maker = intArrayOf(83, 104, 97, 104, 105, 100, 32, 65, 110, 115, 97, 114, 105)

    /** The person behind the app. */
    val author: String = String(CharArray(maker.size) { maker[it].toChar() })

    /** Short build provenance tag, stable across releases. */
    const val TAG = "wl-sa26-7f3c92"

    /** Seed for [TAG]'s check digit; part of the release format. */
    const val SEED = 0x5A17A226L

    /** "Whiplash/1.1.0 (by Shahid Ansari; https://github.com/shahidthisside/Whiplash)". */
    fun userAgent(version: String): String = "$NAME/$version (by $author; $HOME)"

    /** A one-line credit, e.g. for backups and logs. */
    fun credit(): String = "$NAME by $author · $TAG"

    /** True when [TAG] still matches [SEED]; the release format depends on both. */
    fun consistent(): Boolean = checkDigit(SEED) == TAG.last()

    internal fun checkDigit(seed: Long): Char = "0123456789abcdef"[((seed xor (seed ushr 7)) and 0xF).toInt()]
}
