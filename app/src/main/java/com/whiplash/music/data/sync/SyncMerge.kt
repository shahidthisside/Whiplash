package com.whiplash.music.data.sync

/**
 * Three-way merge of two devices' libraries against the last version both
 * agreed on ([base], the snapshot saved after the previous successful sync).
 *
 * Comparing against the base is what lets deletions sync without storing
 * tombstones: something in the base but gone on one side was deleted there,
 * while something missing from the base is new. Rules per item:
 * - changed on one side only → that side's version (including deletion);
 * - changed on both → edit beats delete, otherwise a per-type rule below.
 *
 * A null [base] is the first sync on this device: nothing counts as deleted,
 * so the two libraries are combined, and settings come from the cloud (a
 * fresh install should pick up your theme rather than overwrite it).
 */
object SyncMerge {

    const val MAX_HISTORY = 200

    fun merge(base: SyncSnapshot?, local: SyncSnapshot, remote: SyncSnapshot): SyncSnapshot {
        val b = base ?: SyncSnapshot()
        val firstSync = base == null

        val favorites = merge3(b.favorites, local.favorites, remote.favorites) { l, r, _ -> minOf(l, r) }
        val pinned = merge3(b.pinned, local.pinned, remote.pinned) { l, r, _ -> maxOf(l, r) }
        val history = merge3(b.history, local.history, remote.history) { l, _, _ -> l }
            .entries.sortedByDescending { it.value }.take(MAX_HISTORY).associate { it.key to it.value }
        val playlists = merge3(b.playlists, local.playlists, remote.playlists) { l, r, old -> mergePlaylist(old, l, r) }
        val replay = merge3(b.replay, local.replay, remote.replay) { l, r, _ ->
            l.copy(
                plays = maxOf(l.plays, r.plays),
                listenedMs = maxOf(l.listenedMs, r.listenedMs),
                lastPlayedAtEpochMs = maxOf(l.lastPlayedAtEpochMs, r.lastPlayedAtEpochMs),
            )
        }
        val settings = merge3(b.settings, local.settings, remote.settings) { l, r, _ -> if (firstSync) r else l }
        val lyricOffsets = merge3(b.lyricOffsets, local.lyricOffsets, remote.lyricOffsets) { l, r, _ -> if (firstSync) r else l }

        val profile = when {
            local.profile == remote.profile -> local.profile
            local.profile == b.profile -> remote.profile
            remote.profile == b.profile -> local.profile
            else -> listOfNotNull(local.profile, remote.profile).maxByOrNull { it.updatedAtEpochMs }
        }

        val merged = SyncSnapshot(
            favorites = favorites,
            pinned = pinned,
            history = history,
            playlists = playlists,
            replay = replay,
            settings = settings,
            lyricOffsets = lyricOffsets,
            profile = profile,
        )
        // This phone's metadata wins; the cloud fills in songs it has never seen.
        val known = remote.songs + local.songs
        val songs = merged.referencedIds().mapNotNull { id -> known[id]?.let { id to it } }.toMap()
        return merged.copy(songs = songs)
    }

    /**
     * What to write for a sync where some [excluded] categories are switched
     * off: this phone keeps its own data for them ([Plan.toApply]), the cloud
     * keeps its copy ([Plan.toUpload]), and the saved base leaves them empty
     * so turning a category back on combines both sides instead of treating
     * the difference as deletions.
     */
    data class Plan(val toApply: SyncSnapshot, val toUpload: SyncSnapshot, val newBase: SyncSnapshot)

    fun plan(merged: SyncSnapshot, local: SyncSnapshot, remote: SyncSnapshot, excluded: Set<SyncCategory>): Plan {
        if (excluded.isEmpty()) return Plan(merged, merged, merged)
        var apply = merged
        var upload = merged
        var base = merged
        excluded.forEach { c ->
            apply = c.copy(from = local, into = apply)
            upload = c.copy(from = remote, into = upload)
            base = c.copy(from = SyncSnapshot(), into = base)
        }
        val known = remote.songs + local.songs
        fun SyncSnapshot.withSongs() = copy(songs = referencedIds().mapNotNull { id -> known[id]?.let { id to it } }.toMap())
        return Plan(apply.withSongs(), upload.withSongs(), base.copy(songs = upload.withSongs().songs))
    }

    /**
     * Per-key three-way merge. [bothChanged] gets (local, remote, base) when
     * each side changed the same key differently and neither deleted it.
     */
    fun <K, V> merge3(
        base: Map<K, V>,
        local: Map<K, V>,
        remote: Map<K, V>,
        bothChanged: (V, V, V?) -> V,
    ): Map<K, V> {
        val out = LinkedHashMap<K, V>()
        val keys = LinkedHashSet<K>().apply { addAll(local.keys); addAll(remote.keys); addAll(base.keys) }
        for (key in keys) {
            val b = base[key]
            val l = local[key]
            val r = remote[key]
            val value: V? = when {
                l == r -> l
                l == b -> r // only the cloud changed it (or deleted it)
                r == b -> l // only this phone changed it (or deleted it)
                l == null -> r // deleted here, edited elsewhere: keep the edit
                r == null -> l
                else -> bothChanged(l, r, b)
            }
            if (value != null) out[key] = value
        }
        return out
    }

    /**
     * Both phones edited the same playlist. Name, cover and pin come from
     * whichever edited it last; the track list keeps this phone's order,
     * drops tracks the other phone removed and appends tracks it added.
     */
    fun mergePlaylist(base: SyncPlaylist?, local: SyncPlaylist, remote: SyncPlaylist): SyncPlaylist {
        val newer = if (remote.updatedAtEpochMs > local.updatedAtEpochMs) remote else local
        val baseIds = base?.tracks?.map { it.id }?.toSet() ?: emptySet()
        val localIds = local.tracks.map { it.id }.toSet()
        val remoteIds = remote.tracks.map { it.id }.toSet()
        val removedRemotely = baseIds - remoteIds
        val tracks = local.tracks.filter { it.id !in removedRemotely } +
            remote.tracks.filter { it.id !in baseIds && it.id !in localIds }
        return newer.copy(
            createdAtEpochMs = local.createdAtEpochMs,
            updatedAtEpochMs = maxOf(local.updatedAtEpochMs, remote.updatedAtEpochMs),
            tracks = tracks.distinctBy { it.id },
        )
    }
}
