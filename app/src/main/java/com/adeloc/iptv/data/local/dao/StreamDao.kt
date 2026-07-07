package com.adeloc.iptv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adeloc.iptv.data.local.entity.StreamEntity
import com.adeloc.iptv.data.local.entity.StreamType
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<StreamEntity>)

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId")
    fun getStreamsByPlaylist(playlistId: Int): Flow<List<StreamEntity>>

    @Query("DELETE FROM streams WHERE playlistId = :playlistId")
    suspend fun deleteStreamsByPlaylist(playlistId: Int)

    @Query("DELETE FROM streams")
    suspend fun deleteAllStreams()

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' AND streamType = :type")
    suspend fun searchStreamsByType(playlistId: Int, query: String, type: StreamType): List<StreamEntity>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND streamType = :type AND name LIKE '%' || :searchQuery || '%'")
    fun searchStreams(playlistId: Int, type: StreamType, searchQuery: String): Flow<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%'")
    suspend fun searchAllStreams(playlistId: Int, query: String): List<StreamEntity>

    @Query("UPDATE streams SET resumePosition = :position, duration = :duration, lastWatched = :lastWatched WHERE url = :url")
    suspend fun saveProgress(url: String, position: Long, duration: Long, lastWatched: Long)

    @Query("SELECT * FROM streams WHERE resumePosition > 0 AND resumePosition < (duration * 0.95) ORDER BY lastWatched DESC LIMIT 15")
    fun getContinueWatching(): Flow<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE url = :url LIMIT 1")
    suspend fun getStreamByUrl(url: String): StreamEntity?

    @Query("UPDATE streams SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFav: Boolean)

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND isFavorite = 1 AND streamType = :type")
    fun getFavoriteStreamsByType(playlistId: Int, type: StreamType): Flow<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND categoryId = :categoryId AND streamType = :type")
    fun getStreamsByCategory(playlistId: Int, categoryId: String, type: StreamType): Flow<List<StreamEntity>>

    @Query("UPDATE streams SET lastWatched = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: Int, timestamp: Long)

    @Query("UPDATE streams SET lastWatched = :timestamp WHERE streamId = :streamId")
    suspend fun updateLastWatched(streamId: String, timestamp: Long)

    @Query("SELECT * FROM streams WHERE playlistId = :playlistId AND streamType = :type AND lastWatched > 0 ORDER BY lastWatched DESC LIMIT 10")
    fun getRecentlyWatched(playlistId: Int, type: StreamType): Flow<List<StreamEntity>>

    @Query("SELECT id FROM streams WHERE streamId = :apiId AND playlistId = :playlistId AND streamType = 'SERIES' LIMIT 1")
    suspend fun getSeriesIdByApiId(apiId: String, playlistId: Int): Int?
}
