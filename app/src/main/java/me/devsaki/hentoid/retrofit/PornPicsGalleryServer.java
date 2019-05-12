package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.PornPicsContent;
import me.devsaki.hentoid.util.OkHttpClientSingleton;
import pl.droidsonroids.retrofit2.JspoonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class PornPicsGalleryServer {

    public static final Api API = new Retrofit.Builder()
            .baseUrl(Site.PORNPICS.getUrl())
            .client(OkHttpClientSingleton.getInstance())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(JspoonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("/galleries/{id}/")
        Single<PornPicsContent> getGalleryMetadata(@Path("id") String contentId);
    }
}
