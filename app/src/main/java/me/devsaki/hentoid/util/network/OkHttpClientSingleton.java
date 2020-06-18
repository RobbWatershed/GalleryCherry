package me.devsaki.hentoid.util.network;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.util.Consts;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Manages a single instance of OkHttpClient per timeout delay
 */
@SuppressWarnings("squid:S3077")
// https://stackoverflow.com/questions/11639746/what-is-the-point-of-making-the-singleton-instance-volatile-while-using-double-l
public class OkHttpClientSingleton {

    private static volatile Map<String, OkHttpClient> instance = new Hashtable<>();

    private OkHttpClientSingleton() {
    }

    public static OkHttpClient getInstance() {
        int DEFAULT_TIMEOUT = 20 * 1000;
        return getInstance(DEFAULT_TIMEOUT);
    }

    public static OkHttpClient getInstance(int timeoutMs) {
        return getInstance(timeoutMs, OkHttpClientSingleton::rewriteUserAgentInterceptor);
    }

    public static OkHttpClient getInstance(int timeoutMs, Interceptor interceptor) {
        if (null == OkHttpClientSingleton.instance.get(timeoutMs + "" + interceptor.toString())) {
            synchronized (OkHttpClientSingleton.class) {
                if (null == OkHttpClientSingleton.instance.get(timeoutMs + "" + interceptor.toString())) {

                    int CACHE_SIZE = 2 * 1024 * 1024; // 2 MB

                    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                            .addInterceptor(interceptor)
                            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .cache(new Cache(HentoidApp.getInstance().getCacheDir(), CACHE_SIZE));


                    OkHttpClientSingleton.instance.put(timeoutMs + "" + interceptor.toString(), clientBuilder.build());
                }
            }
        }
        return OkHttpClientSingleton.instance.get(timeoutMs + "" + interceptor.toString());
    }

    private static okhttp3.Response rewriteUserAgentInterceptor(Interceptor.Chain chain) throws IOException {
        Request request = chain.request()
                .newBuilder()
                .header("User-Agent", Consts.USER_AGENT_NEUTRAL)
                .build();
        return chain.proceed(request);
    }
}
