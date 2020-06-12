package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.json.oauth2.Oauth2AccessToken;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Header;
import retrofit2.http.POST;

public class RedditPublicApiServer {

    private static final String REDDIT_API_URL = "https://www.reddit.com/api/v1/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(REDDIT_API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @FormUrlEncoded
        @POST("access_token")
        Single<Oauth2AccessToken> getAccessToken(
                @Field("code") String code,
                @Field("redirect_uri") String redirectUri,
                @Field("grant_type") String grantType,
                @Header("Authorization") String authorization
        );
    }
}
