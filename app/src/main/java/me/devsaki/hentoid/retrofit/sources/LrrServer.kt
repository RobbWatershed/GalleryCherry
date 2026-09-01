package me.devsaki.hentoid.retrofit.sources

import me.devsaki.hentoid.json.sources.lrr.LrrArchives
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.network.OkHttpClientManager
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.QueryMap

// Lanraragi
object LrrServer {
    lateinit var api: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        api = Retrofit.Builder()
            .baseUrl("${Settings.lrrEndpoint}/api/")
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
    }
}