package com.whiplash.music.recommend

import java.util.concurrent.ConcurrentHashMap

/**
 * A small thread-safe cache for per-song text work (keys, vibes, language)
 * that's asked for again and again while a batch is ranked. Cleared
 * wholesale when full; it only ever holds derived values.
 */
internal class Memo<K : Any, V : Any>(private val max: Int = 4_000) {
    private val map = ConcurrentHashMap<K, V>()

    inline fun getOrPut(key: K, compute: () -> V): V {
        get(key)?.let { return it }
        val v = compute()
        put(key, v)
        return v
    }

    fun get(key: K): V? = map[key]

    fun put(key: K, value: V) {
        if (map.size >= max) map.clear()
        map[key] = value
    }
}
