package com.adeloc.iptv.domain.usecase

import com.adeloc.iptv.data.local.dao.EpgDao
import com.adeloc.iptv.util.EpgParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.URL

class SyncEpgUseCase(private val epgDao: EpgDao) {

    fun execute(playlistId: Int, epgUrl: String): Flow<String> = flow {
        emit("Downloading EPG...")
        
        try {
            val inputStream = URL(epgUrl).openStream()
            
            emit("Parsing programs...")
            val programs = EpgParser.parseXmltv(inputStream, playlistId) { count ->
                emit("Parsed $count programs...")
            }

            emit("Saving to database...")
            // Clear old EPG for this playlist
            epgDao.deleteEpgByPlaylist(playlistId)
            
            // Batch insert in chunks of 1000
            programs.chunked(1000).forEach { chunk ->
                epgDao.insertPrograms(chunk)
            }

            emit("Sync complete: ${programs.size} programs.")
        } catch (e: Exception) {
            emit("Sync failed: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
