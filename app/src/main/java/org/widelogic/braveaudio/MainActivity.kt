package org.widelogic.braveaudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import org.widelogic.braveaudio.data.PlaylistEntity
import org.widelogic.braveaudio.data.TrackEntity
import org.widelogic.braveaudio.playback.AudioVisualizer
import org.widelogic.braveaudio.playback.EqualizerController
import org.widelogic.braveaudio.playback.PlaybackHolder
import org.widelogic.braveaudio.playback.PlaybackService

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaybackHolder.init(this)
        // Keep MediaSessionService alive so playback can transition to foreground/background.
        startService(Intent(this, PlaybackService::class.java))

        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let(vm::addSingle)
        }

        setContent {
            val dark by vm.darkTheme.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { BraveAudioUi(vm) }
            }
        }
    }
}

private enum class Tab(val label: String) {
    Library("Library"), Playlists("Playlists"), Player("Player"), Eq("EQ"), Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BraveAudioUi(vm: MainViewModel) {
    var tab by remember { mutableStateOf(Tab.Library) }
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::selectFolder)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::addSingle)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brave Audio") },
                actions = {
                    if (tab == Tab.Library) {
                        IconButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Default.AudioFile, "Open audio file")
                        }
                        IconButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, "Choose folder")
                        }
                        IconButton(onClick = vm::scanFolder) {
                            Icon(Icons.Default.Refresh, "Rescan")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                when(item) {
                                    Tab.Library -> Icons.Default.LibraryMusic
                                    Tab.Playlists -> Icons.Default.QueueMusic
                                    Tab.Player -> Icons.Default.PlayCircle
                                    Tab.Eq -> Icons.Default.Equalizer
                                    Tab.Settings -> Icons.Default.Settings
                                },
                                item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when(tab) {
                Tab.Library -> LibraryScreen(tracks, playlists, vm)
                Tab.Playlists -> PlaylistsScreen(playlists, vm)
                Tab.Player -> PlayerScreen()
                Tab.Eq -> EqualizerScreen()
                Tab.Settings -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<TrackEntity>,
    playlists: List<PlaylistEntity>,
    vm: MainViewModel
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(tracks, query) {
        if (query.isBlank()) tracks else tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Search title, artist, album") },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            singleLine = true
        )
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No music yet. Choose a folder or open an audio file.")
            }
        } else {
            LazyColumn {
                items(filtered, key = { it.uri }) { track ->
                    TrackRow(track, playlists, onPlay = { vm.play(track, filtered) },
                        onDelete = { vm.removeTrack(track) },
                        onEdit = { title, artist, album -> vm.updateTrack(track, title, artist, album) },
                        onAddToPlaylist = { id -> vm.addToPlaylist(id, track) })
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    playlists: List<PlaylistEntity>,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (String, String, String) -> Unit,
    onAddToPlaylist: (Long) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Unknown artist" })
        },
        leadingContent = { Icon(Icons.Default.MusicNote, null) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = { menu = false; onPlay() }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit library info") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; edit = true }
                    )
                    playlists.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("Add to ${p.name}") },
                            onClick = { menu = false; onAddToPlaylist(p.id) }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Remove from library") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (edit) {
        TrackEditDialog(
            track = track,
            onDismiss = { edit = false },
            onSave = { title, artist, album ->
                edit = false
                onEdit(title, artist, album)
            }
        )
    }
}


@Composable
private fun TrackEditDialog(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember(track.uri) { mutableStateOf(track.title) }
    var artist by remember(track.uri) { mutableStateOf(track.artist) }
    var album by remember(track.uri) { mutableStateOf(track.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit library info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, artist, album) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlaylistsScreen(playlists: List<PlaylistEntity>, vm: MainViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<PlaylistEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = { showCreate = true },
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New playlist")
        }
        LazyColumn {
            items(playlists, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    leadingContent = { Icon(Icons.Default.QueueMusic, null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { edit = p }) { Icon(Icons.Default.Edit, "Rename") }
                            IconButton(onClick = { vm.deletePlaylist(p) }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                )
            }
        }
    }
    if (showCreate) NameDialog("New playlist", "", onDismiss = { showCreate = false }) {
        vm.createPlaylist(it); showCreate = false
    }
    edit?.let { p ->
        NameDialog("Rename playlist", p.name, onDismiss = { edit = null }) {
            vm.renamePlaylist(p, it); edit = null
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PlayerScreen() {
    val player = remember { PlaybackHolder.player }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var title by remember { mutableStateOf(player.currentMediaMetadata.title?.toString().orEmpty()) }
    var artist by remember { mutableStateOf(player.currentMediaMetadata.artist?.toString().orEmpty()) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                title = mediaMetadata.title?.toString().orEmpty()
                artist = mediaMetadata.artist?.toString().orEmpty()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(500)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.size(220.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(96.dp))
            }
        }
        Text(title.ifBlank { "Nothing playing" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(artist.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = { if (duration > 0) player.seekTo((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )
        Text("${fmt(position)} / ${fmt(duration)}")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { player.seekToPreviousMediaItem() }) {
                Icon(Icons.Default.SkipPrevious, "Previous")
            }
            FilledIconButton(
                onClick = { if (player.isPlaying) player.pause() else player.play() },
                modifier = Modifier.size(70.dp)
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", modifier = Modifier.size(36.dp))
            }
            FilledTonalIconButton(onClick = { player.seekToNextMediaItem() }) {
                Icon(Icons.Default.SkipNext, "Next")
            }
        }
    }
}

@Composable
private fun EqualizerScreen() {
    val player = remember { PlaybackHolder.player }
    val eq = remember { EqualizerController(player) }
    val visualizer = remember { AudioVisualizer(player) }
    val levels by eq.levels.collectAsStateWithLifecycle()
    val fft by visualizer.fft.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) visualizer.start()
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        eq.attach()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            visualizer.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        onDispose { eq.release(); visualizer.stop() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Spectrum", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Spectrum(fft)
        }
        Text("6-band equalizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        eq.frequencies.forEachIndexed { i, label ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label)
                    Text("${(levels[i] * 12).toInt()} dB")
                }
                Slider(
                    value = levels[i],
                    onValueChange = { eq.setBand(i, it) },
                    valueRange = -1f..1f
                )
            }
        }
    }
}

@Composable
private fun Spectrum(values: FloatArray) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val n = values.size.coerceAtLeast(1)
        val w = size.width / n
        values.forEachIndexed { i, v ->
            val h = (v.coerceIn(0f, 1.6f) / 1.6f) * size.height
            drawLine(
                color = color,
                start = Offset(i * w + w / 2, size.height),
                end = Offset(i * w + w / 2, size.height - h),
                strokeWidth = w * .58f
            )
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    val dark by vm.darkTheme.collectAsStateWithLifecycle()
    val folder by vm.folderUri.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card {
            ListItem(
                headlineContent = { Text("Dark theme") },
                supportingContent = { Text("Switch between dark and light appearance") },
                trailingContent = { Switch(dark, vm::setTheme) }
            )
        }
        Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card {
            ListItem(
                headlineContent = { Text("Selected folder") },
                supportingContent = { Text(folder ?: "No folder selected", maxLines = 2, overflow = TextOverflow.Ellipsis) }
            )
        }
        Text("Audio formats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("MP3, AAC/M4A, OGG/Vorbis, Opus, FLAC, WAV/PCM, AMR and other formats supported by the device/Media3 decoder.")
        Text("Brave Audio 1.0.0", style = MaterialTheme.typography.bodySmall)
    }
}

private fun fmt(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
