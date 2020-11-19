package me.devsaki.hentoid.retrofit;

import androidx.annotation.NonNull;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter;

import java.util.Date;

import io.reactivex.Single;
import me.devsaki.hentoid.json.hina.HinaResult;
import me.devsaki.hentoid.util.network.OkHttpClientSingleton;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public class HinaServer {

    public static final String HINA_RAPIDAPI_HOST = "bloom-hina.p.rapidapi.com";

    private static final String HINA_BASE_URL = "https://bloom-hina.p.rapidapi.com/";
    private static final String HINA_THUMBS_URL = "https://proxy.ixil.cc/ren?width=75&height=100&method=cover&image=%s";

    private static final Moshi moshi = new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .build();

    public static final Api API = new Retrofit.Builder()
            .baseUrl(HINA_BASE_URL)
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(Api.class);

    public static String getThumbFor(@NonNull final String uri) {
        return String.format(HINA_THUMBS_URL, uri);
    }

    public interface Api {

        @GET("hina")
        Single<HinaResult> getLatest(
                @Query("page") int page,
                @Query("op") int resultsPerPage,
                @Header("x-rapidapi-key") String rapidApiKey,
                @Header("x-rapidapi-host") String rapidApiHost
        );

        @GET("search")
        Single<HinaResult> search(
                @Query("page") int page,
                @Query("op") int resultsPerPage,
                @Query("query") String query,
                @Header("x-rapidapi-key") String rapidApiKey,
                @Header("x-rapidapi-host") String rapidApiHost
        );

        @GET("hina/payload")
        Single<HinaResult.HinaGallery> getGallery(
                @Query("id") String id,
                @Header("x-rapidapi-key") String rapidApiKey,
                @Header("x-rapidapi-host") String rapidApiHost
        );
    }
}
