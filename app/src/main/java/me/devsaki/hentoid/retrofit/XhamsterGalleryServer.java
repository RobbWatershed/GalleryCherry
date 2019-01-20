package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.XhamsterContent;
import pl.droidsonroids.retrofit2.JspoonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class XhamsterGalleryServer {

    public static final Api API = new Retrofit.Builder()
            .baseUrl(Site.XHAMSTER.getUrl())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(JspoonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("gallery/{id}/{page}")
        Single<XhamsterContent> getGalleryMetadata(@Path("id") String contentId, @Path("page") String page);
    }
}
