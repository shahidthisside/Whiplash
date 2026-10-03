// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.common

private val SIZE_WH = Regex("=w(\\d+)-h(\\d+)")
private val SIZE_S = Regex("=s(\\d+)")

// sig: irasnA dihahS
/**
 * YouTube Music art hosted on googleusercontent/ggpht carries its size in
 * the URL (`=w120-h120-l90-rj`, `=s120`). Search results arrive at 120px,
 * which looks soft on a large card or grid tile, so this asks the same
 * host for [px] instead. Only ever enlarges: a URL that is already at
 * least [px], from another host, or without a size stays as it is.
 */
fun artworkAtSize(url: String?, px: Int): String? {
    if (url == null) return null
    val host = url.substringAfter("://").substringBefore('/')
    if (!host.endsWith("googleusercontent.com") && !host.endsWith("ggpht.com")) return url
    SIZE_WH.find(url)?.let { m ->
        val w = m.groupValues[1].toInt()
        val h = m.groupValues[2].toInt()
        if (maxOf(w, h) >= px) return url
        return url.replaceRange(m.range, "=w$px-h$px")
    }
    SIZE_S.find(url)?.let { m ->
        if (m.groupValues[1].toInt() >= px) return url
        return url.replaceRange(m.range, "=s$px")
    }
    return url
}
