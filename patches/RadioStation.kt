package com.tubemusic.app.model

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val imageUrl: String,
    val tags: String,
    val state: String,
    val codec: String,
    val bitrate: Int,
    val votes: Int,
    val clicks: Int
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId("radio:$id")
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist("Radio Italia · ${state.ifBlank { "Streaming" }}")
                .setAlbumTitle(tags)
                .setArtworkUri(imageUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                .build()
        )
        .build()
}
