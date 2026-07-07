package com.adeloc.iptv.data.repository

import com.adeloc.iptv.data.local.dao.StreamDao
import com.adeloc.iptv.data.local.entity.StreamEntity
import com.adeloc.iptv.data.local.entity.StreamType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class GlobalSearchResult(
    val channels: List<StreamEntity>,
    val movies: List<StreamEntity>,
    val series: List<StreamEntity>
)

class SearchRepository(private val streamDao: StreamDao) {

    suspend fun searchAll(playlistId: Int, query: String): GlobalSearchResult = coroutineScope {
        val channels = async { streamDao.searchStreamsByType(playlistId, query, StreamType.LIVE) }
        val movies = async { streamDao.searchStreamsByType(playlistId, query, StreamType.VOD) }
        val series = async { streamDao.searchStreamsByType(playlistId, query, StreamType.SERIES) }

        GlobalSearchResult(
            channels = channels.await(),
            movies = movies.await(),
            series = series.await()
        )
    }

    suspend fun searchLiveOnly(playlistId: Int, query: String): List<StreamEntity> {
        return streamDao.searchStreamsByType(playlistId, query, StreamType.LIVE)
    }

    suspend fun searchVodOnly(playlistId: Int, query: String): List<StreamEntity> {
        return streamDao.searchStreamsByType(playlistId, query, StreamType.VOD)
    }
}
