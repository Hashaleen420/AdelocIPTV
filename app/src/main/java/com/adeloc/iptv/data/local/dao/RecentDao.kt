package com.adeloc.iptv.data.local.dao

import androidx.room.*
import com.adeloc.iptv.data.local.entity.RecentEntity
import com.adeloc.iptv.data.local.entity.StreamEntity
import com.adeloc.iptv.data.local.entity.StreamType
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentEntity)

    @Query("SELECT * FROM recents WHERE playlistId = :playlistId AND streamType = :type ORDER BY lastWatched DESC")
    fun getRecentlyWatched(playlistId: Int, type: StreamType): Flow<List<RecentEntity>>

    @Query("SELECT * FROM recents WHERE playlistId = :playlistId AND streamId = :streamId LIMIT 1")
    suspend fun getRecentByStreamId(playlistId: Int, streamId: String): RecentEntity?

    @Query("DELETE FROM recents WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun removeRecent(playlistId: Int, streamId: String)
    
    @Query("UPDATE recents SET resumePosition = :position, duration = :duration, lastWatched = :lastWatched WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun updateProgress(playlistId: Int, streamId: String, position: Long, duration: Long, lastWatched: Long)

    @Query("DELETE FROM recents WHERE playlistId = :playlistId")
    suspend fun deleteAllRecentsByPlaylist(playlistId: Int)

    @Query("""
        SELECT s.* FROM streams s
        INNER JOIN recents r ON s.streamId = r.streamId AND s.playlistId = r.playlistId
        WHERE r.playlistId = :playlistId AND r.streamType = :type
        ORDER BY r.lastWatched DESC
    """)
    fun getRecentlyWatchedStreams(playlistId: Int, type: StreamType): Flow<List<StreamEntity>>
}
