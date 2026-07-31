package com.example.photos

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*

private val Context.dataStore by preferencesDataStore(name = "photos_prefs")

enum class ThemeChoice {
    Midnight,
    Emerald,
    Pink,
    Purple
}

enum class IconChoice(val componentName: String) {
    Default(".MainActivityIconDefault"),
    Midnight(".MainActivityIconMidnight"),
    Emerald(".MainActivityIconEmerald"),
    Pink(".MainActivityIconPink"),
    Purple(".MainActivityIconPurple")
}

class PhotoViewModel : ViewModel() {
    private val _photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val photos: StateFlow<List<PhotoItem>> = _photos
    private val _themeChoice = MutableStateFlow(ThemeChoice.Midnight)
    val themeChoice: StateFlow<ThemeChoice> = _themeChoice
    private val _iconChoice = MutableStateFlow(IconChoice.Default)
    val iconChoice: StateFlow<IconChoice> = _iconChoice

    private val _albumCovers = MutableStateFlow<Map<String, String>>(emptyMap())
    val albumCovers: StateFlow<Map<String, String>> = _albumCovers

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _customAlbums = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val customAlbums: StateFlow<Map<String, Set<String>>> = _customAlbums

    private val _selectedTab = MutableStateFlow(PhotosTab.All)
    val selectedTab: StateFlow<PhotosTab> = _selectedTab

