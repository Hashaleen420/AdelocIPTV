package com.adeloc.iptv.data.local.dao

import androidx.room.*
import com.adeloc.iptv.data.local.entity.EpgProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("SELECT * FROM epg_programs_new WHERE playlistId = :playlistId AND channelId = :channelId AND endTime > :currentTime ORDER BY startTime ASC")
    fun getEpgForChannel(playlistId: Int, channelId: String, currentTime: Long): Flow<List<EpgProgramEntity>>

    @Query("SELECT * FROM epg_programs_new WHERE playlistId = :playlistId AND TRIM(LOWER(channelId)) = TRIM(LOWER(:channelId))")
    suspend fun getProgramsForChannel(playlistId: Int, channelId: String): List<EpgProgramEntity>
    
    @Query("SELECT * FROM epg_programs_new WHERE playlistId = :playlistId AND TRIM(LOWER(channelId)) = TRIM(LOWER(:channelName))")
    suspend fun getEpgForChannelByName(playlistId: Int, channelName: String): List<EpgProgramEntity>

    @Query("SELECT * FROM epg_programs_new WHERE playlistId = :playlistId AND channelId = :channelId AND startTime <= :currentTime AND endTime >= :currentTime LIMIT 1")
    suspend fun getCurrentProgram(playlistId: Int, channelId: String, currentTime: Long): EpgProgramEntity?

    @Query("DELETE FROM epg_programs_new")
    suspend fun deleteAllEpg()

    @Query("DELETE FROM epg_programs_new WHERE playlistId = :playlistId")
    suspend fun deleteEpgByPlaylist(playlistId: Int)

    @Query("DELETE FROM epg_programs_new WHERE endTime < :currentTime")
    suspend fun deleteExpiredPrograms(currentTime: Long)
}
