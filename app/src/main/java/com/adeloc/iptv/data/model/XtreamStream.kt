package com.adeloc.iptv.data.model

import com.google.gson.annotations.SerializedName

data class XtreamStream(
    @SerializedName("num")
    val num: Any? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("stream_type")
    val streamType: String? = null,
    @SerializedName("stream_id")
    val streamId: Any? = null,
    @SerializedName("series_id")
    val seriesId: Any? = null,
    @SerializedName("stream_icon")
    val streamIcon: String? = null,
    @SerializedName("epg_channel_id")
    val epgChannelId: String? = null,
    @SerializedName("cover")
    val cover: String? = null,
    @SerializedName("category_id")
    val categoryId: String? = null,
    @SerializedName("added")
    val added: String? = null,
    @SerializedName("custom_sid")
    val customSid: String? = null,
    @SerializedName("tv_archive")
    val tvArchive: Any? = null,
    @SerializedName("direct_source")
    val directSource: String? = null,
    @SerializedName("tv_archive_duration")
    val tvArchiveDuration: Any? = null,
    @SerializedName("thumbnail")
    val thumbnail: String? = null,
    @SerializedName("rating")
    val rating: Any? = null,
    @SerializedName("rating_5based")
    val rating5based: Any? = null,
    @SerializedName("container_extension")
    val containerExtension: String? = null,
    @SerializedName("last_modified")
    val lastModified: Any? = null
)
