package me.devsaki.hentoid.retrofit.sources

import me.devsaki.hentoid.activities.sources.PAW_DOMAIN_FILTER
import me.devsaki.hentoid.json.sources.pawchive.PawArtist
import me.devsaki.hentoid.json.sources.pawchive.PawPost
import me.devsaki.hentoid.util.network.OkHttpClientManager
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

object PawServer {
    private const val PAW_URL = "https://$PAW_DOMAIN_FILTER/api/v1/"

    lateinit var api: Api

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        api = Retrofit.Builder()
            .baseUrl(PAW_URL)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @GET("{service}/user/{creator_id}/profile")
        fun getArtist(
            @Path("service") service: String,
            @Path("creator_id") userId: String,
            @Header("cookie") cookies: String,
            @Header("accept") accept: String,
            @Header("user-agent") userAgent: String
        ): Call<PawArtist>

        @GET("{service}/user/{creator_id}")
        fun getArtistGalleries(
            @Path("service") service: String,
            @Path("creator_id") userId: String,
            @Header("cookie") cookies: String,
            @Header("accept") accept: String,
            @Header("user-agent") userAgent: String,
            @Query("o") offset: Int = 0
        ): Call<List<PawPost>>

        @GET("{service}/user/{creator_id}/post/{post_id}")
        fun getGallery(
            @Path("service") service: String,
            @Path("creator_id") userId: String,
            @Path("post_id") id: String,
            @Header("cookie") cookies: String,
            @Header("accept") accept: String,
            @Header("user-agent") userAgent: String
        ): Call<PawPost>
    }
}