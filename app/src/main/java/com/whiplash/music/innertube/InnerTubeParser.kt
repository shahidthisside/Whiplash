// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.innertube

import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.recommend.UploadKind
import org.json.JSONArray
import org.json.JSONObject

/** Where the next radio page continues from. */
data class InnerTubeCursor(val token: String, val playlistId: String)

class InnerTubePage(val items: List<InnerTubeTrack>, val next: InnerTubeCursor?)

class InnerTubeRelated(val songs: List<InnerTubeTrack>, val artists: List<String>)

class InnerTubeTrack(val track: PlayableItem.YoutubeTrack, val kind: UploadKind)

/** Turns YouTube Music's (deeply nested, renderer-based) JSON into tracks. Pure, so it's unit-tested on real responses. */
object InnerTubeParser {

    fun radioPage(json: JSONObject): InnerTubePage {
        val panel = findFirst(json, "playlistPanelRenderer")
            ?: json.optJSONObject("continuationContents")?.optJSONObject("playlistPanelContinuation")
            ?: return InnerTubePage(emptyList(), null)
        val items = panel.optJSONArray("contents").objects().mapNotNull { entry ->
            val video = entry.optJSONObject("playlistPanelVideoRenderer")
                ?: entry.optJSONObject("playlistPanelVideoWrapperRenderer")
                    ?.optJSONObject("primaryRenderer")?.optJSONObject("playlistPanelVideoRenderer")
            video?.let(::panelTrack)
        }
        val token = panel.optJSONArray("continuations").objects().firstNotNullOfOrNull {
            (it.optJSONObject("nextRadioContinuationData") ?: it.optJSONObject("nextContinuationData"))?.optString("continuation")
        }?.takeIf { it.isNotEmpty() }
        val playlistId = panel.optString("playlistId").ifEmpty { null }
            ?: items.firstOrNull()?.let { findFirstString(json, "playlistId") }
        return InnerTubePage(items, if (token != null && playlistId != null) InnerTubeCursor(token, playlistId) else null)
    }

    /** The "Related" tab's browse id in a `next` response. */
    fun relatedBrowseId(json: JSONObject): String? {
        val out = mutableListOf<String>()
        collectStrings(json, "browseId", out)
        return out.firstOrNull { it.startsWith("MPTR") }
    }

    fun related(json: JSONObject): InnerTubeRelated {
        val songs = mutableListOf<InnerTubeTrack>()
        val artists = mutableListOf<String>()
        val shelves = mutableListOf<JSONObject>()
        collect(json, "musicCarouselShelfRenderer", shelves)
        for (shelf in shelves) {
            val title = shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title").text()
            val contents = shelf.optJSONArray("contents").objects()
            when {
                // Live versions and covers of the same song: not what "related" means.
                title.equals("Other performances", ignoreCase = true) -> Unit
                title.equals("Similar artists", ignoreCase = true) ->
                    contents.mapNotNullTo(artists) { it.optJSONObject("musicTwoRowItemRenderer")?.optJSONObject("title").text().ifEmpty { null } }
                else -> contents.mapNotNullTo(songs) { it.optJSONObject("musicResponsiveListItemRenderer")?.let(::listTrack) }
            }
        }
        return InnerTubeRelated(songs.distinctBy { it.track.id }, artists)
    }

    private fun panelTrack(v: JSONObject): InnerTubeTrack? {
        val id = v.optString("videoId").ifEmpty { return null }
        val title = v.optJSONObject("title").text().ifEmpty { return null }
        val byline = v.optJSONObject("longBylineText")?.optJSONArray("runs").objects()
        val artists = byline.filter { pageType(it) == "MUSIC_PAGE_TYPE_ARTIST" }.map { it.optString("text") }
        val album = byline.firstOrNull { pageType(it) == "MUSIC_PAGE_TYPE_ALBUM" }?.optString("text")
        val bylineText = byline.joinToString("") { it.optString("text") }
        val artist = artists.joinToString(" & ").ifEmpty { bylineText.substringBefore(" • ") }
        val type = v.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint").videoType()
        return InnerTubeTrack(
            PlayableItem.YoutubeTrack(id, title, artist, album, artwork(v.optJSONObject("thumbnail")), parseLength(v.optJSONObject("lengthText").text())),
            kindOf(type, hasViews = VIEWS_RX.containsMatchIn(bylineText)),
        )
    }

