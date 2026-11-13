package org.widelogic.braveaudio

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.widelogic.braveaudio.data.PlaylistEntity
import org.widelogic.braveaudio.data.TrackEntity
import org.widelogic.braveaudio.playback.AudioEffects
import org.widelogic.braveaudio.playback.AudioVisualizer
import org.widelogic.braveaudio.playback.PlaybackHolder
import org.widelogic.braveaudio.playback.PlaybackService

private val Navy = Color(0xFF07182F)
private val Deep = Color(0xFF0C2442)
private val Panel = Color(0xFF142F50)
private val Raised = Color(0xFF1B3B5E)
private val Aqua = Color(0xFF16E4E8)
private val Ice = Color(0xFFB9FAFF)
private val Muted = Color(0xFF9EB9D0)
private val Sky = Color(0xFF4FAEEA)
private val LightBackground = Color(0xFFEAF7FC)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackHolder.init(this)
        startService(Intent(this, PlaybackService::class.java))
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let(vm::addSingle)
        setContent {
            val dark by vm.darkTheme.collectAsStateWithLifecycle()
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme(primary = Aqua, secondary = Ice, background = Navy, surface = Panel, onSurface = Color.White)
                else lightColorScheme(primary = Color(0xFF007C96), secondary = Color(0xFF007E91), background = LightBackground, surface = Color.White, onSurface = Deep)
            ) { BraveAudioScreen(vm, dark) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let(vm::addSingle)
    }
}

private enum class Section { HOME, LIBRARY, EQUALIZER }
private enum class Category { SONGS, ALBUMS, ARTISTS, PLAYLISTS, FAVORITES }

