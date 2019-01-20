package me.devsaki.hentoid.retrofit;

import io.reactivex.Single;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.content.XnxxContent;
import pl.droidsonroids.retrofit2.JspoonConverterFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

public class XnxxGalleryServer {

    public static final Api API = new Retrofit.Builder()
            .baseUrl(Site.XNXX.getUrl())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
            .addConverterFactory(JspoonConverterFactory.create())
            .build()
            .create(Api.class);

    public interface Api {

        @GET("/gallery/{id}/{hash}/{name}/{page}/")
        Single<XnxxContent> getGalleryMetadata(@Path("id") String contentId, @Path("hash") String hash, @Path("name") String contentName, @Path("page") String contentPage);
    }
}