    private fun listTrack(r: JSONObject): InnerTubeTrack? {
        val columns = r.optJSONArray("flexColumns").objects().map {
            it.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text") ?: JSONObject()
        }
        val titleRuns = columns.getOrNull(0)?.optJSONArray("runs").objects()
        val watch = titleRuns.firstNotNullOfOrNull { it.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint") }
            ?: findFirst(r, "watchEndpoint")
        val id = watch?.optString("videoId")?.ifEmpty { null } ?: return null
        val title = titleRuns.joinToString("") { it.optString("text") }.ifEmpty { return null }
        val credit = columns.getOrNull(1)?.optJSONArray("runs").objects()
        val artists = credit.filter { pageType(it) == "MUSIC_PAGE_TYPE_ARTIST" }.map { it.optString("text") }
        val creditText = credit.joinToString("") { it.optString("text") }
        val artist = artists.joinToString(" & ").ifEmpty { creditText.substringBefore(" • ") }
        val album = columns.getOrNull(2)?.optJSONArray("runs").objects().firstOrNull { pageType(it) == "MUSIC_PAGE_TYPE_ALBUM" }?.optString("text")
        val type = findFirst(r, "watchEndpointMusicConfig")?.optString("musicVideoType")
        val length = r.optJSONArray("fixedColumns").objects().firstOrNull()
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text").text()
        return InnerTubeTrack(
            PlayableItem.YoutubeTrack(id, title, artist, album, artwork(findFirst(r, "musicThumbnailRenderer")?.optJSONObject("thumbnail")), parseLength(length)),
            kindOf(type, hasViews = VIEWS_RX.containsMatchIn(creditText)),
        )
    }

    /**
     * ATV is the official audio track, OMV an official video, UGC a fan upload.
     * Anonymous responses label many audio tracks PODCAST_EPISODE, so for
     * anything else a view count in the credits is what marks a video.
     */
    internal fun kindOf(type: String?, hasViews: Boolean): UploadKind = when (type) {
        "MUSIC_VIDEO_TYPE_ATV" -> UploadKind.AUDIO
        "MUSIC_VIDEO_TYPE_OMV" -> UploadKind.OFFICIAL_VIDEO
        "MUSIC_VIDEO_TYPE_UGC" -> UploadKind.USER_VIDEO
        else -> if (hasViews) UploadKind.OFFICIAL_VIDEO else UploadKind.AUDIO
    }

    internal fun parseLength(text: String?): Long {
        val parts = text?.trim()?.split(':')?.map { it.toLongOrNull() ?: return 0L } ?: return 0L
        if (parts.isEmpty() || parts.size > 3) return 0L
        return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
    }

    /** The largest thumbnail; YT Music's square covers are resized up from their 60/120px defaults. */
    private fun artwork(thumbnail: JSONObject?): String? {
        val url = thumbnail?.optJSONArray("thumbnails").objects().lastOrNull()?.optString("url")?.ifEmpty { null } ?: return null
        return url.replace(SIZE_RX, "=w544-h544")
    }

    private fun JSONObject?.videoType(): String? =
        this?.optJSONObject("watchEndpointMusicSupportedConfigs")?.optJSONObject("watchEndpointMusicConfig")?.optString("musicVideoType")

    private fun pageType(run: JSONObject): String? =
        run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            ?.optJSONObject("browseEndpointContextSupportedConfigs")?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")

    private fun JSONObject?.text(): String {
        if (this == null) return ""
        optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        return optJSONArray("runs").objects().joinToString("") { it.optString("text") }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun findFirst(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                for (k in node.keys()) findFirst(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findFirst(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findFirstString(node: Any?, key: String): String? {
        val out = mutableListOf<String>()
        collectStrings(node, key, out)
        return out.firstOrNull()
    }

    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> for (k in node.keys()) {
                val v = node.opt(k)
                if (k == key && v is JSONObject) out += v else collect(v, key, out)
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }

    private fun collectStrings(node: Any?, key: String, out: MutableList<String>) {
        when (node) {
            is JSONObject -> for (k in node.keys()) {
                val v = node.opt(k)
                if (k == key && v is String) out += v else collectStrings(v, key, out)
            }
            is JSONArray -> for (i in 0 until node.length()) collectStrings(node.opt(i), key, out)
        }
    }

    private val VIEWS_RX = Regex("\\d[\\d.,]*[KMB]? views", RegexOption.IGNORE_CASE)
    private val SIZE_RX = Regex("=w\\d+-h\\d+")
}