@Composable
private fun BraveAudioScreen(vm: MainViewModel, dark: Boolean) {
    var section by remember { mutableStateOf(Section.HOME) }
    var category by remember { mutableStateOf(Category.SONGS) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
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
    val state = rememberPlayerState()
    val player = remember { PlaybackHolder.player }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::selectFolder) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::addSingle) }

    val background = if (dark) Navy else LightBackground
    val textColor = if (dark) Color.White else Deep
    val surfaceColor = if (dark) Panel else Color.White
    val accent = if (dark) Aqua else Color(0xFF008DA3)

    Scaffold(
        containerColor = background,
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.clip(CircleShape).background(surfaceColor)) {
                            Icon(Icons.Default.Menu, "Menu", tint = textColor)
                        }
                        DropdownMenu(showMenu, { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Choose music folder") }, onClick = { showMenu = false; folderPicker.launch(null) }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
                            DropdownMenuItem(text = { Text("Open audio file") }, onClick = { showMenu = false; filePicker.launch(arrayOf("audio/*")) }, leadingIcon = { Icon(Icons.Default.AudioFile, null) })
                            DropdownMenuItem(text = { Text("Rescan folder") }, onClick = { showMenu = false; vm.scanFolder() }, leadingIcon = { Icon(Icons.Default.Refresh, null) })
                            DropdownMenuItem(text = { Text(if (dark) "Light theme" else "Dark theme") }, onClick = { showMenu = false; vm.setTheme(!dark) }, leadingIcon = { Icon(Icons.Default.Brightness6, null) })
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(when(section) { Section.HOME -> "Brave Audio"; Section.LIBRARY -> "Library"; Section.EQUALIZER -> "Equalizer" }, color = textColor, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                        if (scanning) Text("Scanning music…", color = accent, fontSize = 11.sp)
                    }
                    IconButton(onClick = { section = Section.LIBRARY; showSearch = !showSearch }, modifier = Modifier.clip(CircleShape).background(surfaceColor)) {
                        Icon(Icons.Default.Search, "Search", tint = textColor)
                    }
                }
                if (showSearch) OutlinedTextField(
                    search, { search = it; section = Section.LIBRARY; selectedPlaylist = null },
                    placeholder = { Text("Search song, album or artist") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    trailingIcon = { IconButton(onClick = { search = ""; showSearch = false }) { Icon(Icons.Default.Close, "Close") } }
                )
            }
        },
        bottomBar = {
            Column {
                if (section != Section.HOME && state.title.isNotBlank()) MiniPlayer(state, onOpen = { section = Section.HOME })
                NavigationBar(containerColor = if (dark) Deep else Color.White, tonalElevation = 0.dp) {
                    listOf(Section.HOME, Section.LIBRARY, Section.EQUALIZER).forEach { item ->
                        NavigationBarItem(
                            selected = section == item,
                            onClick = { section = item },
                            icon = { Icon(when(item) { Section.HOME -> Icons.Default.Home; Section.LIBRARY -> Icons.Default.LibraryMusic; Section.EQUALIZER -> Icons.Default.Tune }, item.name, tint = if(section == item) accent else Muted) },
                            label = { Text(when(item) { Section.HOME -> "Player"; Section.LIBRARY -> "Library"; Section.EQUALIZER -> "Equalizer" }) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = if (dark) Raised else Color(0xFFD4F8FA), selectedTextColor = accent, unselectedTextColor = Muted)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when(section) {
                Section.HOME -> HomeScreen(state, vm, favorites, dark, onOpenLibrary = { section = Section.LIBRARY })
                Section.LIBRARY -> LibraryScreen(
                    tracks, playlists, favorites, category, { category = it; selectedPlaylist = null },
                    selectedPlaylist, selectedTracks, search, dark,
                    onSong = { t, queue -> vm.play(t, queue) },
                    onFavorite = vm::toggleFavorite,
                    onMenu = { menuTrack = it },
                    onCreatePlaylist = { newPlaylist = true },
                    onPlaylist = { p -> selectedPlaylist = p; vm.loadPlaylist(p.id) },
                    onPlaylistPlay = { vm.playPlaylist(it.id) },
                    onPlaylistRename = { renamePlaylist = it },
                    onPlaylistDelete = { vm.deletePlaylist(it); selectedPlaylist = null },
                    onPlaylistRemove = { p, t -> vm.removeFromPlaylist(p.id, t.uri) },
                    onBack = { selectedPlaylist = null },
                    onFolder = { folderPicker.launch(null) }
                )
                Section.EQUALIZER -> EqualizerScreen(dark)
            }
        }
    }
    if (newPlaylist) TextEntryDialog("New playlist", "", onDismiss = { newPlaylist = false }) {
        vm.createPlaylist(it); newPlaylist = false
    }
    renamePlaylist?.let { p -> TextEntryDialog("Rename playlist", p.name, onDismiss = { renamePlaylist = null }) {
        vm.renamePlaylist(p, it); renamePlaylist = null
    } }
    menuTrack?.let { t ->
        AlertDialog(
            onDismissRequest = { menuTrack = null },
            title = { Text(t.title, maxLines = 2) },
            text = {
                Column {
                    TextButton(onClick = { vm.toggleFavorite(t.uri); menuTrack = null }) { Text(if (t.uri in favorites) "Remove favorite" else "Add to favorites") }
                    TextButton(onClick = { editedTrack = t; menuTrack = null }) { Text("Edit library metadata") }
                    playlists.forEach { p -> TextButton(onClick = { vm.addToPlaylist(p.id, t); menuTrack = null }) { Text("Add to ${p.name}") } }
                    TextButton(onClick = { vm.removeTrack(t); menuTrack = null }) { Text("Remove from library", color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton(onClick = { menuTrack = null }) { Text("Close") } }
        )
    }
    editedTrack?.let { t -> TrackEditDialog(t, onDismiss = { editedTrack = null }) { title, artist, album ->
        vm.updateTrack(t, title, artist, album); editedTrack = null
    } }
}

private data class PlaybackUi(
    val title: String = "", val artist: String = "", val uri: String = "", val isPlaying: Boolean = false,
    val position: Long = 0L, val duration: Long = 0L, val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF
)

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
            duration = player.duration.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
            repeat = player.repeatMode
        )
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { update() }
        }
        player.addListener(listener)
        update()
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) { while(true) { update(); delay(450) } }
    return state
}

@Composable
private fun HomeScreen(state: PlaybackUi, vm: MainViewModel, favorites: Set<String>, dark: Boolean, onOpenLibrary: () -> Unit) {
    val player = remember { PlaybackHolder.player }
    val accent = if (dark) Aqua else Color(0xFF008DA3)
    val surface = if (dark) Panel else Color.White
    val text = if (dark) Color.White else Deep
    val secondary = if (dark) Muted else Color(0xFF466984)
    val ctx = LocalContext.current
    val audioManager = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15 }
    var volume by remember { mutableFloatStateOf((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7).toFloat() / maxVolume) }
    val isFav = state.uri.isNotBlank() && state.uri in favorites
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                drawCircle(color = accent.copy(alpha = 0.11f), radius = w * .49f)
                drawCircle(brush = Brush.sweepGradient(listOf(Sky, Aqua, Ice, Sky)), radius = w * .45f, style = Stroke(width = 11.dp.toPx()))
                drawCircle(color = if(dark) Deep else Color(0xFFDAF2FA), radius = w * .39f)
            }
            val albumArt = rememberAlbumArt(state.uri)
            if (albumArt != null) {
                Image(bitmap = albumArt.asImageBitmap(), contentDescription = "Album art", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(167.dp).clip(CircleShape))
            }
            IconButton(
                onClick = { if (state.title.isBlank()) onOpenLibrary() else if (player.isPlaying) player.pause() else player.play() },
                modifier = Modifier.size(116.dp)
            ) { Icon(if(state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play / pause", tint = accent, modifier = Modifier.size(96.dp)) }
        }
        Spacer(Modifier.height(25.dp))
        Text("NOW PLAYING", color = secondary, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
        Text(state.title.ifBlank { "Select music to play" }, color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(state.artist.ifBlank { "Brave Audio" }, color = secondary, fontSize = 15.sp)
        Spacer(Modifier.height(15.dp))
        HomeWaveform(accent)
        Spacer(Modifier.height(19.dp))
        Column(
            Modifier.fillMaxWidth().shadow(9.dp, RoundedCornerShape(24.dp)).background(surface, RoundedCornerShape(24.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = text, modifier = Modifier.size(32.dp)) }
                FilledIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(66.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)) {
                    Icon(if(state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Navy, modifier = Modifier.size(39.dp))
                }
                IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next", tint = text, modifier = Modifier.size(32.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Slider(
                value = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f,1f) else 0f,
                onValueChange = { if (state.duration > 0) player.seekTo((it * state.duration).toLong()) },
                modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.position), color = secondary, fontSize = 12.sp)
                Text(formatTime(state.duration), color = secondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeDown, "Volume", tint = secondary)
                Slider(value = volume, onValueChange = {
                    volume = it
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVolume).toInt(), 0)
                }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                Icon(Icons.Default.VolumeUp, null, tint = secondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if(state.shuffle) accent else secondary)
                }
                IconButton(onClick = { if(state.uri.isNotBlank()) vm.toggleFavorite(state.uri) }) {
                    Icon(if(isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if(isFav) accent else secondary)
                }
                IconButton(onClick = { player.repeatMode = when (player.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }) {
                    Icon(if(state.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat", tint = if(state.repeat != Player.REPEAT_MODE_OFF) accent else secondary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeWaveform(color: Color) {
    Canvas(Modifier.width(260.dp).height(33.dp)) {
        val count = 42
        for (i in 0 until count) {
            val h = (5 + (i * 17 % 23)).dp.toPx().coerceAtMost(size.height)
            val x = i * (size.width / count)
            drawLine(color.copy(alpha = .35f + .65f * ((i % 5).toFloat() / 5f)), Offset(x, (size.height-h)/2), Offset(x, (size.height+h)/2), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun MiniPlayer(state: PlaybackUi, onOpen: () -> Unit) {
    val p = remember { PlaybackHolder.player }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.GraphicEq, null, tint = Aqua, modifier = Modifier.size(35.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontWeight = FontWeight.Bold)
            Text(state.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
        }
        Text(formatTime(state.position), fontSize = 11.sp, color = Muted)
        IconButton(onClick = { if(p.isPlaying) p.pause() else p.play() }) {
            Icon(if(state.isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, "Play", tint = Aqua, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<TrackEntity>, playlists: List<PlaylistEntity>, favorites: Set<String>,
    category: Category, setCategory: (Category) -> Unit,
    selectedPlaylist: PlaylistEntity?, selectedTracks: List<TrackEntity>, search: String, dark: Boolean,
    onSong: (TrackEntity, List<TrackEntity>) -> Unit,
    onFavorite: (String) -> Unit, onMenu: (TrackEntity) -> Unit, onCreatePlaylist: () -> Unit,
    onPlaylist: (PlaylistEntity) -> Unit, onPlaylistPlay: (PlaylistEntity) -> Unit,
    onPlaylistRename: (PlaylistEntity) -> Unit, onPlaylistDelete: (PlaylistEntity) -> Unit,
    onPlaylistRemove: (PlaylistEntity, TrackEntity) -> Unit, onBack: () -> Unit, onFolder: () -> Unit
) {
    val fg = if (dark) Color.White else Deep
    val secondary = if (dark) Muted else Color(0xFF516F85)
    val card = if (dark) Panel else Color.White
    val accent = if (dark) Aqua else Color(0xFF008DA3)
    val visibleTracks = tracks.filter { t -> search.isBlank() || listOf(t.title,t.artist,t.album).any { it.contains(search,true) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Category.entries.forEach { cat ->
                val selected = category == cat
                Box(Modifier.clip(RoundedCornerShape(15.dp)).background(if(selected) accent else card).clickable { setCategory(cat) }.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if(selected) Navy else fg)
                }
            }
        }
        if (selectedPlaylist != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = fg) }
                Text(selectedPlaylist.name, color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onPlaylistPlay(selectedPlaylist) }) { Icon(Icons.Default.PlayArrow, "Play playlist", tint = accent) }
            }
            LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(selectedTracks, key = { it.uri }) { t -> TrackCard(t, card, fg, secondary, accent, t.uri in favorites,
                    onPlay = { onSong(t, selectedTracks) }, onFavorite = { onFavorite(t.uri) }, onMenu = { onPlaylistRemove(selectedPlaylist, t) }) }
            }
            return@Column
        }
        when(category) {
            Category.SONGS, Category.FAVORITES -> {
                val list = if(category == Category.FAVORITES) visibleTracks.filter { it.uri in favorites } else visibleTracks
                if(list.isEmpty()) EmptyLibrary(onFolder)
                else LazyColumn(contentPadding = PaddingValues(start=12.dp,end=12.dp,bottom=18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(list, key = { it.uri }) { t -> TrackCard(t, card, fg, secondary, accent, t.uri in favorites,
                        onPlay = { onSong(t,list) }, onFavorite = { onFavorite(t.uri) }, onMenu = { onMenu(t) }) }
                }
            }
            Category.ALBUMS, Category.ARTISTS -> {
                val groups = visibleTracks.groupBy { if(category == Category.ALBUMS) it.album.ifBlank { "Unknown album" } else it.artist.ifBlank { "Unknown artist" } }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(groups.entries.sortedBy { it.key }) { entry ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(card).clickable { entry.value.firstOrNull()?.let { onSong(it, entry.value) } }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if(category == Category.ALBUMS) Icons.Default.Album else Icons.Default.Person, null, tint = accent, modifier = Modifier.size(36.dp))
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(entry.key, color = fg, fontWeight = FontWeight.Bold)
                                Text("${entry.value.size} songs", color = secondary, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.PlayCircleFilled, "Play", tint = accent)
                        }
                    }
                }
            }
            Category.PLAYLISTS -> {
                Button(onClick = onCreatePlaylist, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("New playlist") }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QueueMusic, null, tint = accent, modifier = Modifier.padding(7.dp))
                            Text(playlist.name, modifier = Modifier.weight(1f).clickable { onPlaylist(playlist) }.padding(8.dp), color = fg, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onPlaylistPlay(playlist) }) { Icon(Icons.Default.PlayArrow, "Play", tint = accent) }
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Options", tint = fg) }
                                DropdownMenu(expanded, { expanded = false }) {
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
        Icon(Icons.Default.LibraryMusic, null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text("No music found", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onFolder) { Text("Choose music folder") }
    }
}

@Composable
private fun TrackCard(track: TrackEntity, card: Color, fg: Color, secondary: Color, accent: Color, favorite: Boolean,
                      onPlay: () -> Unit, onFavorite: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(card).heightIn(min = 76.dp).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Deep, Color(0xFF006B8F), Color(0xFF00B9C2)))), contentAlignment = Alignment.Center) {
            val art = rememberAlbumArt(track.uri)
            if (art != null) Image(art.asImageBitmap(), "Album art", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.MusicNote, null, tint = Ice, modifier = Modifier.size(31.dp))
        }
        Column(Modifier.weight(1f).padding(start=12.dp)) {
            Text(track.title, color = fg, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifBlank { track.album.ifBlank { "Unknown artist" } }, color = secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(track.durationMs), color = secondary, fontSize = 12.sp)
        IconButton(onClick = onPlay) { Icon(Icons.Default.PlayCircleFilled, "Play ${track.title}", tint = accent, modifier = Modifier.size(29.dp)) }
        Box {
            var expanded by remember { mutableStateOf(false) }
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.MoreVert, "More", tint = secondary) }
            DropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem(text = { Text(if(favorite) "Remove favorite" else "Favorite") }, onClick = { expanded = false; onFavorite() })
                DropdownMenuItem(text = { Text("More options / Remove") }, onClick = { expanded = false; onMenu() })
            }
        }
    }
}

