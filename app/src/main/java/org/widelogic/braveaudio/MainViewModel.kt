package org.widelogic.braveaudio

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.widelogic.braveaudio.data.*
import org.widelogic.braveaudio.playback.PlaybackHolder

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as BraveAudioApp).database
    private val dao = db.dao()
    private val scanner = LibraryScanner(app)

    val tracks = dao.observeTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = dao.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = app.getSharedPreferences("brave_audio", 0)
    private val _darkTheme = MutableStateFlow(prefs.getBoolean("dark", true))
    val darkTheme: StateFlow<Boolean> = _darkTheme

    private val _folderUri = MutableStateFlow(prefs.getString("folderUri", null))
    val folderUri: StateFlow<String?> = _folderUri

    fun setTheme(dark: Boolean) {
        _darkTheme.value = dark
        prefs.edit().putBoolean("dark", dark).apply()
    }

    fun selectFolder(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        _folderUri.value = uri.toString()
        prefs.edit().putString("folderUri", uri.toString()).apply()
        scanFolder()
    }

    fun scanFolder() {
        val value = _folderUri.value ?: return
        viewModelScope.launch {
            dao.deleteFolderTracks(value)
            dao.upsertTracks(scanner.scanTree(Uri.parse(value)))
        }
    }

    fun addSingle(uri: Uri) {
        viewModelScope.launch {
            scanner.readSingle(uri)?.let { dao.upsertTrack(it) }
        }
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
        p.setMediaItems(items, index, 0L)
        p.prepare()
        p.playWhenReady = true
    }

    fun createPlaylist(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.createPlaylist(PlaylistEntity(name = name.trim()))
    }

    fun renamePlaylist(item: PlaylistEntity, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.updatePlaylist(item.copy(name = name.trim()))
    }

    fun deletePlaylist(item: PlaylistEntity) = viewModelScope.launch { dao.deletePlaylist(item) }

    fun addToPlaylist(playlistId: Long, track: TrackEntity) = viewModelScope.launch {
        val current = dao.playlistWithTracks(playlistId)?.tracks.orEmpty()
        dao.addToPlaylist(PlaylistTrackCrossRef(playlistId, track.uri, current.size))
    }
}
