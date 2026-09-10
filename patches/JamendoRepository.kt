package com.tubemusic.app.data

import com.tubemusic.app.model.RadioStation
import com.tubemusic.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class JamendoRepository(
    private val clientId: String = DEMO_CLIENT_ID,
    private val radioFallback: RadioBrowserRepository = RadioBrowserRepository()
) {
    suspend fun top(limit: Int = 30): List<Track> = fetchWithFallback(
        mapOf("limit" to limit.toString(), "order" to "popularity_month", "groupby" to "artist_id", "type" to "single albumtrack"),
        null, limit
    )

    suspend fun newReleases(limit: Int = 30): List<Track> = fetchWithFallback(
        mapOf("limit" to limit.toString(), "order" to "releasedate_desc", "groupby" to "artist_id", "type" to "single albumtrack"),
        null, limit
    )

    suspend fun byGenre(genre: String, limit: Int = 40): List<Track> = fetchWithFallback(
        mapOf("limit" to limit.toString(), "fuzzytags" to genre, "groupby" to "artist_id", "boost" to "popularity_month", "type" to "single albumtrack"),
        genre, limit
    )

    suspend fun byYear(year: Int, limit: Int = 50): List<Track> = fetchWithFallback(
        mapOf("limit" to limit.toString(), "datebetween" to "$year-01-01_${year}-12-31", "order" to "popularity_total", "groupby" to "artist_id", "type" to "single albumtrack"),
        null, limit
    )

    suspend fun search(query: String, limit: Int = 50): List<Track> = fetchWithFallback(
        mapOf("limit" to limit.toString(), "search" to query, "boost" to "popularity_month", "type" to "single albumtrack"),
        query, limit
    )

    private suspend fun fetchWithFallback(extra: Map<String, String>, fallbackQuery: String?, limit: Int): List<Track> {
        val jamendo = runCatching { fetch(extra) }.getOrDefault(emptyList())
        if (jamendo.isNotEmpty()) return jamendo

        val radios = runCatching { radioFallback.italianStations(180) }.getOrDefault(emptyList())
            .filter { it.streamUrl.startsWith("https://", ignoreCase = true) }
        if (radios.isEmpty()) return emptyList()

        val filtered = if (fallbackQuery.isNullOrBlank()) radios else {
            val q = fallbackQuery.lowercase()
            radios.filter { (it.name + " " + it.tags + " " + it.state).lowercase().contains(q) }.ifEmpty { radios }
        }
        return filtered.take(limit).map(::radioAsTrack)
    }

    private fun radioAsTrack(station: RadioStation): Track = Track(
        id = "radio-fallback:${station.id}",
        title = station.name,
        artist = "Radio streaming gratuita",
        album = station.tags.ifBlank { "Radio Italia" },
        imageUrl = station.imageUrl,
        streamUrl = station.streamUrl,
        durationSeconds = 0,
        releaseDate = ""
    )

    private suspend fun fetch(extra: Map<String, String>): List<Track> = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "client_id" to clientId,
            "format" to "json",
            "audioformat" to "mp31",
            "imagesize" to "300"
        ).apply { putAll(extra) }

        val query = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val connection = (URL("https://api.jamendo.com/v3.0/tracks/?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 18000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TubeMusic/0.5")
        }
        try {
            if (connection.responseCode !in 200..299) error("Jamendo HTTP ${connection.responseCode}")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val headers = root.optJSONObject("headers")
            if (headers != null && headers.optString("status") != "success") {
                error(headers.optString("error_message", "Jamendo non disponibile"))
            }
            val array = root.optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val stream = o.optString("audio")
                    if (stream.isBlank()) continue
                    add(Track(
                        id = "jamendo:${o.optString("id")}",
                        title = o.optString("name", "Senza titolo"),
                        artist = o.optString("artist_name", "Artista"),
                        album = o.optString("album_name", ""),
                        imageUrl = o.optString("image", o.optString("album_image", "")),
                        streamUrl = stream,
                        durationSeconds = o.optInt("duration", 0),
                        releaseDate = o.optString("releasedate", "")
                    ))
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEMO_CLIENT_ID = "709fa152"
    }
}
