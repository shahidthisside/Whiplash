package com.whiplash.music.playback.provider.newpipe

import org.schabi.newpipe.extractor.Image

/**
 * Real, reported performance problem: every single artwork request across
 * the app — including small 40-48dp list rows in Home, Search, Queue, and
 * song-actions sheets — was picking `thumbnails.maxByOrNull { it.height }`,
 * i.e. always the *highest*-resolution thumbnail YouTube exposes for that
 * item (commonly 1280x720 `maxresdefault`). Coil then had to download that
 * full-size JPEG over the network and decode/downscale it in memory just
 * to paint a thumbnail a fraction of that size — real, unnecessary network
 * and CPU cost repeated for every row in every list, which is what made
 * Home/lists feel slow to load. NewPipeExtractor already exposes multiple
 * real resolution variants per thumbnail (verified against its own public
 * `Image` API — the same list real NewPipe's own low/medium/high quality
 * thumbnail setting picks from), so the fix is to pick the *smallest*
 * variant that's still large enough for the target UI size, rather than
 * downloading the largest one and shrinking it after the fact — same
 * visual result at the small sizes these lists render at, at a fraction
 * of the network/decode cost.
 *
 * Scoped deliberately to Home/Search list-row thumbnails only (this is
 * what was actually verified working on-device). Full-player artwork is
 * intentionally left untouched here.
 */

/** Comfortably covers every list-row/thumbnail use in the app (up to ~96dp on a 3x-density screen) without ever downloading more than necessary. */
private const val LIST_THUMBNAIL_MIN_HEIGHT_PX = 300

/**
 * Picks the smallest available [Image] whose height is at least
 * [minHeightPx], falling back to the largest available one if every
 * variant is smaller than that (some items only expose low-res
 * thumbnails — better to show a small real image than none at all).
 */
fun List<Image>.smallestAtLeast(minHeightPx: Int = LIST_THUMBNAIL_MIN_HEIGHT_PX): Image? =
    filter { it.height >= minHeightPx }.minByOrNull { it.height }
        ?: maxByOrNull { it.height }
