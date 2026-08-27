package com.whiplash.music.domain.model

/**
 * User-selectable audio quality preference (CLAUDE.md section 61).
 *
 * Backed by real, verifiable capability: NewPipeExtractor's
 * [org.schabi.newpipe.extractor.stream.StreamInfo.getAudioStreams] returns
 * multiple bitrate options for the overwhelming majority of YouTube videos
 * (confirmed in Phase 7a/7b testing — 5 distinct audio streams for a real
 * video). This is a genuinely honorable setting, not a fake one.
 */
enum class AudioQuality {
    /** Let the provider pick automatically (currently: highest available). */
    AUTO,
    LOW,
    MEDIUM,
    HIGH,
    HIGHEST,
}
