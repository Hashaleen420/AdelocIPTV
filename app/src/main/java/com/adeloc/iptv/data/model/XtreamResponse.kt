package com.adeloc.iptv.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class XtreamResponse(
    @SerializedName("user_info")
    val userInfo: UserInfo?,
    @SerializedName("server_info")
    val serverInfo: ServerInfo?
)

data class UserInfo(
    val username: String?,
    val password: String?,
    val message: String?,
    val auth: Int?,
    val status: String?,
    val exp_date: JsonElement?,
    val is_trial: String?,
    val active_cons: String?,
    val max_connections: String?,
    val rev_res: String?,
    val allowed_output_formats: List<String>?
)

data class ServerInfo(
    val url: String?,
    val port: String?,
    val https_port: String?,
    val server_protocol: String?,
    val rtmp_port: String?,
    val timezone: String?,
    val timestamp: Long?,
    val time_now: String?
)
