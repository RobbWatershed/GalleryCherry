package me.devsaki.hentoid.retrofit.sources

import me.devsaki.hentoid.json.sources.luscious.LusciousBookMetadata
import me.devsaki.hentoid.json.sources.luscious.LusciousGalleryMetadata
import me.devsaki.hentoid.util.network.OkHttpClientManager
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.QueryMap

object LusciousServer {
    private const val API_URL = "https://members.luscious.net/"

    lateinit var api: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        api = Retrofit.Builder()
            .baseUrl(API_URL)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @GET("graphql/nobatch/")
        fun getBookMetadata(@QueryMap options: Map<String, String>): Call<LusciousBookMetadata>

        @GET("graphql/nobatch/")
        fun getGalleryMetadata(@QueryMap options: Map<String, String>): Call<LusciousGalleryMetadata>
    }
}