package org.widelogic.braveaudio.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryScanner(private val context: Context) {
    private val supportedExt = setOf(
        "mp3","m4a","aac","ogg","oga","opus","flac","wav","wave","amr","3gp","mp4","ac3","ec3"
    )

    suspend fun scanTree(treeUri: Uri): List<TrackEntity> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = mutableListOf<TrackEntity>()
        walk(root, treeUri.toString(), out)
        out
    }

    suspend fun readSingle(uri: Uri): TrackEntity? = withContext(Dispatchers.IO) {
        readTrack(uri, "", null)
    }

    private fun walk(file: DocumentFile, folderUri: String, out: MutableList<TrackEntity>) {
        if (file.isDirectory) {
            file.listFiles().forEach { walk(it, folderUri, out) }
            return
        }
        val name = file.name.orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        if (file.type?.startsWith("audio/") == true || ext in supportedExt) {
            readTrack(file.uri, folderUri, file.type)?.let(out::add)
        }
    }

    private fun readTrack(uri: Uri, folderUri: String, mime: String?): TrackEntity? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Unknown"
            TrackEntity(
                uri = uri.toString(),
                title = title,
                artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                mimeType = mime.orEmpty(),
                folderUri = folderUri
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }
}
