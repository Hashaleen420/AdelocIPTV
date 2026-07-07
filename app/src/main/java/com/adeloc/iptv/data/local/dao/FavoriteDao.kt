package com.adeloc.iptv.data.local.dao

import androidx.room.*
import com.adeloc.iptv.data.local.entity.FavoriteEntity
import com.adeloc.iptv.data.local.entity.StreamEntity
import com.adeloc.iptv.data.local.entity.StreamType
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun removeFavorite(playlistId: Int, streamId: String)

    @Query("SELECT * FROM favorites WHERE playlistId = :playlistId")
    fun getFavorites(playlistId: Int): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND streamId = :streamId)")
    fun isFavorite(playlistId: Int, streamId: String): Flow<Boolean>

    @Query("SELECT streamId FROM favorites WHERE playlistId = :playlistId AND streamType = :type")
    fun getFavoriteStreamIds(playlistId: Int, type: StreamType): Flow<List<String>>

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId")
    suspend fun deleteAllFavoritesByPlaylist(playlistId: Int)

    @Query("""
        SELECT s.* FROM streams s
        INNER JOIN favorites f ON s.streamId = f.streamId AND s.playlistId = f.playlistId
        WHERE f.playlistId = :playlistId AND f.streamType = :type
    """)
    fun getFavoriteStreams(playlistId: Int, type: StreamType): Flow<List<StreamEntity>>
}
