package com.whiplash.music.recommend

import java.io.File

/** Where the radio keeps what it learned (a small JSON document). */
interface LearnedStore {
    fun load(): String?
    fun save(json: String)
    /** Bumped when the listener clears history, so in-memory state is dropped too. */
    val generation: Int
}

class FileLearnedStore(private val file: File) : LearnedStore {
    @Volatile override var generation = 0
        private set

    override fun load(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    override fun save(json: String) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    fun clear() {
        generation++
        runCatching { file.delete() }
    }
}
