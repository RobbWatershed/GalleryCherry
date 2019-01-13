package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.HellpornoContent;
import pl.droidsonroids.retrofit2.JspoonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class HellpornoGalleryServer {

    public static final Api API = new Retrofit.Builder()
            .baseUrl("https://hellporno.com/") // Parse desktop site (not mobile site) because it contains tag information
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(JspoonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("/albums/{id}/")
        Single<HellpornoContent> getGalleryMetadata(@Path("id") String contentId);
    }
}
