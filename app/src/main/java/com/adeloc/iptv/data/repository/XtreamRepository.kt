package com.adeloc.iptv.data.repository

import com.adeloc.iptv.data.model.XtreamResponse
import com.adeloc.iptv.data.model.XtreamSeriesDetail
import com.adeloc.iptv.data.remote.XtreamApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class XtreamRepository {

    private fun createApiService(baseUrl: String): XtreamApiService {
        var url = baseUrl.trim()
        if (!url.startsWith("http")) url = "http://$url"
        url = url.replace("/player_api.php", "")
        if (!url.endsWith("/")) url = "$url/"
        
        return Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XtreamApiService::class.java)
    }

    suspend fun login(baseUrl: String, username: String, password: String): Result<XtreamResponse> {
        return try {
            val apiService = createApiService(baseUrl)
            val response = apiService.login(username, password)
            
            if (response.userInfo?.auth == 1) {
                Result.success(response)
            } else {
                val errorMessage = response.userInfo?.message ?: "Invalid credentials"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesDetail(baseUrl: String, username: String, password: String, seriesId: String): Result<XtreamSeriesDetail> {
        return try {
            val apiService = createApiService(baseUrl)
            val response = apiService.getSeriesInfo(username, password, seriesId = seriesId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
