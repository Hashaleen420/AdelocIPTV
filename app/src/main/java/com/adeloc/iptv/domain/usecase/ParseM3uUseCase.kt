package com.adeloc.iptv.domain.usecase

import com.adeloc.iptv.data.local.dao.StreamDao
import com.adeloc.iptv.util.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.URL

class ParseM3uUseCase(private val streamDao: StreamDao) {

    /**
     * Parses an M3U playlist from a URL and saves it to the database.
     * Emits the number of channels parsed as progress.
     */
    fun execute(playlistId: Int, m3uUrl: String): Flow<Int> = flow {
        // 1. Clear old streams for this playlist
        streamDao.deleteStreamsByPlaylist(playlistId)

        // 2. Open stream and parse
        val inputStream = URL(m3uUrl).openStream()
        
        val streams = M3uParser.parseLineByLine(inputStream, playlistId) { count ->
            // Emit progress every 100 channels
            emit(count)
        }

        // 3. Batch insert into Room
        // For extremely large lists, we chunk the insertion to avoid Binder transaction limits
        streams.chunked(500).forEach { chunk ->
            streamDao.insertStreams(chunk)
        }

        // Emit final count
        emit(streams.size)
    }.flowOn(Dispatchers.IO)
}
