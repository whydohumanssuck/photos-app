package com.example.photos

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.layout.ContentScale

private val sourceOrder = listOf("Camera", "Downloads", "Pinterest", "Other")

enum class PhotosTab(val title: String, val icon: ImageVector) {
    All("All", Icons.Default.GridOn),
    Albums("Albums", Icons.Default.Folder),
    Favorites("Favorites", Icons.Default.Favorite)
}

class MainActivity : ComponentActivity() {
    private val photoViewModel: PhotoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotosApp(photoViewModel)
        }
    }
}

@Composable
fun PhotosApp(viewModel: PhotoViewModel = viewModel()) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val albumCovers by viewModel.albumCovers.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val customAlbums by viewModel.customAlbums.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val themeChoice by viewModel.themeChoice.collectAsStateWithLifecycle()
    val iconChoice by viewModel.iconChoice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val packageManager = context.packageManager
    val iconBase = context.packageName

    val allGridState = rememberLazyGridState()
    val favoritesGridState = rememberLazyGridState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(iconChoice) {
        IconChoice.values().forEach { choice ->
            val aliasName = iconBase + choice.componentName
            val componentName = android.content.ComponentName(iconBase, aliasName)
            packageManager.setComponentEnabledSetting(
                componentName,
                if (choice == iconChoice) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms -> if (perms.values.any { it }) viewModel.loadPhotos(context) }
    )

    var search by remember { mutableStateOf("") }
    var selectedPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var albumCoverSource by remember { mutableStateOf<String?>(null) }
    var showCoverPicker by remember { mutableStateOf(false) }
    var activeAlbum by remember { mutableStateOf<String?>(null) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var batchSelection by remember { mutableStateOf(setOf<String>()) }
    var showBatchActions by remember { mutableStateOf(false) }
    var showAddToAlbumDialog by remember { mutableStateOf(false) }
    var selectedAddToAlbum by remember { mutableStateOf<String?>(null) }
    var showPhotoAddToAlbumDialog by remember { mutableStateOf(false) }
    var showRenameAlbumDialog by remember { mutableStateOf(false) }
    var renameAlbumName by remember { mutableStateOf("") }
    var editingAlbum by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            ))
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            ))
        }
    }

    val filteredPhotos = photos.filter {
        it.name.contains(search, ignoreCase = true) ||
            it.bucket.contains(search, ignoreCase = true) ||
            it.source.contains(search, ignoreCase = true) ||
            it.relativePath.contains(search, ignoreCase = true)
    }

    val sourceGroups = filteredPhotos
        .groupBy { it.source }
        .toSortedMap(compareBy { sourceOrder.indexOf(it).let { index -> if (index == -1) Int.MAX_VALUE else index } })

    val albumGroups = filteredPhotos
        .groupBy { it.bucket }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)

    val appColors = when (themeChoice) {
        ThemeChoice.Midnight -> lightColors(
            primary = Color(0xFF64748B),
            primaryVariant = Color(0xFF0F172A),
            secondary = Color(0xFF82AAFF),
            background = Color(0xFF050712),
            surface = Color(0xFF111826),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
        ThemeChoice.Emerald -> lightColors(
            primary = Color(0xFF10B981),
            primaryVariant = Color(0xFF0F3323),
            secondary = Color(0xFF6EE7B7),
            background = Color(0xFF052E1B),
            surface = Color(0xFF0F3323),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
        ThemeChoice.Pink -> lightColors(
            primary = Color(0xFFF472B6),
            primaryVariant = Color(0xFF7C3AED),
            secondary = Color(0xFFF9A8D4),
            background = Color(0xFF2F0926),
            surface = Color(0xFF3A0B2E),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
        ThemeChoice.Purple -> lightColors(
            primary = Color(0xFF8B5CF6),
            primaryVariant = Color(0xFF312E81),
            secondary = Color(0xFF818CF8),
            background = Color(0xFF120E2B),
            surface = Color(0xFF1D1038),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    }

    MaterialTheme(colors = appColors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Gallery") },
                    backgroundColor = MaterialTheme.colors.primaryVariant,
                    contentColor = MaterialTheme.colors.onPrimary,
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            bottomBar = {
                BottomNavigation(backgroundColor = Color(0xFF111827)) {
                    PhotosTab.values().forEach { tab ->
                        BottomNavigationItem(
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            selected = selectedTab == tab,
                            onClick = { viewModel.setSelectedTab(tab) },
                            selectedContentColor = Color(0xFF93C5FD),
                            unselectedContentColor = Color(0xFF94A3B8)
                        )
                    }
                }
            },
            backgroundColor = Color(0xFF0F172A)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(12.dp)
            ) {
                if (selectedTab != PhotosTab.Favorites) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by filename or album") },
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            placeholderColor = Color(0x80FFFFFF),
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFFFFFFF)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                when (selectedTab) {
                    PhotosTab.All -> AllScreen(
                        groupedPhotos = sourceGroups,
                        albumCovers = albumCovers,
                        favorites = favorites,
                        selectedIds = batchSelection,
                        dateFormatter = dateFormatter,
                        onPhotoClick = { selectedPhoto = it },
                        onCoverClick = { source -> albumCoverSource = source; showCoverPicker = true },
                        onToggleFavorite = { viewModel.toggleFavorite(context, it.uri.toString()) },
                        onToggleSelect = { id ->
                            batchSelection = if (batchSelection.contains(id)) batchSelection - id else batchSelection + id
                            showBatchActions = batchSelection.isNotEmpty()
                        }
                    )
                    PhotosTab.Albums -> AlbumsScreen(
                        groupedPhotos = albumGroups,
                        albumCovers = albumCovers,
                        customAlbums = customAlbums,
                        onAlbumSelected = { activeAlbum = it },
                        onCreateAlbum = { showAlbumDialog = true },
                        onChangeCover = { source -> albumCoverSource = source; showCoverPicker = true }
                    )
                    PhotosTab.Favorites -> FavoritesScreen(
                        photos = photos.filter { favorites.contains(it.uri.toString()) },
                        selectedIds = batchSelection,
                        dateFormatter = dateFormatter,
                        onPhotoClick = { selectedPhoto = it },
                        onToggleFavorite = { viewModel.toggleFavorite(context, it.uri.toString()) },
                        onToggleSelect = { id ->
                            batchSelection = if (batchSelection.contains(id)) batchSelection - id else batchSelection + id
                            showBatchActions = batchSelection.isNotEmpty()
                        }
                    )
                }
            }

            selectedPhoto?.let { photo ->
                PhotoViewer(
                    photo = photo,
                    isFavorite = favorites.contains(photo.uri.toString()),
                    onClose = { selectedPhoto = null },
                    onToggleFavorite = { viewModel.toggleFavorite(context, photo.uri.toString()) },
                    onShare = { shareUri(context, photo.uri, photo.mimeType) },
                    onAddToAlbum = {
                        selectedAddToAlbum = null
                        showPhotoAddToAlbumDialog = true
                    }
                )
            }

            if (showAlbumDialog) {
                AlertDialog(
                    onDismissRequest = { showAlbumDialog = false },
                    title = { Text("Create album") },
                    text = {
                        OutlinedTextField(
                            value = newAlbumName,
                            onValueChange = { newAlbumName = it },
                            placeholder = { Text("Album name") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.createAlbum(context, newAlbumName)
                            newAlbumName = ""
                            showAlbumDialog = false
                        }) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAlbumDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showPhotoAddToAlbumDialog && selectedPhoto != null) {
                AlertDialog(
                    onDismissRequest = { showPhotoAddToAlbumDialog = false },
                    title = { Text("Add to album") },
                    text = {
                        if (customAlbums.isEmpty()) {
                            Text("No custom albums available. Create one first.")
                        } else {
                            Column {
                                Text("Select an album to add this item:")
                                Spacer(modifier = Modifier.height(8.dp))
                                customAlbums.keys.sorted().forEach { albumName ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedAddToAlbum = albumName }
                                            .padding(4.dp)
                                    ) {
                                        RadioButton(
                                            selected = selectedAddToAlbum == albumName,
                                            onClick = { selectedAddToAlbum = albumName }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(albumName, color = Color.White)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedPhoto?.let { photo ->
                                selectedAddToAlbum?.let { albumName ->
                                    viewModel.addPhotoToAlbum(context, albumName, photo.uri.toString())
                                }
                            }
                            selectedAddToAlbum = null
                            showPhotoAddToAlbumDialog = false
                        }, enabled = selectedAddToAlbum != null) { Text("Add") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            selectedAddToAlbum = null
                            showPhotoAddToAlbumDialog = false
                        }) { Text("Cancel") }
                    }
                )
            }

            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    title = { Text("App settings") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Theme", fontSize = 16.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            ThemeChoice.values().forEach { choice ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setThemeChoice(context, choice) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = themeChoice == choice,
                                        onClick = { viewModel.setThemeChoice(context, choice) },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.secondary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(choice.name, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Launcher icon", fontSize = 16.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            IconChoice.values().forEach { choice ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setIconChoice(context, choice) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = iconChoice == choice,
                                        onClick = { viewModel.setIconChoice(context, choice) },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.secondary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(choice.name, color = Color.White)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettingsDialog = false }) { Text("Done") }
                    }
                )
            }

            if (showRenameAlbumDialog && editingAlbum != null) {
                AlertDialog(
                    onDismissRequest = { showRenameAlbumDialog = false },
                    title = { Text("Rename album") },
                    text = {
                        OutlinedTextField(
                            value = renameAlbumName,
                            onValueChange = { renameAlbumName = it },
                            placeholder = { Text("Album name") }
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.renameAlbum(context, editingAlbum!!, renameAlbumName)
                            editingAlbum = null
                            renameAlbumName = ""
                            showRenameAlbumDialog = false
                        }) { Text("Rename") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showRenameAlbumDialog = false
                            editingAlbum = null
                        }) { Text("Cancel") }
                    }
                )
            }

            if (showAddToAlbumDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAddToAlbumDialog = false
                        selectedAddToAlbum = null
                    },
                    title = { Text("Add selected to album") },
                    text = {
                        if (customAlbums.isEmpty()) {
                            Text("Create a custom album first.")
                        } else {
                            Column {
                                Text("Choose an album to add selected items to:")
                                Spacer(modifier = Modifier.height(8.dp))
                                customAlbums.keys.sorted().forEach { albumName ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedAddToAlbum = albumName }
                                            .padding(4.dp)
                                    ) {
                                        RadioButton(
                                            selected = selectedAddToAlbum == albumName,
                                            onClick = { selectedAddToAlbum = albumName }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(albumName, color = Color.White)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedAddToAlbum?.let { albumName ->
                                batchSelection.forEach { photoUri ->
                                    viewModel.addPhotoToAlbum(context, albumName, photoUri)
                                }
                            }
                            showAddToAlbumDialog = false
                            selectedAddToAlbum = null
                        }, enabled = customAlbums.isNotEmpty() && selectedAddToAlbum != null) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAddToAlbumDialog = false
                            selectedAddToAlbum = null
                        }) { Text("Cancel") }
                    }
                )
            }

            val selectedItems = photos.filter { batchSelection.contains(it.uri.toString()) }
            val allSelectedFavorites = selectedItems.isNotEmpty() && selectedItems.all { favorites.contains(it.uri.toString()) }

            if (showBatchActions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(Color(0xFF111827), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${batchSelection.size} selected", color = Color.White, modifier = Modifier.weight(1f))
                            IconButton(onClick = { batchSelection = emptySet(); showBatchActions = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                selectedAddToAlbum = null
                                showAddToAlbumDialog = true
                            }) {
                                Icon(Icons.Default.Folder, contentDescription = "Add to album")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add to album")
                            }
                            Button(onClick = {
                                shareUris(context, selectedItems.map { it.uri }, if (selectedItems.all { it.isVideo }) "video/*" else if (selectedItems.all { !it.isVideo }) "image/*" else "*/*")
                            }, enabled = selectedItems.isNotEmpty()) {
                                Icon(Icons.Default.Share, contentDescription = "Share selected")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share")
                            }
                            Button(onClick = {
                                selectedItems.forEach { photo ->
                                    if (allSelectedFavorites) {
                                        viewModel.toggleFavorite(context, photo.uri.toString())
                                    } else if (!favorites.contains(photo.uri.toString())) {
                                        viewModel.toggleFavorite(context, photo.uri.toString())
                                    }
                                }
                            }, enabled = selectedItems.isNotEmpty()) {
                                Icon(Icons.Default.Favorite, contentDescription = "Toggle favorites")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (allSelectedFavorites) "Unfavorite" else "Favorite")
                            }
                            Button(onClick = {
                                batchSelection = emptySet()
                                showBatchActions = false
                            }) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            if (showCoverPicker && albumCoverSource != null) {
                val pickerItems = customAlbums[albumCoverSource!!]?.let { members ->
                    photos.filter { members.contains(it.uri.toString()) }
                } ?: sourceGroups[albumCoverSource!!].orEmpty().ifEmpty { albumGroups[albumCoverSource!!].orEmpty() }
                AlbumCoverPicker(
                    source = albumCoverSource!!,
                    items = if (pickerItems.isNotEmpty()) pickerItems else photos,
                    onSelect = { uriString ->
                        viewModel.setAlbumCover(context, albumCoverSource!!, uriString)
                        showCoverPicker = false
                    },
                    onDismiss = { showCoverPicker = false }
                )
            }

            activeAlbum?.let { album ->
                val albumPhotos = if (customAlbums.containsKey(album)) {
                    photos.filter { customAlbums[album]?.contains(it.uri.toString()) == true }
                } else {
                    albumGroups[album].orEmpty()
                }
                AlbumDetailOverlay(
                    source = album,
                    photos = albumPhotos,
                    favoriteUris = favorites,
                    isCustomAlbum = customAlbums.containsKey(album),
                    onClose = { activeAlbum = null },
                    onPhotoClick = { selectedPhoto = it },
                    onToggleFavorite = { viewModel.toggleFavorite(context, it.uri.toString()) },
                    onRenameAlbum = {
                        editingAlbum = album
                        renameAlbumName = album
                        showRenameAlbumDialog = true
                    },
                    onDeleteAlbum = { viewModel.deleteAlbum(context, album); activeAlbum = null },
                    onChangeCover = {
                        albumCoverSource = album
                        showCoverPicker = true
                    },
                    onRemoveFromAlbum = { photo ->
                        if (customAlbums.containsKey(album)) {
                            viewModel.removePhotoFromAlbum(context, album, photo.uri.toString())
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AllScreen(
    groupedPhotos: Map<String, List<PhotoItem>>,
    albumCovers: Map<String, String>,
    favorites: Set<String>,
    selectedIds: Set<String>,
    dateFormatter: SimpleDateFormat,
    onPhotoClick: (PhotoItem) -> Unit,
    onCoverClick: (String) -> Unit,
    onToggleFavorite: (PhotoItem) -> Unit,
    onToggleSelect: (String) -> Unit
) {
    if (groupedPhotos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No media found.", color = Color(0xFFCBD5E1), fontSize = 16.sp)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        groupedPhotos.forEach { (source, items) ->
            item {
                AlbumHeader(
                    source = source,
                    count = items.size,
                    coverUri = albumCovers[source],
                    onChangeCover = { onCoverClick(source) }
                )
            }
            item {
                val gridState = rememberLazyGridState()
                val visibleDate by remember {
                    derivedStateOf {
                        items.getOrNull(gridState.firstVisibleItemIndex)?.dateTaken?.takeIf { it > 0 }
                            ?.let { dateFormatter.format(Date(it)) } ?: ""
                    }
                }
                Box(modifier = Modifier.heightIn(min = 120.dp, max = 520.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items) { photo ->
                            PhotoCard(
                                photo = photo,
                                isFavorite = favorites.contains(photo.uri.toString()),
                                selected = selectedIds.contains(photo.uri.toString()),
                                onToggleFavorite = { onToggleFavorite(photo) },
                                onClick = { onPhotoClick(photo) },
                                onLongPress = { onToggleSelect(photo.uri.toString()) }
                            )
                        }
                    }
                    if (visibleDate.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(visibleDate, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsScreen(
    groupedPhotos: Map<String, List<PhotoItem>>,
    albumCovers: Map<String, String>,
    customAlbums: Map<String, Set<String>>,
    onAlbumSelected: (String) -> Unit,
    onCreateAlbum: () -> Unit,
    onChangeCover: (String) -> Unit
) {
    if (groupedPhotos.isEmpty() && customAlbums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums available.", color = Color(0xFFCBD5E1), fontSize = 16.sp)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Button(onClick = onCreateAlbum, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = "Create album")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create album")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (customAlbums.isNotEmpty()) {
            item {
                Text("Custom albums", color = Color(0xFF93C5FD), fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(customAlbums.entries.toList()) { (albumName, items) ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0xFF111827),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onAlbumSelected(albumName) }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(albumName.firstOrNull()?.toString().orEmpty(), color = Color.White, fontSize = 24.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(albumName, color = Color(0xFF93C5FD), fontSize = 18.sp)
                            Text("${items.size} items", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                        }
                        TextButton(onClick = { onChangeCover(albumName) }) {
                            Text("Cover")
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        groupedPhotos.forEach { (source, items) ->
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = Color(0xFF111827),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onAlbumSelected(source) }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (albumCovers[source].isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(source.first().toString(), color = Color.White, fontSize = 24.sp)
                            }
                        } else {
                            AsyncImage(
                                model = albumCovers[source],
                                contentDescription = "$source cover",
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.Black, RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source, color = Color(0xFF93C5FD), fontSize = 18.sp)
                            Text("${items.size} items", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                        }
                        TextButton(onClick = { onChangeCover(source) }) {
                            Text("Change cover")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    photos: List<PhotoItem>,
    selectedIds: Set<String>,
    dateFormatter: SimpleDateFormat,
    onPhotoClick: (PhotoItem) -> Unit,
    onToggleFavorite: (PhotoItem) -> Unit,
    onToggleSelect: (String) -> Unit
) {
    if (photos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No favorites yet.", color = Color(0xFFCBD5E1), fontSize = 16.sp)
        }
        return
    }

    val gridState = rememberLazyGridState()
    val visibleDate by remember {
        derivedStateOf {
            photos.getOrNull(gridState.firstVisibleItemIndex)?.dateTaken?.takeIf { it > 0 }
                ?.let { dateFormatter.format(Date(it)) } ?: ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(photos) { photo ->
                PhotoCard(
                    photo = photo,
                    isFavorite = true,
                    selected = selectedIds.contains(photo.uri.toString()),
                    onToggleFavorite = { onToggleFavorite(photo) },
                    onClick = { onPhotoClick(photo) },
                    onLongPress = { onToggleSelect(photo.uri.toString()) }
                )
            }
        }
        if (visibleDate.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(visibleDate, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AlbumDetailOverlay(
    source: String,
    photos: List<PhotoItem>,
    favoriteUris: Set<String>,
    isCustomAlbum: Boolean,
    onClose: () -> Unit,
    onPhotoClick: (PhotoItem) -> Unit,
    onToggleFavorite: (PhotoItem) -> Unit,
    onRenameAlbum: () -> Unit,
    onDeleteAlbum: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveFromAlbum: (PhotoItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(source, fontSize = 20.sp, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
                    if (isCustomAlbum) {
                        IconButton(onClick = onChangeCover) {
                            Icon(Icons.Default.Image, contentDescription = "Change cover")
                        }
                        IconButton(onClick = onRenameAlbum) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename album")
                        }
                        IconButton(onClick = onDeleteAlbum) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete album")
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(photos) { photo ->
                        PhotoCard(
                            photo = photo,
                            isFavorite = favoriteUris.contains(photo.uri.toString()),
                            selected = false,
                            onToggleFavorite = { onToggleFavorite(photo) },
                            onClick = { onPhotoClick(photo) },
                            onLongPress = {
                                if (isCustomAlbum) {
                                    onRemoveFromAlbum(photo)
                                }
                            }
                        )
                    }
                }
                if (isCustomAlbum) {
                    Text(
                        "Long press a photo to remove it from this album.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumHeader(source: String, count: Int, coverUri: String?, onChangeCover: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (!coverUri.isNullOrBlank()) {
            AsyncImage(
                model = coverUri,
                contentDescription = "$source cover",
                modifier = Modifier
                    .size(64.dp)
                    .padding(end = 8.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(source.first().toString(), color = Color.White, fontSize = 20.sp)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = source, color = Color(0xFF93C5FD), fontSize = 16.sp)
            Text(text = "$count items", color = Color(0xFFCBD5E1), fontSize = 12.sp)
        }
        TextButton(onClick = onChangeCover) {
            Text("Change cover")
        }
    }
}

@Composable
fun AlbumCoverPicker(source: String, items: List<PhotoItem>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose cover for $source") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.height(340.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { photo ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(100.dp)
                            .clickable { onSelect(photo.uri.toString()) }
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PhotoCard(photo: PhotoItem, isFavorite: Boolean, selected: Boolean = false, onToggleFavorite: () -> Unit, onClick: () -> Unit, onLongPress: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        backgroundColor = if (selected) Color(0xFF1E40AF) else Color(0xFF111827),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
    ) {
        Box {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
            if (photo.isVideo) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0x66093C5A))
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFF472B6) else Color.White
                )
            }
        }
        Column {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = photo.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun PhotoViewer(
    photo: PhotoItem,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onAddToAlbum: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFF472B6) else Color.White
                        )
                    }
                    IconButton(onClick = onAddToAlbum) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Add to album")
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
                if (photo.isVideo) {
                    VideoPlayer(uri = photo.uri)
                } else {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    val displayDate = if (photo.dateTaken > 0L) photo.dateTaken else photo.dateAdded
                    Text(text = photo.name, color = Color.White, fontSize = 18.sp)
                    Text(text = "Source: ${photo.source}", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Text(text = "Folder: ${photo.bucket}", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Text(text = if (photo.isVideo) "Video" else "Photo", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Text(text = "Taken: ${SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(displayDate))}", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Text(text = "Size: ${formatBytes(photo.size)}", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    if (photo.isVideo) {
                        Text(text = "Duration: ${formatDuration(photo.durationMillis)}", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(uri) {
        onDispose {
            player.release()
        }
    }

    AndroidView(factory = { ctx ->
        PlayerView(ctx).apply {
            this.player = player
            useController = true
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }, modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    )
}

private fun shareUri(context: android.content.Context, uri: Uri, mimeType: String) {
    try {
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            type = mimeType
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share media"))
    } catch (e: Exception) {
        Log.e("PhotosApp", "Share failed", e)
    }
}

private fun shareUris(context: android.content.Context, uris: List<Uri>, mimeType: String) {
    try {
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            type = mimeType
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share media"))
    } catch (e: Exception) {
        Log.e("PhotosApp", "Share failed", e)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDuration(millis: Long?): String {
    if (millis == null || millis <= 0) return "Unknown"
    val seconds = millis / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
