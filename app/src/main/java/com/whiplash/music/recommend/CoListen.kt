package com.whiplash.music.recommend

import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Which artists go together, learned rather than hand-written: artists the
 * listener plays through in the same sitting, and artists YouTube Music puts
 * on the same radio. Similarity is co-occurrence normalised by how common
 * each artist is (cosine over co-occurrence counts), so a huge artist that
 * shows up everywhere doesn't look close to everyone.
 */
class CoListen {
    /** From radio pages; persisted. */
    private val pages = Pairs()
    /** From the listener's own sessions; rebuilt from play history. */
    private var history = Pairs()

    fun addPage(artistKeys: List<String>) {
        val keys = artistKeys.filter { it.isNotEmpty() }.distinct().take(MAX_PAGE_ARTISTS)
        for (i in keys.indices) for (j in i + 1 until keys.size) pages.add(keys[i], keys[j], 1.0)
        keys.forEach { pages.count(it, 1.0) }
        pages.trim()
    }

    /**
     * Sessions are runs of plays with gaps under [SESSION_GAP_MS]; within
     * one, artists heard through (not skipped) near each other are paired,
     * closer plays counting more. A finished song counts double.
     */
    fun rebuildHistory(plays: List<PastPlay>) {
        val next = Pairs()
        var session = mutableListOf<Pair<String, Double>>()
        var last = Long.MIN_VALUE
        fun flush() {
            for (i in session.indices) {
                val (a, wa) = session[i]
                next.count(a, wa)
                for (j in i + 1 until minOf(session.size, i + 1 + WINDOW)) {
                    val (b, wb) = session[j]
                    if (a != b) next.add(a, b, minOf(wa, wb) / (j - i))
                }
            }
            session = mutableListOf()
        }
        for (p in plays.sortedBy { it.startedAt }) {
            if (p.startedAt - last > SESSION_GAP_MS) flush()
            last = p.startedAt
            if (p.skipped || p.artistKey.isEmpty()) continue
            session += p.artistKey to if (p.completed) 2.0 else 1.0
        }
        flush()
        next.trim()
        history = next
    }

    /** 0…1: how close [a] and [b] are. Same artist is 1. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        // What the listener does outweighs what YouTube suggests.
        return maxOf(history.cosine(a, b), pages.cosine(a, b) * PAGE_TRUST)
    }

    /** How close [key] is to the closest of [anchors] (other artists only). */
    fun affinity(key: String, anchors: Collection<String>): Double =
        anchors.filter { it != key }.maxOfOrNull { similarity(key, it) } ?: 0.0

    fun toJson(): JSONObject = pages.toJson()

    fun loadPages(json: JSONObject?) { if (json != null) pages.load(json) }

    private class Pairs {
        val totals = HashMap<String, Double>()
        val links = HashMap<String, HashMap<String, Double>>()

        fun count(a: String, w: Double) { totals[a] = (totals[a] ?: 0.0) + w }

        fun add(a: String, b: String, w: Double) {
            links.getOrPut(a) { HashMap() }.merge(b, w, Double::plus)
            links.getOrPut(b) { HashMap() }.merge(a, w, Double::plus)
        }

        fun cosine(a: String, b: String): Double {
            val c = links[a]?.get(b) ?: return 0.0
            val na = totals[a] ?: return 0.0
            val nb = totals[b] ?: return 0.0
            // Needs repeated evidence: one shared session proves little.
            if (c < MIN_EVIDENCE) return 0.0
            return (c / sqrt(na * nb)).coerceIn(0.0, 1.0)
        }

        /** Keeps memory bounded: the most-seen artists and their strongest links. */
        fun trim() {
            if (totals.size > MAX_ARTISTS) {
                val keep = totals.entries.sortedByDescending { it.value }.take(MAX_ARTISTS).mapTo(HashSet()) { it.key }
                totals.keys.retainAll(keep)
                links.keys.retainAll(keep)
                links.values.forEach { it.keys.retainAll(keep) }
            }
            for ((k, m) in links) if (m.size > MAX_LINKS) {
                val keep = m.entries.sortedByDescending { it.value }.take(MAX_LINKS)
                links[k] = HashMap(keep.associate { it.key to it.value })
            }
        }

        fun toJson(): JSONObject {
            val t = JSONObject(); totals.forEach { (k, v) -> t.put(k, v) }
            val l = JSONObject()
            links.forEach { (k, m) -> val o = JSONObject(); m.forEach { (b, v) -> o.put(b, v) }; l.put(k, o) }
            return JSONObject().put("totals", t).put("links", l)
        }

        fun load(json: JSONObject) {
            val t = json.optJSONObject("totals") ?: return
            val l = json.optJSONObject("links") ?: return
            t.keys().forEach { totals[it] = t.optDouble(it, 0.0) }
            l.keys().forEach { k ->
                val o = l.optJSONObject(k) ?: return@forEach
                val m = HashMap<String, Double>()
                o.keys().forEach { m[it] = o.optDouble(it, 0.0) }
                links[k] = m
            }
        }
    }

    private companion object {
        const val SESSION_GAP_MS = 30 * 60_000L
        const val WINDOW = 5
        const val MAX_PAGE_ARTISTS = 40
        const val MAX_ARTISTS = 1_500
        const val MAX_LINKS = 60
        const val MIN_EVIDENCE = 1.5
        const val PAGE_TRUST = 0.8
    }
}
