package com.adeloc.iptv.data.model

import com.google.gson.annotations.SerializedName

data class XtreamEpgResponse(
    @SerializedName("epg_listings")
    val epgListings: List<EpgListing>?
)

data class EpgListing(
    val id: String?,
    @SerializedName("epg_id")
    val epgId: String?,
    val title: String?,
    val description: String?,
    @SerializedName("start")
    val start: String?,
    @SerializedName("end")
    val end: String?,
    @SerializedName("start_timestamp")
    val startTimestamp: String?,
    @SerializedName("stop_timestamp")
    val stopTimestamp: String?,
    @SerializedName("now_playing")
    val nowPlaying: Int?,
    @SerializedName("has_archive")
    val hasArchive: Int?
)
