package me.devsaki.hentoid.retrofit

import me.devsaki.hentoid.json.oauth2.Oauth2AccessToken
import me.devsaki.hentoid.util.network.OkHttpClientManager.getInstance
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

object RedditPublicApiServer {
    private const val REDDIT_API_URL: String = "https://www.reddit.com/api/v1/"

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
        @FormUrlEncoded
        @POST("access_token")
        fun getAccessToken(
            @Field("code") code: String,
            @Field("redirect_uri") redirectUri: String,
            @Field("grant_type") grantType: String,
            @Header("Authorization") authorization: String
        ): Call<Oauth2AccessToken>
    }
}