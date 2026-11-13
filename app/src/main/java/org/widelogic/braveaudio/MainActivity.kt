package org.widelogic.braveaudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.widelogic.braveaudio.data.PlaylistEntity
import org.widelogic.braveaudio.data.TrackEntity
import org.widelogic.braveaudio.data.EqPresetStore
import org.widelogic.braveaudio.playback.AudioEffects
import org.widelogic.braveaudio.playback.AudioVisualizer
import org.widelogic.braveaudio.playback.PlaybackHolder
import org.widelogic.braveaudio.playback.PlaybackService

private val PositiveGold = Color(0xFFFCBF3E)
private val Orange = Color(0xFFFF8A38)
private val Pink = Color(0xFFFF5CB8)
private val WhiteIce = Color(0xFFF4FBFF)
private val DeepNavy = Color(0xFF081722)
private val DarkGray = Color(0xFF1B1E24)
private val SurfaceGray = Color(0xFF262A32)
private val SoftGray = Color(0xFFECEDF0)
private val Cyan = Color(0xFF16E4E8)
private val Aqua = Color(0xFF4FAEEA)

private data class ThemePalette(
    val key: String,
    val label: String,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val accent2: Color,
    val positive: Color,
    val navBackground: Color,
)

private val ThemePalettes = listOf(
    ThemePalette("ocean_dark", "Ocean dark", true, Color(0xFF07182F), Color(0xFF142F50), Color(0xFF1B3B5E), Color.White, Color(0xFF9EB9D0), Cyan, WhiteIce, PositiveGold, Color(0xFF0C2442)),
    ThemePalette("light_orange", "Light gray / orange", false, Color(0xFFF2F3F5), Color.White, Color(0xFFE6E8EC), Color(0xFF223041), Color(0xFF617284), Orange, PositiveGold, PositiveGold, Color.White),
    ThemePalette("dark_yellow", "Dark gray / yellow", true, Color(0xFF181A1F), Color(0xFF252932), Color(0xFF2E333E), Color.White, Color(0xFFB7BDC9), PositiveGold, Color(0xFFFFE6A6), PositiveGold, Color(0xFF20242C)),
    ThemePalette("dark_orange", "Dark gray / orange", true, Color(0xFF191A1E), Color(0xFF272A31), Color(0xFF30343C), Color.White, Color(0xFFB7BDC9), Orange, Color(0xFFFFC16E), PositiveGold, Color(0xFF20242C)),
    ThemePalette("sunset_dark", "Pink / orange / yellow", true, Color(0xFF1A1A1F), Color(0xFF292933), Color(0xFF323240), Color.White, Color(0xFFC6C6D1), Pink, Orange, PositiveGold, Color(0xFF23232D)),
)

