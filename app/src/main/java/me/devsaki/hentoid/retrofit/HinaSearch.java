package me.devsaki.hentoid.retrofit;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;

import java.util.Date;

import io.reactivex.Single;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.json.hina.HinaSearchResult;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class HinaSearch {

    private static final Moshi moshi = new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .build();

    public static final Api API = new Retrofit.Builder()
            .baseUrl(BuildConfig.MEILI_ROUTER)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Api.class);

    public interface Api {

        @GET("indexes/hina/search")
        Single<HinaSearchResult> getLatest(
                @Query("offset") int page,
                @Query("limit") int resultsPerPage
        );

        @GET("indexes/hina/search")
        Single<HinaSearchResult> search(
                @Query("offset") int page,
                @Query("limit") int resultsPerPage,
                @Query("q") String query
        );
    }
}
