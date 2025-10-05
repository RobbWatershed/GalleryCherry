package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.json.sources.RedditUser;
import me.devsaki.hentoid.json.sources.RedditUserSavedPosts;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public class RedditOAuthApiServer {

    private static final String REDDIT_API_URL = "https://oauth.reddit.com/";

    public static final Api API = new Retrofit.Builder()
            .baseUrl(REDDIT_API_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("api/v1/me")
        Single<RedditUser> getUser(
                @Header("Authorization") String authorization
        );

        @GET("user/{username}/saved")
        Single<RedditUserSavedPosts> getUserSavedPosts(
                @Path("username") String username,
                @Header("Authorization") String authorization
        );
    }
}