private fun paletteFor(key: String): ThemePalette = ThemePalettes.firstOrNull { it.key == key } ?: ThemePalettes.first()

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackHolder.init(this)
        startService(Intent(this, PlaybackService::class.java))
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let(vm::addSingle)
        setContent {
            val themeKey by vm.themeKey.collectAsStateWithLifecycle()
            val palette = remember(themeKey) { paletteFor(themeKey) }
            MaterialTheme(
                colorScheme = if (palette.isDark) {
                    darkColorScheme(
                        primary = palette.accent,
                        secondary = palette.accent2,
                        background = palette.background,
                        surface = palette.surface,
                        onSurface = palette.text,
                        onBackground = palette.text
                    )
                } else {
                    lightColorScheme(
                        primary = palette.accent,
                        secondary = palette.accent2,
                        background = palette.background,
                        surface = palette.surface,
                        onSurface = palette.text,
                        onBackground = palette.text
                    )
                }
            ) {
                Surface(Modifier.fillMaxSize(), color = palette.background) {
                    BraveAudioScreen(vm, palette)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let(vm::addSingle)
    }
}

private enum class Section { HOME, LIBRARY, EQUALIZER, SETTINGS }
private enum class Category { SONGS, ALBUMS, ARTISTS, PLAYLISTS, FAVORITES }
private enum class SpectrumMode { BARS, FINE_BARS, CURVE }

private data class PlaybackUi(
    val title: String = "",
    val artist: String = "",
    val uri: String = "",
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val playbackState: Int = Player.STATE_IDLE,
)

@Composable
private fun BraveAudioScreen(vm: MainViewModel, palette: ThemePalette) {
    var section by remember { mutableStateOf(Section.HOME) }
    var category by remember { mutableStateOf(Category.SONGS) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportingPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var search by remember { mutableStateOf("") }
    var newPlaylist by remember { mutableStateOf(false) }
    var renamePlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var editedTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var menuTrack by remember { mutableStateOf<TrackEntity?>(null) }

    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val folder by vm.folderUri.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val selectedTracks by vm.selectedPlaylistTracks.collectAsStateWithLifecycle()
    val exchangeMessage by vm.exchangeMessage.collectAsStateWithLifecycle()
    val state = rememberPlayerState()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::selectFolder) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::addSingle) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importPlaylist) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val target = exportingPlaylist
        if (uri != null && target != null) vm.exportPlaylist(target.id, uri)
        exportingPlaylist = null
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.clip(CircleShape).background(palette.surface)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = palette.text)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Choose music folder") }, onClick = { showMenu = false; folderPicker.launch(null) }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
                            DropdownMenuItem(text = { Text("Open audio file") }, onClick = { showMenu = false; filePicker.launch(arrayOf("audio/*")) }, leadingIcon = { Icon(Icons.Default.AudioFile, null) })
                            DropdownMenuItem(text = { Text("Rescan folder") }, onClick = { showMenu = false; vm.scanFolder() }, leadingIcon = { Icon(Icons.Default.Refresh, null) })
                            DropdownMenuItem(text = { Text("Color themes") }, onClick = { showMenu = false; showThemeDialog = true }, leadingIcon = { Icon(Icons.Default.Palette, null) })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { showMenu = false; section = Section.SETTINGS }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                            DropdownMenuItem(text = { Text("Import M3U playlist") }, onClick = { showMenu = false; importPicker.launch(arrayOf("audio/x-mpegurl", "audio/x-mpegurl", "application/x-mpegurl", "text/plain", "*/*")) }, leadingIcon = { Icon(Icons.Default.FileOpen, null) })
                            DropdownMenuItem(text = { Text("Export M3U playlist") }, onClick = { showMenu = false; showExportDialog = true }, leadingIcon = { Icon(Icons.Default.SaveAlt, null) })
                            DropdownMenuItem(text = { Text("About") }, onClick = { showMenu = false; showAbout = true }, leadingIcon = { Icon(Icons.Default.Info, null) })
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (section) {
                                Section.HOME -> "Brave Audio"
                                Section.LIBRARY -> "Library"
                                Section.EQUALIZER -> "Equalizer"
                                Section.SETTINGS -> "Settings"
                            },
                            color = palette.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp
                        )
                        when {
                            scanning -> Text("Scanning music…", color = palette.accent, fontSize = 11.sp)
                            folder != null -> Text("Source selected", color = palette.secondaryText, fontSize = 11.sp)
                        }
                    }
                    IconButton(
                        onClick = { section = Section.LIBRARY; showSearch = !showSearch },
                        modifier = Modifier.clip(CircleShape).background(palette.surface)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = palette.text)
                    }
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it; section = Section.LIBRARY; selectedPlaylist = null },
                        placeholder = { Text("Search song, album or artist") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        trailingIcon = { IconButton(onClick = { search = ""; showSearch = false }) { Icon(Icons.Default.Close, contentDescription = "Close") } }
                    )
                }
            }
        },
        bottomBar = {
            Column {
                if (section != Section.HOME && state.title.isNotBlank()) {
                    MiniPlayer(state = state, palette = palette, onOpen = { section = Section.HOME })
                }
                NavigationBar(containerColor = palette.navBackground, tonalElevation = 0.dp) {
                    listOf(Section.HOME, Section.LIBRARY, Section.EQUALIZER, Section.SETTINGS).forEach { item ->
                        NavigationBarItem(
                            selected = section == item,
                            onClick = { section = item },
                            icon = {
                                Icon(
                                    imageVector = when (item) {
                                        Section.HOME -> Icons.Default.Home
                                        Section.LIBRARY -> Icons.Default.LibraryMusic
                                        Section.EQUALIZER -> Icons.Default.Tune
                                        Section.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = item.name,
                                    tint = if (section == item) palette.accent else palette.secondaryText
                                )
                            },
                            label = { Text(when (item) { Section.HOME -> "Player"; Section.LIBRARY -> "Library"; Section.EQUALIZER -> "EQ"; Section.SETTINGS -> "Settings" }) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = palette.surfaceRaised,
                                selectedTextColor = palette.accent,
                                unselectedTextColor = palette.secondaryText
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (section) {
                Section.HOME -> HomeScreen(state = state, vm = vm, favorites = favorites, palette = palette, onOpenLibrary = { section = Section.LIBRARY })
                Section.LIBRARY -> LibraryScreen(
                    tracks = tracks,
                    playlists = playlists,
                    favorites = favorites,
                    category = category,
                    setCategory = { category = it; selectedPlaylist = null },
                    selectedPlaylist = selectedPlaylist,
                    selectedTracks = selectedTracks,
                    search = search,
                    palette = palette,
                    onSong = { t, q -> vm.play(t, q) },
                    onFavorite = { vm.toggleFavorite(it) },
                    onMenu = { menuTrack = it },
                    onCreatePlaylist = { newPlaylist = true },
                    onPlaylist = { selectedPlaylist = it; vm.loadPlaylist(it.id) },
                    onPlaylistPlay = { vm.playPlaylist(it.id) },
                    onPlaylistRename = { renamePlaylist = it },
                    onPlaylistDelete = { vm.deletePlaylist(it) },
                    onPlaylistRemove = { p, t -> vm.removeFromPlaylist(p.id, t.uri) },
                    onBack = { selectedPlaylist = null },
                    onFolder = { folderPicker.launch(null) }
                )
                Section.EQUALIZER -> EqualizerScreen(palette)
                Section.SETTINGS -> SettingsScreen(
                    palette = palette,
                    onSelectTheme = { showThemeDialog = true },
                    onImport = { importPicker.launch(arrayOf("*/*")) },
                    onExport = { showExportDialog = true },
                    onFolder = { folderPicker.launch(null) },
                    onRescan = { vm.scanFolder() },
                    onAbout = { showAbout = true },
                )
            }
        }
    }

    if (exchangeMessage.isNotBlank()) {
        AlertDialog(
            onDismissRequest = vm::clearExchangeMessage,
            title = { Text("Playlist import / export") },
            text = { Text(exchangeMessage) },
            confirmButton = { TextButton(onClick = vm::clearExchangeMessage) { Text("OK") } }
        )
    }
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export playlist to M3U") },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    if (playlists.isEmpty()) Text("Create a playlist first.")
                    playlists.forEach { playlist ->
                        TextButton(onClick = {
                            exportingPlaylist = playlist
                            showExportDialog = false
                            val safeName = playlist.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "playlist" }
                            exportPicker.launch("$safeName.m3u")
                        }) { Text(playlist.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExportDialog = false }) { Text("Close") } }
        )
    }
    if (showThemeDialog) ThemeSelectionDialog(currentKey = palette.key, onSelect = { vm.setThemeKey(it) }, onDismiss = { showThemeDialog = false })
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
    if (newPlaylist) TextEntryDialog("New playlist", "", onDismiss = { newPlaylist = false }) { vm.createPlaylist(it); newPlaylist = false }
    renamePlaylist?.let { current ->
        TextEntryDialog("Rename playlist", current.name, onDismiss = { renamePlaylist = null }) {
            vm.renamePlaylist(current, it)
            renamePlaylist = null
        }
    }
    editedTrack?.let { track ->
        TrackEditDialog(track = track, onDismiss = { editedTrack = null }) { title, artist, album ->
            vm.updateTrack(track, title, artist, album)
            editedTrack = null
        }
    }
    menuTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { menuTrack = null },
            title = { Text(track.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(track.artist.ifBlank { "Unknown artist" })
                    Text(track.album.ifBlank { "Unknown album" }, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f))
                    HorizontalDivider()
                    Text("Use the actions below to edit the library item or remove it from the database.")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { editedTrack = track; menuTrack = null }) { Text("Edit") }
                    TextButton(onClick = { vm.removeTrack(track); menuTrack = null }) { Text("Remove") }
                }
            },
            dismissButton = { TextButton(onClick = { menuTrack = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun rememberPlayerState(): PlaybackUi {
    val player = remember { PlaybackHolder.player }
    var state by remember { mutableStateOf(PlaybackUi()) }
    fun update() {
        state = PlaybackUi(
            title = player.mediaMetadata.title?.toString().orEmpty(),
            artist = player.mediaMetadata.artist?.toString().orEmpty(),
            uri = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            position = player.currentPosition.coerceAtLeast(0L),
            duration = player.duration.takeIf { it > 0 } ?: 0L,
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode,
            playbackState = player.playbackState
        )
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = update()
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) = update()
        }
        player.addListener(listener)
        update()
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            update()
            delay(400)
        }
    }
    return state
}