    fun loadPhotos(context: Context) {
        viewModelScope.launch {
            val images = queryMedia(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
            val videos = queryMedia(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            val allPhotos = (images + videos).sortedByDescending { it.dateAdded }
            _photos.value = allPhotos

            val savedCovers = loadAlbumCovers(context)
            _albumCovers.value = if (savedCovers.isNotEmpty()) savedCovers else defaultCoverMap(allPhotos)
            _favorites.value = loadFavoriteUris(context)
            _customAlbums.value = loadCustomAlbums(context)
            _themeChoice.value = loadThemeChoice(context)
            _iconChoice.value = loadIconChoice(context)
        }
    }

    fun setSelectedTab(tab: PhotosTab) {
        _selectedTab.value = tab
    }

    fun setAlbumCover(context: Context, source: String, uriString: String) {
        viewModelScope.launch {
            saveAlbumCovers(context, _albumCovers.value + (source to uriString))
            _albumCovers.value = _albumCovers.value + (source to uriString)
        }
    }

    fun setThemeChoice(context: Context, choice: ThemeChoice) {
        viewModelScope.launch {
            saveThemeChoice(context, choice)
            _themeChoice.value = choice
        }
    }

    fun setIconChoice(context: Context, choice: IconChoice) {
        viewModelScope.launch {
            saveIconChoice(context, choice)
            _iconChoice.value = choice
        }
    }

    fun toggleFavorite(context: Context, photoUri: String) {
        viewModelScope.launch {
            val current = _favorites.value.toMutableSet()
            if (current.contains(photoUri)) current.remove(photoUri) else current.add(photoUri)
            saveFavoriteUris(context, current)
            _favorites.value = current
        }
    }

    fun createAlbum(context: Context, albumName: String) {
        viewModelScope.launch {
            val current = _customAlbums.value.toMutableMap()
            if (albumName.isBlank() || current.containsKey(albumName)) return@launch
            current[albumName] = emptySet()
            saveCustomAlbums(context, current)
            _customAlbums.value = current
        }
    }

    fun deleteAlbum(context: Context, albumName: String) {
        viewModelScope.launch {
            val current = _customAlbums.value.toMutableMap()
            current.remove(albumName)
            saveCustomAlbums(context, current)
            _customAlbums.value = current
        }
    }

    fun renameAlbum(context: Context, oldName: String, newName: String) {
        viewModelScope.launch {
            val current = _customAlbums.value.toMutableMap()
            if (!current.containsKey(oldName) || newName.isBlank()) return@launch
            val members = current.remove(oldName) ?: emptySet()
            current[newName] = members
            saveCustomAlbums(context, current)
            _customAlbums.value = current
        }
    }

    fun addPhotoToAlbum(context: Context, albumName: String, photoUri: String) {
        viewModelScope.launch {
            val current = _customAlbums.value.toMutableMap()
            val members = current[albumName]?.toMutableSet() ?: mutableSetOf()
            members.add(photoUri)
            current[albumName] = members
            saveCustomAlbums(context, current)
            _customAlbums.value = current
        }
    }

    fun removePhotoFromAlbum(context: Context, albumName: String, photoUri: String) {
        viewModelScope.launch {
            val current = _customAlbums.value.toMutableMap()
            val members = current[albumName]?.toMutableSet() ?: mutableSetOf()
            members.remove(photoUri)
            current[albumName] = members
            saveCustomAlbums(context, current)
            _customAlbums.value = current
        }
    }

    private suspend fun loadAlbumCovers(context: Context): Map<String, String> {
        val json = context.dataStore.data.first()[PreferenceKeys.albumCovers] ?: ""
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun saveAlbumCovers(context: Context, covers: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.albumCovers] = JSONObject(covers).toString()
        }
    }

    private suspend fun loadFavoriteUris(context: Context): Set<String> {
        return context.dataStore.data.first()[PreferenceKeys.favorites] ?: emptySet()
    }

    private suspend fun saveFavoriteUris(context: Context, uris: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.favorites] = uris
        }
    }

    private suspend fun loadThemeChoice(context: Context): ThemeChoice {
        val value = context.dataStore.data.first()[PreferenceKeys.themeChoice] ?: ThemeChoice.Midnight.name
        return ThemeChoice.values().find { it.name == value } ?: ThemeChoice.Midnight
    }

    private suspend fun saveThemeChoice(context: Context, themeChoice: ThemeChoice) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.themeChoice] = themeChoice.name
        }
    }

    private suspend fun loadIconChoice(context: Context): IconChoice {
        val value = context.dataStore.data.first()[PreferenceKeys.iconChoice] ?: IconChoice.Default.name
        return IconChoice.values().find { it.name == value } ?: IconChoice.Default
    }

    private suspend fun saveIconChoice(context: Context, iconChoice: IconChoice) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.iconChoice] = iconChoice.name
        }
    }

    private suspend fun loadCustomAlbums(context: Context): Map<String, Set<String>> {
        val json = context.dataStore.data.first()[PreferenceKeys.customAlbums] ?: ""
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key ->
                val array = obj.optJSONArray(key)
                if (array == null) emptySet() else (0 until array.length()).map { array.getString(it) }.toSet()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun saveCustomAlbums(context: Context, albums: Map<String, Set<String>>) {
        context.dataStore.edit { prefs ->
            val obj = JSONObject()
            albums.forEach { (album, members) ->
                val array = org.json.JSONArray()
                members.forEach { array.put(it) }
                obj.put(album, array)
            }
            prefs[PreferenceKeys.customAlbums] = obj.toString()
        }
    }

    private fun defaultCoverMap(items: List<PhotoItem>): Map<String, String> {
        return items.groupBy { it.bucket }.mapValues { entry -> entry.value.firstOrNull()?.uri.toString() ?: "" }
    }

    private suspend fun queryMedia(context: Context, collection: Uri, isVideo: Boolean): List<PhotoItem> {
        return withContext(Dispatchers.IO) {
            val projection = if (isVideo) {
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT,
                    MediaStore.Video.Media.DURATION
                )
            } else {
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT
                )
            }
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            val items = mutableListOf<PhotoItem>()
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val bucketIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateTakenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val durationIndex = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Media $id"
                    val bucket = cursor.getString(bucketIndex) ?: "Unknown"
                    val relativePath = cursor.getString(pathIndex) ?: "Unknown"
                    val dateAdded = cursor.getLong(dateAddedIndex)
                    val dateTaken = if (dateTakenIndex != -1) cursor.getLong(dateTakenIndex) else 0L
                    val mimeType = cursor.getString(mimeIndex) ?: if (isVideo) "video/*" else "image/*"
                    val size = cursor.getLong(sizeIndex)
                    val width = if (widthIndex != -1) cursor.getInt(widthIndex) else 0
                    val height = if (heightIndex != -1) cursor.getInt(heightIndex) else 0
                    val durationMillis = if (durationIndex != -1) cursor.getLong(durationIndex) else null
                    val source = detectSource(bucket, relativePath, name)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    items += PhotoItem(
                        uri = uri,
                        name = name,
                        source = source,
                        bucket = bucket,
                        relativePath = relativePath,
                        mimeType = mimeType,
                        size = size,
                        width = width,
                        height = height,
                        isVideo = isVideo,
                        dateAdded = dateAdded,
                        dateTaken = dateTaken,
                        durationMillis = durationMillis
                    )
                }
            }
            items
        }
    }

    private fun detectSource(bucket: String, relativePath: String, displayName: String): String {
        val lowerPath = relativePath.lowercase(Locale.getDefault())
        val lowerBucket = bucket.lowercase(Locale.getDefault())
        val lowerDisplay = displayName.lowercase(Locale.getDefault())
        return when {
            lowerBucket.contains("camera") || lowerPath.contains("dcim") -> "Camera"
            lowerBucket.contains("download") || lowerPath.contains("download") -> "Downloads"
            lowerPath.contains("pinterest") || lowerDisplay.contains("pinterest") -> "Pinterest"
            else -> "Other"
        }
    }

    private object PreferenceKeys {
        val albumCovers = stringPreferencesKey("album_covers_json")
        val favorites = stringSetPreferencesKey("favorite_uris")
        val themeChoice = stringPreferencesKey("theme_choice")
        val iconChoice = stringPreferencesKey("icon_choice")
        val customAlbums = stringPreferencesKey("custom_albums_json")
    }
}
