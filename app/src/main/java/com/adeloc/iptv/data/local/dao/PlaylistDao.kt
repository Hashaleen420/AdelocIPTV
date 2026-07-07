package com.adeloc.iptv.data.local.dao

import androidx.room.*
import com.adeloc.iptv.data.local.entity.LiveChannel
import com.adeloc.iptv.data.local.entity.PlaylistProfile
import com.adeloc.iptv.data.local.entity.StreamCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Upsert
    suspend fun insertPlaylist(playlist: PlaylistProfile): Long

    @Query("SELECT * FROM playlist_profiles")
    fun getAllPlaylists(): Flow<List<PlaylistProfile>>

    @Query("SELECT * FROM playlist_profiles")
    suspend fun getAllPlaylistsSync(): List<PlaylistProfile>

    @Query("SELECT * FROM playlist_profiles ORDER BY id DESC LIMIT 1")
    fun getLatestPlaylist(): Flow<PlaylistProfile?>

    @Query("SELECT * FROM playlist_profiles WHERE id = :id")
    suspend fun getPlaylistById(id: Int): PlaylistProfile?

    @Query("SELECT * FROM playlist_profiles WHERE id = :id")
    fun observePlaylistById(id: Int): Flow<PlaylistProfile?>

    @Query("SELECT * FROM playlist_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActivePlaylist(): Flow<PlaylistProfile?>

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistProfile)

    @Query("DELETE FROM playlist_profiles")
    suspend fun deleteAllPlaylists()

    @Transaction
    suspend fun activatePlaylist(playlistId: Int) {
        deactivateAllPlaylists()
        setPlaylistActive(playlistId)
    }

    @Query("UPDATE playlist_profiles SET isActive = 0")
    suspend fun deactivateAllPlaylists()

    @Query("UPDATE playlist_profiles SET isActive = 1 WHERE id = :playlistId")
    suspend fun setPlaylistActive(playlistId: Int)

    @Query("UPDATE playlist_profiles SET expDate = :expDate WHERE id = :id")
    suspend fun updatePlaylistExpiry(id: Int, expDate: String?)

    @Upsert
    suspend fun insertCategories(categories: List<StreamCategory>)

    @Update
    suspend fun updateCategory(category: StreamCategory)

    @Query("SELECT * FROM stream_categories WHERE playlistId = :playlistId AND type = :type AND isHidden = 0")
    fun getCategoriesByPlaylist(playlistId: Int, type: String): Flow<List<StreamCategory>>

    @Query("SELECT * FROM stream_categories WHERE playlistId = :playlistId AND type = :type")
    fun getAllCategoriesByPlaylist(playlistId: Int, type: String): Flow<List<StreamCategory>>

    @Query("SELECT * FROM stream_categories WHERE playlistId = :playlistId AND type = :type")
    suspend fun getCategoriesByPlaylistSync(playlistId: Int, type: String): List<StreamCategory>

    @Query("DELETE FROM stream_categories WHERE playlistId = :playlistId")
    suspend fun deleteCategoriesByPlaylist(playlistId: Int)

    @Query("DELETE FROM stream_categories")
    suspend fun deleteAllCategories()

    @Upsert
    suspend fun insertChannels(channels: List<LiveChannel>)

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId AND streamId = :streamId")
    fun getChannelByStreamId(playlistId: Int, streamId: String): Flow<LiveChannel?>

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId AND categoryId = :categoryId")
    fun getChannelsByCategory(playlistId: Int, categoryId: String): Flow<List<LiveChannel>>

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId AND isFavorite = 1")
    fun getFavoriteChannels(playlistId: Int): Flow<List<LiveChannel>>

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId")
    fun getAllChannelsByPlaylist(playlistId: Int): Flow<List<LiveChannel>>

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%'")
    fun searchChannelsFlow(playlistId: Int, query: String): Flow<List<LiveChannel>>

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%'")
    suspend fun searchChannels(playlistId: Int, query: String): List<LiveChannel>

    @Query("DELETE FROM live_channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Int)

    @Query("DELETE FROM live_channels")
    suspend fun deleteAllChannels()

    @Query("UPDATE live_channels SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFav: Boolean)

    @Query("UPDATE live_channels SET lastWatched = :timestamp WHERE id = :id")
    suspend fun updateChannelLastWatched(id: Int, timestamp: Long)

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId AND lastWatched > 0 ORDER BY lastWatched DESC LIMIT 10")
    fun getRecentlyWatchedChannels(playlistId: Int): Flow<List<LiveChannel>>
}
