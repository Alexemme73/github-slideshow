package com.tubemusic.app.data

import com.tubemusic.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class JamendoRepository(
    private val clientId: String = DEMO_CLIENT_ID
) {
    suspend fun top(limit: Int = 30): List<Track> = fetch(
        mapOf(
            "limit" to limit.toString(),
            "order" to "popularity_month",
            "groupby" to "artist_id",
            "featured" to "1"
        )
    )

    suspend fun newReleases(limit: Int = 30): List<Track> = fetch(
        mapOf(
            "limit" to limit.toString(),
            "order" to "releasedate_desc",
            "groupby" to "artist_id"
        )
    )

    suspend fun byGenre(genre: String, limit: Int = 40): List<Track> = fetch(
        mapOf(
            "limit" to limit.toString(),
            "tags" to genre,
            "featured" to "1",
            "groupby" to "artist_id",
            "boost" to "popularity_month"
        )
    )

    suspend fun byYear(year: Int, limit: Int = 50): List<Track> = fetch(
        mapOf(
            "limit" to limit.toString(),
            "datebetween" to "$year-01-01_${year}-12-31",
            "order" to "popularity_total",
            "groupby" to "artist_id"
        )
    )

    suspend fun search(query: String, limit: Int = 50): List<Track> = fetch(
        mapOf(
            "limit" to limit.toString(),
            "search" to query,
            "boost" to "popularity_month"
        )
    )

    private suspend fun fetch(extra: Map<String, String>): List<Track> = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "client_id" to clientId,
            "format" to "json",
            "audioformat" to "mp32",
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
            setRequestProperty("User-Agent", "TubeMusic/0.1")
        }
        try {
            if (connection.responseCode !in 200..299) error("Jamendo HTTP ${connection.responseCode}")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONObject(json).getJSONArray("results")
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val stream = o.optString("audio")
                    if (stream.isBlank()) continue
                    add(
                        Track(
                            id = o.optString("id"),
                            title = o.optString("name", "Senza titolo"),
                            artist = o.optString("artist_name", "Artista"),
                            album = o.optString("album_name", ""),
                            imageUrl = o.optString("image", o.optString("album_image", "")),
                            streamUrl = stream,
                            durationSeconds = o.optInt("duration", 0),
                            releaseDate = o.optString("releasedate", "")
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        // Client ID pubblicato dalla documentazione Jamendo esclusivamente per test/read API.
        // Per distribuire l'app, sostituire con un proprio client_id dal Developer Portal Jamendo.
        const val DEMO_CLIENT_ID = "709fa152"
    }
}
