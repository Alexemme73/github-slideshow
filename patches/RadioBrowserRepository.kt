package com.tubemusic.app.data

import com.tubemusic.app.model.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class RadioBrowserRepository {
    private val servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info"
    )

    suspend fun italianStations(limit: Int = 150): List<RadioStation> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (server in servers) {
            try {
                return@withContext fetchStations(server, limit)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Servizio radio non disponibile")
    }

    suspend fun registerClick(stationId: String) = withContext(Dispatchers.IO) {
        if (stationId.isBlank()) return@withContext
        for (server in servers) {
            try {
                val connection = (URL("$server/json/url/$stationId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "TubeMusic/0.4")
                }
                try {
                    connection.responseCode
                    return@withContext
                } finally {
                    connection.disconnect()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun fetchStations(server: String, limit: Int): List<RadioStation> {
        val url = "$server/json/stations/bycountrycodeexact/IT" +
            "?order=clickcount&reverse=true&hidebroken=true&limit=$limit"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TubeMusic/0.4")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Radio Browser HTTP ${connection.responseCode}")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            val seen = HashSet<String>()
            return buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    if (o.optInt("lastcheckok", 0) != 1) continue
                    val id = o.optString("stationuuid")
                    val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
                    if (id.isBlank() || stream.isBlank()) continue
                    if (!stream.startsWith("http://") && !stream.startsWith("https://")) continue
                    if (!seen.add(id)) continue
                    add(
                        RadioStation(
                            id = id,
                            name = o.optString("name", "Radio").trim(),
                            streamUrl = stream,
                            imageUrl = o.optString("favicon"),
                            tags = o.optString("tags"),
                            state = o.optString("state"),
                            codec = o.optString("codec"),
                            bitrate = o.optInt("bitrate", 0),
                            votes = o.optInt("votes", 0),
                            clicks = o.optInt("clickcount", 0)
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