@Composable
private fun HomeScreen(state: PlaybackUi, vm: MainViewModel, favorites: Set<String>, palette: ThemePalette, onOpenLibrary: () -> Unit) {
    val player = remember { PlaybackHolder.player }
    val ctx = LocalContext.current
    val audioManager = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15 }
    var volume by remember { mutableFloatStateOf((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7).toFloat() / maxVolume) }
    val isFav = state.uri.isNotBlank() && state.uri in favorites
    val playbackError by PlaybackHolder.errorMessage.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                drawCircle(color = palette.accent.copy(alpha = 0.10f), radius = w * .50f)
                drawCircle(brush = Brush.sweepGradient(listOf(palette.accent2, palette.accent, palette.positive, palette.accent2)), radius = w * .45f, style = Stroke(width = 12.dp.toPx()))
                drawCircle(color = palette.surfaceRaised, radius = w * .39f)
            }
            val albumArt = rememberAlbumArt(state.uri)
            if (albumArt != null) {
                Image(bitmap = albumArt.asImageBitmap(), contentDescription = "Album art", contentScale = ContentScale.Crop, modifier = Modifier.size(170.dp).clip(CircleShape))
            }
            IconButton(
                onClick = {
                    if (state.title.isBlank()) onOpenLibrary() else PlaybackHolder.togglePlaybackSafely(player)
                },
                modifier = Modifier.size(118.dp)
            ) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play / pause", tint = palette.accent, modifier = Modifier.size(96.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("NOW PLAYING", color = palette.secondaryText, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
        Text(state.title.ifBlank { "Select music to play" }, color = palette.text, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(state.artist.ifBlank { "Brave Audio" }, color = palette.secondaryText, fontSize = 15.sp)
        Spacer(Modifier.height(15.dp))
        HomeWaveform(accent = palette.accent, highlight = palette.positive)
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier.fillMaxWidth().shadow(9.dp, RoundedCornerShape(24.dp)).background(palette.surface, RoundedCornerShape(24.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0L) }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = palette.text, modifier = Modifier.size(32.dp))
                }
                FilledIconButton(
                    onClick = { PlaybackHolder.togglePlaybackSafely(player) },
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.accent)
                ) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play", tint = palette.background, modifier = Modifier.size(39.dp))
                }
                IconButton(onClick = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = palette.text, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Slider(
                value = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { if (state.duration > 0) player.seekTo((it * state.duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.position), color = palette.secondaryText, fontSize = 12.sp)
                Text(formatTime(state.duration), color = palette.secondaryText, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeDown, contentDescription = "Volume", tint = palette.secondaryText)
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).toInt(), 0)
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                )
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = palette.secondaryText)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) palette.accent else palette.secondaryText)
                }
                IconButton(onClick = { if (state.uri.isNotBlank()) vm.toggleFavorite(state.uri) }) {
                    Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (isFav) palette.accent else palette.secondaryText)
                }
                IconButton(onClick = {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }) {
                    Icon(if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = "Repeat", tint = if (state.repeat != Player.REPEAT_MODE_OFF) palette.accent else palette.secondaryText)
                }
            }
            if (playbackError.isNotBlank()) {
                Text(playbackError, color = palette.positive, fontSize = 12.sp)
            }
            if (state.playbackState == Player.STATE_ENDED) {
                Spacer(Modifier.height(4.dp))
                Text("Track ended — press Play to restart or choose another track.", color = palette.secondaryText, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeWaveform(accent: Color, highlight: Color) {
    Canvas(Modifier.width(280.dp).height(34.dp)) {
        val count = 48
        for (i in 0 until count) {
            val h = (7 + (i * 17 % 23)).dp.toPx().coerceAtMost(size.height)
            val x = i * (size.width / count)
            val color = if (i % 7 == 0 || i % 7 == 1) highlight else accent
            drawLine(color.copy(alpha = .35f + .65f * ((i % 5).toFloat() / 5f)), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun MiniPlayer(state: PlaybackUi, palette: ThemePalette, onOpen: () -> Unit) {
    val p = remember { PlaybackHolder.player }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(palette.surface).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = palette.accent, modifier = Modifier.size(35.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.text, fontWeight = FontWeight.Bold)
            Text(state.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.secondaryText, fontSize = 12.sp)
        }
        Text(formatTime(state.position), fontSize = 11.sp, color = palette.secondaryText)
        IconButton(onClick = { PlaybackHolder.togglePlaybackSafely(p) }) {
            Icon(if (state.isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, contentDescription = "Play", tint = palette.accent, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<TrackEntity>,
    playlists: List<PlaylistEntity>,
    favorites: Set<String>,
    category: Category,
    setCategory: (Category) -> Unit,
    selectedPlaylist: PlaylistEntity?,
    selectedTracks: List<TrackEntity>,
    search: String,
    palette: ThemePalette,
    onSong: (TrackEntity, List<TrackEntity>) -> Unit,
    onFavorite: (String) -> Unit,
    onMenu: (TrackEntity) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylist: (PlaylistEntity) -> Unit,
    onPlaylistPlay: (PlaylistEntity) -> Unit,
    onPlaylistRename: (PlaylistEntity) -> Unit,
    onPlaylistDelete: (PlaylistEntity) -> Unit,
    onPlaylistRemove: (PlaylistEntity, TrackEntity) -> Unit,
    onBack: () -> Unit,
    onFolder: () -> Unit,
) {
    val visibleTracks = tracks.filter { t -> search.isBlank() || listOf(t.title, t.artist, t.album).any { it.contains(search, true) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Category.entries.forEach { cat ->
                val selected = category == cat
                Box(Modifier.clip(RoundedCornerShape(15.dp)).background(if (selected) palette.accent else palette.surface).clickable { setCategory(cat) }.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) palette.background else palette.text)
                }
            }
        }
        if (selectedPlaylist != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = palette.text) }
                Text(selectedPlaylist.name, color = palette.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onPlaylistPlay(selectedPlaylist) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play playlist", tint = palette.accent) }
            }
            LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(selectedTracks, key = { it.uri }) { t ->
                    TrackCard(t, palette = palette, favorite = t.uri in favorites, onPlay = { onSong(t, selectedTracks) }, onFavorite = { onFavorite(t.uri) }, onMenu = { onPlaylistRemove(selectedPlaylist, t) })
                }
            }
            return@Column
        }
        when (category) {
            Category.SONGS, Category.FAVORITES -> {
                val list = if (category == Category.FAVORITES) visibleTracks.filter { it.uri in favorites } else visibleTracks
                if (list.isEmpty()) EmptyLibrary(onFolder)
                else LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(list, key = { it.uri }) { t ->
                        TrackCard(t, palette = palette, favorite = t.uri in favorites, onPlay = { onSong(t, list) }, onFavorite = { onFavorite(t.uri) }, onMenu = { onMenu(t) })
                    }
                }
            }
            Category.ALBUMS, Category.ARTISTS -> {
                val groups = visibleTracks.groupBy { if (category == Category.ALBUMS) it.album.ifBlank { "Unknown album" } else it.artist.ifBlank { "Unknown artist" } }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(groups.entries.sortedBy { it.key }) { entry ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface).clickable { entry.value.firstOrNull()?.let { onSong(it, entry.value) } }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (category == Category.ALBUMS) Icons.Default.Album else Icons.Default.Person, contentDescription = null, tint = palette.accent, modifier = Modifier.size(36.dp))
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(entry.key, color = palette.text, fontWeight = FontWeight.Bold)
                                Text("${entry.value.size} songs", color = palette.secondaryText, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play", tint = palette.accent)
                        }
                    }
                }
            }
            Category.PLAYLISTS -> {
                Button(onClick = onCreatePlaylist, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("New playlist")
                }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(palette.surface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = palette.accent, modifier = Modifier.padding(7.dp))
                            Text(playlist.name, modifier = Modifier.weight(1f).clickable { onPlaylist(playlist) }.padding(8.dp), color = palette.text, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onPlaylistPlay(playlist) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = palette.accent) }
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = palette.text) }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(text = { Text("Open") }, onClick = { expanded = false; onPlaylist(playlist) })
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { expanded = false; onPlaylistRename(playlist) })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { expanded = false; onPlaylistDelete(playlist) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onFolder: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text("No music found", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onFolder) { Text("Choose music folder") }
    }
}

