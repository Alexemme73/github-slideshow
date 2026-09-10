package com.tubemusic.app.data

import android.content.Context
import com.tubemusic.app.model.ESoundPlaylist
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ESoundPlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ESoundPlaylist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val url = o.optString("url")
                    if (url.isBlank()) continue
                    add(
                        ESoundPlaylist(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = o.optString("name").ifBlank { "Playlist eSound" },
                            url = url
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(current: List<ESoundPlaylist>, name: String, url: String): List<ESoundPlaylist> {
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            "Incolla un link pubblico eSound valido"
        }
        val cleanName = name.trim().ifBlank { "Playlist eSound ${current.size + 1}" }
        val existing = current.indexOfFirst { it.url.equals(cleanUrl, ignoreCase = true) }
        val updated = if (existing >= 0) {
            current.toMutableList().apply { this[existing] = this[existing].copy(name = cleanName) }
        } else {
            current + ESoundPlaylist(UUID.randomUUID().toString(), cleanName, cleanUrl)
        }
        save(updated)
        return updated
    }

    fun remove(current: List<ESoundPlaylist>, id: String): List<ESoundPlaylist> {
        val updated = current.filterNot { it.id == id }
        save(updated)
        return updated
    }

    fun move(current: List<ESoundPlaylist>, index: Int, delta: Int): List<ESoundPlaylist> {
        val newIndex = index + delta
        if (index !in current.indices || newIndex !in current.indices) return current
        val updated = current.toMutableList().apply {
            val item = removeAt(index)
            add(newIndex, item)
        }
        save(updated)
        return updated
    }

    private fun save(playlists: List<ESoundPlaylist>) {
        val array = JSONArray()
        playlists.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("url", p.url)
            })
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "tubemusic_esound"
        private const val KEY_PLAYLISTS = "playlists"
    }
}
