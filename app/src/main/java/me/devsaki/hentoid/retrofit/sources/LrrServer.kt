package me.devsaki.hentoid.retrofit.sources

import android.util.Base64
import me.devsaki.hentoid.json.sources.lrr.LrrArchives
import me.devsaki.hentoid.json.sources.lrr.LrrCategories
import me.devsaki.hentoid.json.sources.lrr.LrrExtraction
import me.devsaki.hentoid.json.sources.lrr.LrrServerInfo
import me.devsaki.hentoid.json.sources.lrr.LrrSuccess
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.encode64
import me.devsaki.hentoid.util.network.OkHttpClientManager
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.QueryMap
import timber.log.Timber

// Lanraragi

// Yes, the LRR favorites category does have unicode emoji in its name
const val LRR_FAV_CAT = "\uD83D\uDD16 Favorites"

object LrrServer {
    lateinit var api: Api
    private lateinit var endpoint : String
    val lrrCategoryIdCache: MutableMap<String, String> = LinkedHashMap()

    init {
        init()
    }

    // Must have a public init method to reset the connexion pool when updating DoH settings
    fun init() {
        endpoint = Settings.lrrEndpoint
        if (endpoint.startsWith("http")) endpoint += "/api/"
        else endpoint = "http://bogus.com/api/" // Make certain init doesn't crash

        api = Retrofit.Builder()
            .baseUrl(endpoint)
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(MoshiConverterFactory.create().asLenient())
            .build()
            .create(Api::class.java)
    }

    fun getDlLink(arcId : String) : String {
        return "${endpoint}archives/$arcId/download"
    }

    interface Api {
        @GET("search")
        fun search(
            @QueryMap options: Map<String, String>,
            @Header("Authorization") apiKey: String
        ): Call<LrrArchives>

        @GET("archives/{id}/files")
        fun extract(@Path("id") archiveId: String): Call<LrrExtraction>

        @GET("archives/{id}/metadata")
        fun getArchive(@Path("id") archiveId: String): Call<LrrArchives.LrrArchive>

        @GET("archives/{id}/categories")
        fun getArchiveCategories(@Path("id") archiveId: String): Call<LrrCategories>

        @GET("categories")
        fun getAllCategories(): Call<List<LrrCategories.LrrCategory>>

        @GET("categories/{id}")
        fun getCategory(@Path("id") catId: String): Call<LrrCategories.LrrCategory>

        @PUT("categories/{id}/{archive}")
        fun addToCategory(
            @Path("id") catId: String,
            @Path("archive") archiveId: String,
            @Header("Authorization") apiKey: String
        ): Call<LrrSuccess>

        @DELETE("categories/{id}/{archive}")
        fun removeFromCategory(
            @Path("id") catId: String,
            @Path("archive") archiveId: String,
            @Header("Authorization") apiKey: String
        ): Call<LrrSuccess>

        @Multipart
        @PUT("categories")
        fun createCategory(
            @Part("name") name: RequestBody,
            @Header("Authorization") apiKey: String
        ): Call<LrrSuccess>

        @Multipart
        @PUT("archives/upload")
        fun uploadArchive(
            @Part file: MultipartBody.Part,
            @Part("category_id") categoryId: RequestBody,
            @Part("tags") tags: RequestBody,
            @Part("title") title: RequestBody,
            @Header("Authorization") apiKey: String
        ): Call<LrrSuccess>

        @GET("info")
        fun info(): Call<LrrServerInfo>
    }

    fun formatApiKey(): String {
        return "Bearer ${encode64(Settings.lrrApiKey, Base64.NO_WRAP)}"
    }

    fun getLrrCategoryId(name: String): String {
        if (lrrCategoryIdCache.containsKey(name)) return lrrCategoryIdCache[name]!!

        val catCall = api.getAllCategories()
        catCall.execute().let { response ->
            if (response.isSuccessful) {
                response.body()?.let { rb ->
                    rb.firstOrNull { it.name == name }?.let {
                        lrrCategoryIdCache[name] = it.id
                        return it.id
                    }
                }
            } else {
                Timber.w("LRR server failed when querying CategoryId @ ${Settings.lrrEndpoint}")
            }
        }
        return ""
    }
}