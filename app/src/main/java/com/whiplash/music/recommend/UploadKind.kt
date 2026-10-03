// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.recommend

/** What kind of upload a track is, when YouTube Music has told us. */
enum class UploadKind { AUDIO, OFFICIAL_VIDEO, USER_VIDEO }

/**
 * Upload kinds seen in YouTube Music responses, by video id. Tracks don't
 * carry this themselves, so ranking and duplicate removal look it up here;
 * an unknown id just means "no extra information".
 */
object UploadKinds {
    private val kinds = Memo<String, UploadKind>(20_000)

    fun of(id: String): UploadKind? = kinds.get(id)

    fun record(id: String, kind: UploadKind) = kinds.put(id, kind)
}
