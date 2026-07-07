package com.adeloc.iptv.util

import android.util.Xml
import com.adeloc.iptv.data.local.entity.EpgProgramEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

object EpgParser {

    private val xmltvFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    suspend fun parseXmltv(
        inputStream: InputStream,
        playlistId: Int,
        onProgress: suspend (Int) -> Unit
    ): List<EpgProgramEntity> {
        val programs = mutableListOf<EpgProgramEntity>()
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentProgram: EpgProgramEntity? = null
        var currentTag: String? = null
        var count = 0

        try {
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = name
                        if (name == "programme") {
                            val channelId = parser.getAttributeValue(null, "channel")
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            
                            currentProgram = EpgProgramEntity(
                                playlistId = playlistId,
                                channelId = channelId ?: "",
                                title = "",
                                description = null,
                                startTime = parseXmltvDate(startStr),
                                endTime = parseXmltvDate(stopStr)
                            )
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (currentProgram != null) {
                            val text = parser.text.trim()
                            if (text.isNotEmpty()) {
                                when (currentTag) {
                                    "title" -> currentProgram = currentProgram!!.copy(title = text)
                                    "desc" -> currentProgram = currentProgram!!.copy(description = text)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "programme" && currentProgram != null) {
                            programs.add(currentProgram!!)
                            currentProgram = null
                            count++
                            if (count % 1000 == 0) {
                                onProgress(count)
                            }
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }
        } finally {
            inputStream.close()
        }
        return programs
    }

    private fun parseXmltvDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            xmltvFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
