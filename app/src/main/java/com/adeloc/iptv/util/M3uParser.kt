package com.adeloc.iptv.util

import com.adeloc.iptv.data.local.entity.StreamEntity
import com.adeloc.iptv.data.local.entity.StreamType
import com.adeloc.iptv.data.local.entity.guessStreamType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {

    private val regexTvgId = Regex("""tvg-id="([^"]*)"""")
    private val regexTvgLogo = Regex("""tvg-logo="([^"]*)"""")
    private val regexGroupTitle = Regex("""group-title="([^"]*)"""")

    suspend fun parseLineByLine(
        inputStream: InputStream,
        playlistId: Int,
        onProgress: suspend (Int) -> Unit
    ): List<StreamEntity> {
        val streams = mutableListOf<StreamEntity>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        var count = 0

        var currentExtInf: String? = null

        try {
            while (reader.readLine().also { line = it } != null) {
                val trimmedLine = line?.trim() ?: continue

                if (trimmedLine.startsWith("#EXTINF:")) {
                    currentExtInf = trimmedLine
                } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                    if (currentExtInf != null) {
                        val stream = parseStream(currentExtInf, trimmedLine, playlistId)
                        streams.add(stream)
                        currentExtInf = null
                        count++
                        if (count % 100 == 0) {
                            onProgress(count)
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }
        return streams
    }

    private fun parseStream(extInf: String, url: String, playlistId: Int): StreamEntity {
        val tvgId = regexTvgId.find(extInf)?.groupValues?.get(1)
        val tvgLogo = regexTvgLogo.find(extInf)?.groupValues?.get(1)
        val groupTitle = regexGroupTitle.find(extInf)?.groupValues?.get(1)
        
        // Extract name: text after the last comma
        val name = extInf.substringAfterLast(",").trim()

        return StreamEntity(
            playlistId = playlistId,
            categoryId = null,
            streamId = tvgId,
            name = if (name.isNotEmpty()) name else "Unknown Channel",
            logoUrl = tvgLogo,
            groupTitle = groupTitle,
            url = url,
            isLive = guessStreamType(url) == StreamType.LIVE,
            streamType = guessStreamType(url)
        )
    }
}
