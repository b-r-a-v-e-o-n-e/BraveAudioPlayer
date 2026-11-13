package org.widelogic.braveaudio.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URLDecoder
import java.util.Locale

/** Import/export M3U playlists. SAF content URIs are preserved when exporting.
 *  Files from other devices can only be matched to locally indexed tracks.
 */
class PlaylistExchange(private val context: Context, private val dao: LibraryDao) {
    suspend fun exportM3u(playlistId: Long, uri: Uri): Int {
        val playlist = dao.playlistWithTracks(playlistId) ?: error("Playlist no longer exists")
        val tracks = dao.getPlaylistTracks(playlistId)
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write destination")
        stream.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).buffered().use { writer ->
                writer.appendLine("#EXTM3U")
                writer.appendLine("#PLAYLIST:${playlist.playlist.name.replace('\n',' ')}")
                tracks.forEach { t ->
                    writer.appendLine("#EXTINF:${t.durationMs / 1000},${t.artist.replace(',', ' ')} - ${t.title.replace('\n',' ')}")
                    writer.appendLine(t.uri)
                }
            }
        }
        return tracks.size
    }

    suspend fun importM3u(uri: Uri): Pair<Int, Int> {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open playlist")
        val lines = stream.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readLines() }
        val existing = dao.listAllTracks()
        val byUri = existing.associateBy { it.uri }
        val byTitle = existing.groupBy { it.title.lowercase(Locale.ROOT) }
        val byFilename = existing.groupBy { filename(it.uri) }
        val referenced = lines.map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() && !it.startsWith('#') }
        val matched = linkedSetOf<String>()
        referenced.forEach { line ->
            val actual = byUri[line] ?: byFilename[filename(line)]?.singleOrNull()
                ?: byTitle[filename(line).substringBeforeLast('.', filename(line))]?.singleOrNull()
            if (actual != null) matched.add(actual.uri)
        }
        val name = displayName(uri).substringBeforeLast('.').ifBlank { "Imported playlist" }.take(80)
        val id = dao.createPlaylist(PlaylistEntity(name = name))
        matched.forEachIndexed { i, trackUri -> dao.addToPlaylist(PlaylistTrackCrossRef(id, trackUri, i)) }
        return matched.size to referenced.size
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: "Imported playlist.m3u"

    private fun filename(value: String): String = runCatching {
        URLDecoder.decode(value.substringAfterLast('/'), "UTF-8").substringAfterLast(':').lowercase(Locale.ROOT)
    }.getOrDefault(value.substringAfterLast('/').lowercase(Locale.ROOT))
}