@Composable
private fun TrackCard(track: TrackEntity, palette: ThemePalette, favorite: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface).heightIn(min = 76.dp).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(palette.surfaceRaised, palette.accent2, palette.accent))), contentAlignment = Alignment.Center) {
            val art = rememberAlbumArt(track.uri)
            if (art != null) Image(art.asImageBitmap(), contentDescription = "Album art", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.MusicNote, contentDescription = null, tint = WhiteIce, modifier = Modifier.size(31.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(track.title, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifBlank { track.album.ifBlank { "Unknown artist" } }, color = palette.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(track.durationMs), color = palette.secondaryText, fontSize = 12.sp)
        IconButton(onClick = onPlay) { Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play ${track.title}", tint = palette.accent, modifier = Modifier.size(29.dp)) }
        Box {
            var expanded by remember { mutableStateOf(false) }
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = palette.secondaryText) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(if (favorite) "Remove favorite" else "Favorite") }, onClick = { expanded = false; onFavorite() })
                DropdownMenuItem(text = { Text("More options / Remove") }, onClick = { expanded = false; onMenu() })
            }
        }
    }
}

@Composable
private fun EqualizerScreen(palette: ThemePalette) {
    val ctx = LocalContext.current
    val player = remember { PlaybackHolder.player }
    val eq = remember { AudioEffects.equalizer(player) }
    val viz = remember { AudioVisualizer(player) }
    val levels by eq.levels.collectAsStateWithLifecycle()
    val store = remember(ctx) { EqPresetStore(ctx) }
    var customPresets by remember { mutableStateOf(store.list()) }
    var savePresetDialog by remember { mutableStateOf(false) }
    var deletePresetDialog by remember { mutableStateOf<String?>(null) }
    val fft by viz.fft.collectAsStateWithLifecycle()
    var preset by remember { mutableStateOf("Custom") }
    var spectrumMode by remember { mutableStateOf(SpectrumMode.FINE_BARS) }
    val presets = remember {
        mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f),
            "Pop" to listOf(.3f, .12f, -.1f, -.06f, .2f, .3f),
            "Rock" to listOf(.48f, .25f, -.1f, .16f, .32f, .45f),
            "EDM" to listOf(.65f, .35f, 0f, -.23f, .2f, .55f),
            "Jazz" to listOf(.2f, .12f, .0f, .1f, .18f, .16f)
        )
    }
    val visualizerPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) viz.start() }
    DisposableEffect(player) {
        eq.attach()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viz.start()
        onDispose { viz.stop() }
    }
    var session by remember { mutableIntStateOf(player.audioSessionId) }
    LaunchedEffect(player) {
        while (true) {
            if (session != player.audioSessionId) {
                session = player.audioSessionId
                eq.attach()
                viz.start()
            }
            delay(900)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Bass", "Mid", "Treble").forEachIndexed { index, label ->
                val band = listOf(0, 3, 5)[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Knob(levels[band], palette = palette) { eq.setBand(band, it); preset = "Custom" }
                    Text(label, color = palette.text, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(palette.surface).padding(15.dp)) {
            Text("6-BAND EQUALIZER", color = palette.text, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(250.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(29.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf("+12", "+6", "0", "-6", "-12").forEach { Text(it, color = palette.secondaryText, fontSize = 10.sp) }
                }
                eq.frequencies.forEachIndexed { index, label ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label.replace(" Hz", ""), color = palette.text, fontSize = 10.sp, maxLines = 1)
                        Spacer(Modifier.height(7.dp))
                        VerticalEqSlider(value = levels[index], positiveColor = palette.positive, negativeColor = palette.accent, modifier = Modifier.weight(1f).width(35.dp), onChange = { eq.setBand(index, it); preset = "Custom" })
                        Text("${(levels[index] * 12).toInt()}", color = if (levels[index] >= 0f) palette.positive else palette.accent, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (name, values) ->
                val selected = preset == name
                Box(Modifier.clip(RoundedCornerShape(13.dp)).background(if (selected) palette.accent else palette.surface).clickable {
                    values.forEachIndexed { i, v -> eq.setBand(i, v) }
                    preset = name
                }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(name, color = if (selected) palette.background else palette.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("MY EQ PRESETS", color = palette.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { savePresetDialog = true }) { Icon(Icons.Default.Save, null); Text("Save") }
        }
        if (customPresets.isEmpty()) {
            Text("Save current EQ settings as a named preset.", color = palette.secondaryText, fontSize = 12.sp)
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                customPresets.forEach { saved ->
                    AssistChip(
                        onClick = { saved.levels.forEachIndexed { i, level -> eq.setBand(i, level) }; preset = saved.name },
                        label = { Text(saved.name) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, "Delete ${saved.name}", modifier = Modifier.size(16.dp).clickable { deletePresetDialog = saved.name })
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SpectrumMode.BARS to "Bars",
                SpectrumMode.FINE_BARS to "Fine bars",
                SpectrumMode.CURVE to "Curve"
            ).forEach { (mode, label) ->
                val selected = spectrumMode == mode
                Box(Modifier.clip(RoundedCornerShape(13.dp)).background(if (selected) palette.positive else palette.surface).clickable { spectrumMode = mode }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(label, color = if (selected) palette.background else palette.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(19.dp))
        Text("LIVE SPECTRUM", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(7.dp))
        SpectrumCard(fft = fft, mode = spectrumMode, palette = palette)
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            TextButton(onClick = { visualizerPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Enable audio spectrum permission") }
        }
        Spacer(Modifier.height(22.dp))
    }
    if (savePresetDialog) {
        TextEntryDialog("Save EQ preset", "", onDismiss = { savePresetDialog = false }) { name ->
            store.save(name, levels)
            customPresets = store.list()
            preset = name.trim().take(48)
            savePresetDialog = false
        }
    }
    deletePresetDialog?.let { selected ->
        AlertDialog(
            onDismissRequest = { deletePresetDialog = null },
            title = { Text("Delete EQ preset?") },
            text = { Text(selected) },
            confirmButton = { TextButton(onClick = { store.delete(selected); customPresets = store.list(); deletePresetDialog = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletePresetDialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SpectrumCard(fft: FloatArray, mode: SpectrumMode, palette: ThemePalette) {
    Box(Modifier.fillMaxWidth().height(if (mode == SpectrumMode.CURVE) 170.dp else 120.dp).clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(12.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (mode == SpectrumMode.CURVE) {
                val leftPad = 28.dp.toPx()
                val bottomPad = 18.dp.toPx()
                val topPad = 8.dp.toPx()
                val chartWidth = size.width - leftPad
                val chartHeight = size.height - bottomPad - topPad
                val gridColor = palette.secondaryText.copy(alpha = 0.22f)
                repeat(6) { i ->
                    val y = topPad + chartHeight * i / 5f
                    drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                repeat(10) { i ->
                    val x = leftPad + chartWidth * i / 9f
                    drawLine(gridColor, Offset(x, topPad), Offset(x, topPad + chartHeight), strokeWidth = if (i == 2 || i == 5 || i == 8) 1.5.dp.toPx() else 1.dp.toPx())
                }
                val points = interpolateValues(fft, 72)
                val path = Path()
                val glowPath = Path()
                points.forEachIndexed { i, v ->
                    val x = leftPad + chartWidth * (i / (points.size - 1f))
                    val db = (-110f + (v.coerceIn(0f, 1.2f) / 1.2f) * 90f)
                    val normalized = ((db + 120f) / 100f).coerceIn(0f, 1f)
                    val y = topPad + chartHeight * (1f - normalized)
                    if (i == 0) {
                        path.moveTo(x, y)
                        glowPath.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        glowPath.lineTo(x, y)
                    }
                }
                drawPath(glowPath, color = palette.accent.copy(alpha = 0.25f), style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, color = palette.accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                // accent overlay line
                val path2 = Path()
                points.forEachIndexed { i, v ->
                    val x = leftPad + chartWidth * (i / (points.size - 1f))
                    val db = (-118f + (v.coerceIn(0f, 1.2f) / 1.2f) * 82f)
                    val normalized = ((db + 132f) / 108f).coerceIn(0f, 1f)
                    val y = topPad + chartHeight * (1f - normalized)
                    if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
                }
                drawPath(path2, color = palette.positive.copy(alpha = 0.9f), style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
            } else {
                val count = if (mode == SpectrumMode.FINE_BARS) 96 else 48
                val values = interpolateValues(fft, count)
                val gap = size.width / count.coerceAtLeast(1)
                val baseline = size.height * 0.86f
                values.forEachIndexed { i, raw ->
                    val v = raw.coerceIn(0f, 1.45f)
                    val bar = (v / 1.45f * baseline).coerceAtLeast(2.dp.toPx())
                    val x = i * gap + gap / 2
                    // bottom aqua section
                    val aquaHeight = (bar * 0.65f).coerceAtLeast(2.dp.toPx())
                    drawLine(color = palette.accent.copy(alpha = 0.95f), start = Offset(x, baseline), end = Offset(x, baseline - aquaHeight), strokeWidth = gap * if (mode == SpectrumMode.FINE_BARS) 0.52f else 0.70f, cap = StrokeCap.Round)
                    if (bar > aquaHeight) {
                        drawLine(color = palette.positive.copy(alpha = 0.98f), start = Offset(x, baseline - aquaHeight), end = Offset(x, baseline - bar), strokeWidth = gap * if (mode == SpectrumMode.FINE_BARS) 0.52f else 0.70f, cap = StrokeCap.Round)
                    }
                }
            }
        }
        if (mode == SpectrumMode.CURVE) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("100", color = palette.secondaryText, fontSize = 10.sp)
                Text("1k", color = palette.secondaryText, fontSize = 10.sp)
                Text("10k", color = palette.secondaryText, fontSize = 10.sp)
            }
        }
    }
}

private fun interpolateValues(source: FloatArray, targetCount: Int): List<Float> {
    if (source.isEmpty() || targetCount <= 0) return List(targetCount.coerceAtLeast(0)) { 0f }
    if (source.size == targetCount) return source.toList()
    return List(targetCount) { idx ->
        val position = idx.toFloat() * (source.size - 1) / (targetCount - 1).coerceAtLeast(1)
        val left = position.toInt().coerceIn(0, source.lastIndex)
        val right = (left + 1).coerceIn(0, source.lastIndex)
        val t = position - left
        source[left] * (1f - t) + source[right] * t
    }
}

@Composable
private fun Knob(value: Float, palette: ThemePalette, onChange: (Float) -> Unit) {
    Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(palette.secondaryText.copy(alpha = .25f), -210f, 240f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawArc(if (value >= 0f) palette.positive else palette.accent, -210f, (value + 1f) / 2f * 240f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(palette.surface, radius = size.minDimension * .38f)
        }
        Text("${(value * 12).toInt()}", color = if (value >= 0f) palette.positive else palette.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { onChange((value - .1f).coerceAtLeast(-1f)) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = if (value > 0f) palette.positive else palette.accent, modifier = Modifier.size(18.dp)) }
        IconButton(onClick = { onChange((value + .1f).coerceAtMost(1f)) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Add, contentDescription = "Increase", tint = if (value >= 0f) palette.positive else palette.accent, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun SettingsScreen(
    palette: ThemePalette,
    onSelectTheme: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onFolder: () -> Unit,
    onRescan: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PlaybackService.PREFS, Context.MODE_PRIVATE) }
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(context) {
        while (true) {
            remaining = (prefs.getLong(PlaybackService.KEY_DEADLINE, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(1000)
        }
    }
    fun updateTimer(minutes: Int) {
        val action = if (minutes == 0) PlaybackService.ACTION_CANCEL_SLEEP else PlaybackService.ACTION_SLEEP
        val intent = Intent(context, PlaybackService::class.java).apply {
            this.action = action
            putExtra(PlaybackService.EXTRA_MINUTES, minutes)
        }
        context.startService(intent)
        remaining = if (minutes == 0) 0L else minutes * 60_000L
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("APPEARANCE", color = palette.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        SettingsRow("Color theme", "${palette.label} — tap to change", Icons.Default.Palette, palette, onSelectTheme)
        Spacer(Modifier.height(5.dp))
        Text("SLEEP TIMER", color = palette.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(15.dp)) {
            Text(if (remaining > 0) "Playback pauses in ${remaining / 60_000L}m ${(remaining / 1000L) % 60}s" else "Timer inactive", color = palette.text)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    OutlinedButton(onClick = { updateTimer(minutes) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("${minutes}m") }
                }
            }
            TextButton(onClick = { updateTimer(0) }, enabled = remaining > 0) { Text("Cancel timer") }
        }
        Spacer(Modifier.height(5.dp))
        Text("LIBRARY", color = palette.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        SettingsRow("Choose music folder", "Use Android storage picker", Icons.Default.FolderOpen, palette, onFolder)
        SettingsRow("Rescan music folder", "Refresh songs in library", Icons.Default.Refresh, palette, onRescan)
        SettingsRow("Import playlist", "Load M3U / M3U8", Icons.Default.FileOpen, palette, onImport)
        SettingsRow("Export playlist", "Save an M3U playlist", Icons.Default.SaveAlt, palette, onExport)
        Spacer(Modifier.height(5.dp))
        Text("SYSTEM CONTROLS", color = palette.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(15.dp)) {
            Text("Media3 MediaSession", color = palette.text, fontWeight = FontWeight.Bold)
            Text("Play/pause, skip, headset and lock-screen controls are managed by Android while music is playing. Availability depends on device and Android version.", color = palette.secondaryText, fontSize = 13.sp)
        }
        SettingsRow("About", "Brave Audio ${BuildConfig.VERSION_NAME}", Icons.Default.Info, palette, onAbout)
        Spacer(Modifier.height(15.dp))
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, palette: ThemePalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).clickable(onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = palette.accent, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = 13.dp)) {
            Text(title, color = palette.text, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.secondaryText, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = palette.secondaryText)
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Brave Audio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = painterResource(id = R.drawable.author_avatar), contentDescription = "Author avatar", modifier = Modifier.size(92.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape), contentScale = ContentScale.Crop)
                Text("Brave Audio", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Version ${BuildConfig.VERSION_NAME}")
                Text("Author", fontWeight = FontWeight.SemiBold)
                Text("Kristo^Di^Giorgio")
                Text("b-r-a-v-e-o-n-e")
                Text("info@widelogic.org", color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                Text("Compatibility: Android 8.0+ (API 26 to 36)", fontSize = 13.sp)
                Text("Supported playback: common device-supported audio formats via Media3 / ExoPlayer.", fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ThemeSelectionDialog(currentKey: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                ThemePalettes.forEach { palette ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface).clickable { onSelect(palette.key); onDismiss() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            listOf(palette.background, palette.surface, palette.accent, palette.positive).forEach { c ->
                                Box(Modifier.size(18.dp).clip(CircleShape).background(c).border(1.dp, Color.Black.copy(alpha = .08f), CircleShape))
                            }
                        }
                        Text(palette.label, modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold)
                        if (palette.key == currentKey) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = palette.accent)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TextEntryDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TrackEditDialog(track: TrackEntity, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember(track.uri) { mutableStateOf(track.title) }
    var artist by remember(track.uri) { mutableStateOf(track.artist) }
    var album by remember(track.uri) { mutableStateOf(track.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit library details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, artist, album) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

@Composable
private fun rememberAlbumArt(uri: String): Bitmap? {
    val context = LocalContext.current
    val artwork by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = if (uri.isBlank()) null else withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uri))
                retriever.embeddedPicture?.let { bytes ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
    return artwork
}

@Composable
private fun VerticalEqSlider(value: Float, positiveColor: Color, negativeColor: Color, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    Canvas(modifier.pointerInput(Unit) {
        detectDragGestures(onDragStart = { start ->
            onChange((1f - (2f * start.y / size.height)).coerceIn(-1f, 1f))
        }) { change, _ ->
            onChange((1f - (2f * change.position.y / size.height)).coerceIn(-1f, 1f))
            change.consume()
        }
    }) {
        val centerX = size.width / 2f
        val top = 10.dp.toPx()
        val bottom = size.height - top
        val step = (bottom - top) / 24f
        val zeroY = top + (bottom - top) / 2f
        val markerY = top + ((1f - value) / 2f) * (bottom - top)
        for (i in 0..24) {
            val y = top + step * i
            val active = if (value >= 0f) y >= markerY && y <= zeroY else y <= markerY && y >= zeroY
            val color = when {
                active && value >= 0f -> positiveColor
                active && value < 0f -> negativeColor
                else -> Color(0xFF90A4B5).copy(alpha = .40f)
            }
            drawLine(color, Offset(centerX - 4.dp.toPx(), y), Offset(centerX + 4.dp.toPx(), y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        val markerColor = if (value >= 0f) positiveColor else negativeColor
        drawCircle(color = markerColor, radius = 8.dp.toPx(), center = Offset(centerX, markerY))
        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(centerX, markerY))
    }
}
