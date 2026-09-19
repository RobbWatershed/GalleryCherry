package me.devsaki.hentoid.retrofit.sources

import me.devsaki.hentoid.activities.sources.HiperdexActivity.Companion.DOMAIN_FILTER
import me.devsaki.hentoid.json.sources.hiperdex.HiperChapters
import me.devsaki.hentoid.json.sources.hiperdex.HiperContent
import me.devsaki.hentoid.json.sources.hiperdex.HiperPages
import me.devsaki.hentoid.util.network.OkHttpClientManager
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

object HiperdexServer {
    private const val HIPERDEX_URL = "https://$DOMAIN_FILTER/api/trpc/"

    lateinit var api: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        api = Retrofit.Builder()
            .baseUrl(HIPERDEX_URL)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @GET("series.bySlugWithGenres")
        fun getContent(
            @Header("cookie") cookies: String,
            @Header("referer") referer: String,
            @Query("input") input: String,
            @Query("batch") batch: Int = 1
        ): Call<List<HiperContent>>

        @GET("series.chapters")
        fun getChapters(
            @Header("cookie") cookies: String,
            @Header("referer") referer: String,
            @Query("input") input: String,
            @Query("batch") batch: Int = 1
        ): Call<List<HiperChapters>>

        @GET("reader.chapterPages")
        fun getChapterPages(
            @Header("cookie") cookies: String,
            @Header("referer") referer: String,
            @Query("input") input: String,
            @Header("x-cfg-auth") secret: String = "yceqt7qgu004", // use HeaderMap?
            @Query("batch") batch: Int = 1
        ): Call<List<HiperPages>>
    }
}