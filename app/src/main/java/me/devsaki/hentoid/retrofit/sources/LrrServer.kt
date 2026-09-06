package me.devsaki.hentoid.retrofit.sources

import me.devsaki.hentoid.json.sources.lrr.LrrArchives
import me.devsaki.hentoid.json.sources.lrr.LrrCategories
import me.devsaki.hentoid.json.sources.lrr.LrrExtraction
import me.devsaki.hentoid.json.sources.lrr.LrrServerInfo
import me.devsaki.hentoid.json.sources.lrr.LrrSuccess
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.network.OkHttpClientManager
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap

// Lanraragi

// Yes, the LRR favorites category does have unicode emoji in its name
const val LRR_FAV_CAT = "\uD83D\uDD16 Favorites"

object LrrServer {
    lateinit var api: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        val endpoint = "${Settings.lrrEndpoint}/api/"
        api = Retrofit.Builder()
            .baseUrl(endpoint)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @GET("search")
        fun search(
            @QueryMap options: Map<String, String>,
            @Header("Authorization") apiKey: String
        ): Call<LrrArchives>

        @GET("archives/{id}/files")
        fun extract(@Path("id") archiveId: String): Call<LrrExtraction>

        @GET("archives/{id}/metadata")
        fun getArchive(@Path("id") archiveId: String): Call<LrrArchives.LrrArchive>

        @GET("archives/{id}/categories")
        fun getArchiveCategories(@Path("id") archiveId: String): Call<LrrCategories>

        @GET("categories")
        fun getAllCategories(): Call<List<LrrCategories.LrrCategory>>

        @GET("categories/{id}")
        fun getCategory(@Path("id") catId: String): Call<LrrCategories.LrrCategory>

        @PUT("categories/{id}/{archive}")
        fun addToCategory(
            @Path("id") catId: String,
            @Path("archive") archiveId: String,
            @Header("Authorization") apiKey: String
        ): Call<LrrSuccess>

        @DELETE("categories/{id}/{archive}")
        fun removeFromCategory(
            @Path("id") catId: String,
            @Path("archive") archiveId: String,
            @Header("Authorization") apiKey: String
        ): Call<LrrSuccess>

        @GET("info")
        fun info(): Call<LrrServerInfo>
    }
}