package org.widelogic.braveaudio.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val mimeType: String = "",
    val folderUri: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(primaryKeys = ["playlistId", "trackUri"], tableName = "playlist_tracks")
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackUri: String,
    val position: Int
)

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "uri",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackUri"
        )
    )
    val tracks: List<TrackEntity>
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(items: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(item: TrackEntity)

    @Update
    suspend fun updateTrack(item: TrackEntity)

    @Query("DELETE FROM tracks WHERE uri = :uri")
    suspend fun deleteTrack(uri: String)

    @Query("DELETE FROM tracks WHERE folderUri = :folderUri")
    suspend fun deleteFolderTracks(folderUri: String)

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun createPlaylist(item: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(item: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(item: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackUri = :trackUri")
    suspend fun removeFromPlaylist(playlistId: Long, trackUri: String)

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlistWithTracks(id: Long): PlaylistWithTracks?
}

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackCrossRef::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "brave_audio.db"
                ).build().also { INSTANCE = it }
            }
    }
}
