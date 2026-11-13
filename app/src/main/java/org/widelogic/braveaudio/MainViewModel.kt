package org.widelogic.braveaudio

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.widelogic.braveaudio.data.*
import org.widelogic.braveaudio.playback.PlaybackHolder

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as BraveAudioApp).database
    private val dao = db.dao()
    private val scanner = LibraryScanner(app)
    private val exchange = PlaylistExchange(app, dao)

    private val _exchangeMessage = MutableStateFlow("")
    val exchangeMessage: StateFlow<String> = _exchangeMessage

    val tracks = dao.observeTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = dao.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = app.getSharedPreferences("brave_audio", 0)

    private val _themeKey = MutableStateFlow(prefs.getString("themeKey", "ocean_dark") ?: "ocean_dark")
    val themeKey: StateFlow<String> = _themeKey
    fun setThemeKey(key: String) {
        _themeKey.value = key
        prefs.edit().putString("themeKey", key).apply()
    }

    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    val favorites: StateFlow<Set<String>> = _favorites
    fun toggleFavorite(uri: String) {
        val updated = _favorites.value.toMutableSet()
        if (!updated.add(uri)) updated.remove(uri)
        _favorites.value = updated
        prefs.edit().putStringSet("favorites", updated).apply()
    }

    private val _selectedPlaylistTracks = MutableStateFlow<List<TrackEntity>>(emptyList())
    val selectedPlaylistTracks: StateFlow<List<TrackEntity>> = _selectedPlaylistTracks
    fun loadPlaylist(id: Long) = viewModelScope.launch { _selectedPlaylistTracks.value = dao.getPlaylistTracks(id) }
    fun removeFromPlaylist(id: Long, uri: String) = viewModelScope.launch {
        dao.removeFromPlaylist(id, uri)
        _selectedPlaylistTracks.value = dao.getPlaylistTracks(id)
    }

    /** Remove only playlist membership, never physical files or Library entries. */
    fun clearPlaylist(id: Long) = viewModelScope.launch {
        dao.clearPlaylist(id)
        _selectedPlaylistTracks.value = dao.getPlaylistTracks(id)
    }

    /** Import selected SAF folder directly into a single playlist; recursively includes nested folders. */
    fun addFolderToPlaylist(playlistId: Long, folder: Uri) {
        val ctx = getApplication<Application>()
        runCatching { ctx.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModelScope.launch {
            _scanning.value = true
            try {
                val scanned = scanner.scanTree(folder)
                addTracksToPlaylist(playlistId, scanned)
            } finally { _scanning.value = false }
        }
    }

    /** Import one or more picked audio documents directly into a playlist. */
    fun addFilesToPlaylist(playlistId: Long, documents: List<Uri>) {
        val ctx = getApplication<Application>()
        documents.forEach { uri ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        viewModelScope.launch {
            _scanning.value = true
            try {
                val scanned = documents.distinct().mapNotNull { scanner.readSingle(it) }
                addTracksToPlaylist(playlistId, scanned)
            } finally { _scanning.value = false }
        }
    }

    private suspend fun addTracksToPlaylist(playlistId: Long, scanned: List<TrackEntity>) {
        if (scanned.isEmpty()) {
            _exchangeMessage.value = "No supported audio files were found."
            return
        }
        val current = dao.getPlaylistTracks(playlistId)
        val existing = current.map { it.uri }.toMutableSet()
        // Keep existing library metadata rather than overwriting user edits.
        val inLibrary = dao.listAllTracks().map { it.uri }.toHashSet()
        dao.upsertTracks(scanned.filter { it.uri !in inLibrary })
        var position = current.size
        var added = 0
        scanned.forEach { track ->
            if (existing.add(track.uri)) {
                dao.addToPlaylist(PlaylistTrackCrossRef(playlistId, track.uri, position++))
                added++
            }
        }
        _selectedPlaylistTracks.value = dao.getPlaylistTracks(playlistId)
        _exchangeMessage.value = "Added $added track(s) to playlist. ${scanned.size - added} already present."
    }

    private val _folderUri = MutableStateFlow(prefs.getString("folderUri", null))
    val folderUri: StateFlow<String?> = _folderUri

    fun selectFolder(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _folderUri.value = uri.toString()
        prefs.edit().putString("folderUri", uri.toString()).apply()
        scanFolder()
    }

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    fun scanFolder() {
        val value = _folderUri.value ?: return
        viewModelScope.launch {
            _scanning.value = true
            try {
                val found = scanner.scanTree(Uri.parse(value))
                dao.deleteFolderTracks(value)
                if (found.isNotEmpty()) dao.upsertTracks(found)
            } finally {
                _scanning.value = false
            }
        }
    }

    fun addSingle(uri: Uri) {
        viewModelScope.launch {
            scanner.readSingle(uri)?.let { dao.upsertTrack(it) }
        }
    }

    fun playPlaylist(playlistId: Long) = viewModelScope.launch {
        val list = dao.getPlaylistTracks(playlistId)
        if (list.isNotEmpty()) play(list.first(), list)
    }

    fun removeTrack(track: TrackEntity) = viewModelScope.launch { dao.deleteTrack(track.uri) }

    fun updateTrack(track: TrackEntity, title: String, artist: String, album: String) = viewModelScope.launch {
        dao.updateTrack(
            track.copy(
                title = title.trim().ifBlank { track.title },
                artist = artist.trim(),
                album = album.trim()
            )
        )
    }

    fun play(track: TrackEntity, queue: List<TrackEntity> = tracks.value) {
        val p = PlaybackHolder.init(getApplication())
        val items = queue.map { PlaybackHolder.mediaItem(it.uri, it.title, it.artist) }
        val index = queue.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
        p.stop()
        p.setMediaItems(items, index, 0L)
        p.prepare()
        p.playWhenReady = true
    }

    fun importPlaylist(uri: Uri) = viewModelScope.launch {
        _exchangeMessage.value = "Importing playlist…"
        _exchangeMessage.value = runCatching {
            val (matched, total) = withContext(Dispatchers.IO) { exchange.importM3u(uri) }
            "Playlist imported: $matched of $total tracks matched"
        }.getOrElse { "Import failed: ${it.message ?: "Unknown error"}" }
    }

    fun exportPlaylist(id: Long, uri: Uri) = viewModelScope.launch {
        _exchangeMessage.value = runCatching {
            val count = withContext(Dispatchers.IO) { exchange.exportM3u(id, uri) }
            "Exported $count tracks"
        }.getOrElse { "Export failed: ${it.message ?: "Unknown error"}" }
    }

    fun clearExchangeMessage() { _exchangeMessage.value = "" }

    /** The callback opens the newly created playlist immediately. */
    fun createPlaylist(name: String, onCreated: (PlaylistEntity) -> Unit = {}) = viewModelScope.launch {
        if (name.isNotBlank()) {
            val entity = PlaylistEntity(name = name.trim())
            val id = dao.createPlaylist(entity)
            onCreated(entity.copy(id = id))
        }
    }

    fun renamePlaylist(item: PlaylistEntity, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.updatePlaylist(item.copy(name = name.trim()))
    }

    fun deletePlaylist(item: PlaylistEntity) = viewModelScope.launch {
        dao.clearPlaylist(item.id)
        dao.deletePlaylist(item)
    }

    fun addToPlaylist(playlistId: Long, track: TrackEntity) = viewModelScope.launch {
        val current = dao.getPlaylistTracks(playlistId)
        dao.addToPlaylist(PlaylistTrackCrossRef(playlistId, track.uri, current.size))
        _selectedPlaylistTracks.value = dao.getPlaylistTracks(playlistId)
    }

    init {
        if (_folderUri.value != null) scanFolder()
    }
}
