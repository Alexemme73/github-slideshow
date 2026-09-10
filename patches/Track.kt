package com.tubemusic.app.model

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String,
    val streamUrl: String,
    val durationSeconds: Int,
    val releaseDate: String
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId("jamendo:$id")
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(imageUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                .build()
        )
        .build()
}
