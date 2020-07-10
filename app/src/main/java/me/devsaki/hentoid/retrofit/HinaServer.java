package me.devsaki.hentoid.retrofit;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;

import java.util.Date;

import io.reactivex.Single;
import me.devsaki.hentoid.json.hina.HinaResult;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.Field;
import retrofit2.http.GET;

public class HinaServer {

    private static final String GITHUB_BASE_URL = "https://api.ixil.cc/hina/";

    private static final Moshi moshi = new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .build();

    public static final Api API = new Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Api.class);

    public interface Api {

        @GET("")
        Single<HinaResult> getLatest(
                @Field("page") int page,
                @Field("op") int resultsPerPage
        );

        @GET("search")
        Single<HinaResult> search(
                @Field("page") int page,
                @Field("op") int resultsPerPage,
                @Field("query") String query
        );

        @GET("payload")
        Single<HinaResult.HinaGallery> getGallery(
                @Field("id") String id
        );
    }
}