@Composable
private fun EqualizerScreen(dark: Boolean) {
    val ctx = LocalContext.current
    val player = remember { PlaybackHolder.player }
    val eq = remember { AudioEffects.equalizer(player) }
    val viz = remember { AudioVisualizer(player) }
    val levels by eq.levels.collectAsStateWithLifecycle()
    val fft by viz.fft.collectAsStateWithLifecycle()
    val accent = if(dark) Aqua else Color(0xFF008DA3)
    val fg = if(dark) Color.White else Deep
    val card = if(dark) Panel else Color.White
    var preset by remember { mutableStateOf("Custom") }
    val presets = remember { mapOf(
        "Flat" to listOf(0f,0f,0f,0f,0f,0f),
        "Pop" to listOf(.3f,.12f,-.1f,-.06f,.2f,.3f),
        "Rock" to listOf(.48f,.25f,-.1f,.16f,.32f,.45f),
        "EDM" to listOf(.65f,.35f,0f,-.23f,.2f,.55f),
        "Jazz" to listOf(.2f,.12f,.0f,.1f,.18f,.16f)
    ) }
    val visualizerPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) viz.start() }
    DisposableEffect(player) {
        eq.attach()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viz.start()
        onDispose { viz.stop() }
    }
    // Restart visualization when session changes after opening first track.
    var session by remember { mutableIntStateOf(player.audioSessionId) }
    LaunchedEffect(player) {
        while(true) {
            if(session != player.audioSessionId) { session = player.audioSessionId; eq.attach(); viz.start() }
            delay(900)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Bass", "Mid", "Treble").forEachIndexed { index, label ->
                val band = listOf(0,3,5)[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Knob(levels[band], accent, card) { eq.setBand(band,it); preset = "Custom" }
                    Text(label, color = fg, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(card).padding(15.dp)) {
            Text("6-BAND EQUALIZER", color = fg, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().height(250.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(29.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf("+12","+6","0","-6","-12").forEach { Text(it, color = Muted, fontSize = 10.sp) }
                }
                eq.frequencies.forEachIndexed { index, label ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label.replace(" Hz",""), color = fg, fontSize = 10.sp, maxLines = 1)
                        Spacer(Modifier.height(7.dp))
                        VerticalEqSlider(
                            value = levels[index],
                            accent = accent,
                            modifier = Modifier.weight(1f).width(35.dp),
                            onChange = { eq.setBand(index,it); preset = "Custom" }
                        )
                        Text("${(levels[index] * 12).toInt()}", color = accent, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (name, values) ->
                val selected = preset == name
                Box(Modifier.clip(RoundedCornerShape(13.dp)).background(if(selected) accent else card).clickable {
                    values.forEachIndexed { i, v -> eq.setBand(i,v) }
                    preset = name
                }.padding(horizontal=14.dp, vertical=10.dp)) { Text(name, color = if(selected) Navy else fg, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(19.dp))
        Text("LIVE SPECTRUM", color = fg, fontWeight = FontWeight.Bold, fontSize=13.sp)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(18.dp)).background(card).padding(12.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val gap = size.width / fft.size.coerceAtLeast(1)
                fft.forEachIndexed { i, v ->
                    val bar = (v.coerceIn(0f,1.4f)/1.4f * size.height).coerceAtLeast(2.dp.toPx())
                    drawLine(brush = Brush.verticalGradient(listOf(Ice,accent)), start = Offset(i * gap + gap/2,size.height), end = Offset(i*gap+gap/2,size.height-bar), strokeWidth = gap*.65f, cap = StrokeCap.Round)
                }
            }
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            TextButton(onClick = { visualizerPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Enable audio spectrum permission") }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun Knob(value: Float, accent: Color, card: Color, onChange: (Float) -> Unit) {
    Box(Modifier.size(94.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(Muted.copy(alpha=.32f), -210f, 240f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawArc(accent, -210f, (value+1f)/2f * 240f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(card, radius = size.minDimension * .38f)
        }
        Text("${(value*12).toInt()}", color = accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { onChange((value-.1f).coerceAtLeast(-1f)) }, modifier=Modifier.size(30.dp)) { Icon(Icons.Default.Remove, "Decrease", tint = accent, modifier=Modifier.size(18.dp)) }
        IconButton(onClick = { onChange((value+.1f).coerceAtMost(1f)) }, modifier=Modifier.size(30.dp)) { Icon(Icons.Default.Add, "Increase", tint = accent, modifier=Modifier.size(18.dp)) }
    }
}

@Composable
private fun TextEntryDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title={ Text(title) }, text={
        OutlinedTextField(value, { value = it }, label={ Text("Name") }, singleLine=true)
    }, confirmButton={ TextButton(onClick={ if(value.isNotBlank()) onSave(value) }) { Text("Save") } },
        dismissButton={ TextButton(onClick=onDismiss) { Text("Cancel") } })
}

@Composable
private fun TrackEditDialog(track: TrackEntity, onDismiss: () -> Unit, onSave: (String,String,String) -> Unit) {
    var title by remember(track.uri) { mutableStateOf(track.title) }
    var artist by remember(track.uri) { mutableStateOf(track.artist) }
    var album by remember(track.uri) { mutableStateOf(track.album) }
    AlertDialog(onDismissRequest=onDismiss, title={Text("Edit library details")}, text={
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title,{title=it}, label={Text("Title")}, singleLine=true)
            OutlinedTextField(artist,{artist=it}, label={Text("Artist")}, singleLine=true)
            OutlinedTextField(album,{album=it}, label={Text("Album")}, singleLine=true)
        }
    }, confirmButton={TextButton(onClick={onSave(title,artist,album)}) {Text("Save")}}, dismissButton={TextButton(onClick=onDismiss) {Text("Cancel")}})
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
            } catch (_: Exception) { null }
            finally { runCatching { retriever.release() } }
        }
    }
    return artwork
}

@Composable
private fun VerticalEqSlider(value: Float, accent: Color, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
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
        val markerY = top + ((1f-value)/2f) * (bottom-top)
        for (i in 0..24) {
            val y = top + step * i
            val active = y >= markerY
            drawLine(if (active) accent else Muted.copy(alpha=.40f), Offset(centerX-4.dp.toPx(), y), Offset(centerX+4.dp.toPx(), y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(color = accent, radius = 8.dp.toPx(), center = Offset(centerX,markerY))
        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(centerX,markerY))
    }
}
