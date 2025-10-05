package me.devsaki.hentoid.retrofit

import me.devsaki.hentoid.json.sources.RedditUser
import me.devsaki.hentoid.json.sources.RedditUserSavedPosts
import me.devsaki.hentoid.util.network.OkHttpClientManager.getInstance
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

object RedditOAuthApiServer {
    private const val REDDIT_API_URL: String = "https://oauth.reddit.com/"

    lateinit var API: Api

    fun init() {
        API = Retrofit.Builder()
            .baseUrl(REDDIT_API_URL)
            .client(getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    interface Api {
        @GET("api/v1/me")
        fun getUser(
            @Header("Authorization") authorization: String
        ): Call<RedditUser>

        @GET("user/{username}/saved")
        fun getUserSavedPosts(
            @Path("username") username: String,
            @Header("Authorization") authorization: String
        ): Call<RedditUserSavedPosts>
    }
}