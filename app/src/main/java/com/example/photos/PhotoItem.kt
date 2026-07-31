package com.example.photos

import android.net.Uri

data class PhotoItem(
    val uri: Uri,
    val name: String,
    val source: String,
    val bucket: String,
    val relativePath: String,
    val mimeType: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val isVideo: Boolean,
    val dateAdded: Long,
    val dateTaken: Long,
    val durationMillis: Long?
)
