package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class WikiSearchResponse(
    val query: WikiQuery? = null
)

@JsonClass(generateAdapter = true)
data class WikiQuery(
    val pages: Map<String, WikiPage>? = null
)

@JsonClass(generateAdapter = true)
data class WikiPage(
    val title: String,
    val extract: String? = null
)

interface WikipediaService {
    @GET("w/api.php?action=query&format=json&prop=extracts&exintro&explaintext&redirects=1")
    suspend fun getSummary(
        @Query("titles") titles: String
    ): WikiSearchResponse
}

object WikipediaClient {
    val service: WikipediaService by lazy {
        Retrofit.Builder()
            .baseUrl("https://en.wikipedia.org/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(WikipediaService::class.java)
    }
}
