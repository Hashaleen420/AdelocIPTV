package com.adeloc.iptv.data.local

import androidx.room.TypeConverter
import com.adeloc.iptv.data.local.entity.StreamType

class Converters {
    @TypeConverter
    fun fromStreamType(value: StreamType): String {
        return value.name
    }

    @TypeConverter
    fun toStreamType(value: String): StreamType {
        return StreamType.valueOf(value)
    }
}
