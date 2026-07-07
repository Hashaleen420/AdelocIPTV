package com.adeloc.iptv.data.model

import com.google.gson.annotations.SerializedName

data class XtreamSeriesDetail(
    val info: SeriesInfo?,
    val episodes: Map<String, List<Episode>>?
)

data class SeriesInfo(
    val name: String?,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    @SerializedName("last_modified") val lastModified: String?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?
)

data class Episode(
    val id: String?,
    @SerializedName("episode_num") val episodeNum: String?,
    val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    val info: EpisodeInfo?,
    @SerializedName("custom_sid") val customSid: String?,
    @SerializedName("added") val added: String?,
    @SerializedName("season") val season: Int?,
    @SerializedName("direct_source") val directSource: String?
)

data class EpisodeInfo(
    val name: String?,
    val plot: String?,
    val duration: String?,
    @SerializedName("movie_image") val movieImage: String?,
    @SerializedName("bitrate") val bitrate: String?,
    @SerializedName("rating") val rating: String?
)
