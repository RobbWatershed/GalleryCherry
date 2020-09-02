package me.devsaki.hentoid.database;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import com.annimon.stream.function.Consumer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.retrofit.HinaServer;
import timber.log.Timber;

public class HinaDataSource extends PositionalDataSource<Content> {

    @IntDef({Status.SUCCESS, Status.SUCCESS_EMPTY, Status.ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int SUCCESS = 0;
        int SUCCESS_EMPTY = 1;
        int ERROR = 2;
    }

    private final String query;
    private final CompositeDisposable compositeDisposable;
    private final Consumer<Integer> completionCallback;
    private final Consumer<List<Content>> interceptor;
    private int pageSize = 0;

    public HinaDataSource(
            @NonNull CompositeDisposable cd,
            @NonNull final String query,
            @NonNull Consumer<Integer> completionCallback,
            @Nullable Consumer<List<Content>> interceptor) {
        compositeDisposable = cd;
        this.query = query;
        this.completionCallback = completionCallback;
        this.interceptor = interceptor;
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<Content> callback) {
        this.pageSize = params.pageSize;
        createItemsObservable((params.requestedStartPosition / pageSize) + 1, params.requestedLoadSize, callback, null);
    }

    @Override
    public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<Content> callback) {
        createItemsObservable((params.startPosition / pageSize) + 1, params.loadSize, null, callback);
    }

    private void createItemsObservable(
            int requestedPage,
            int loadSize,
            @Nullable PositionalDataSource.LoadInitialCallback<Content> initialCallback,
            @Nullable PositionalDataSource.LoadRangeCallback<Content> callback
    ) {
        if (!query.isEmpty())
            compositeDisposable.add(HinaServer.API.search(requestedPage, pageSize, query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            r -> {
                                int position = (requestedPage - 1) * pageSize;

                                List<Content> contents = r.getGalleries();
                                if (interceptor != null) interceptor.accept(contents);
                                if (initialCallback != null)
                                    initialCallback.onResult(contents, position, r.getMaxAlbums());
                                if (callback != null)
                                    callback.onResult(contents);

                                completionCallback.accept(contents.isEmpty() ? Status.SUCCESS_EMPTY : Status.SUCCESS);
                            },
                            e -> {
                                Timber.e(e);
                                completionCallback.accept(Status.ERROR);
                            }
                    )
            );
        else
            compositeDisposable.add(HinaServer.API.getLatest(requestedPage, pageSize)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            r -> {
                                List<Content> contents = r.getGalleries();
                                if (interceptor != null) interceptor.accept(contents);
                                if (initialCallback != null)
                                    initialCallback.onResult(contents, (requestedPage - 1) * pageSize, r.getMaxAlbums());
                                if (callback != null)
                                    callback.onResult(contents);

                                completionCallback.accept(contents.isEmpty() ? Status.SUCCESS_EMPTY : Status.SUCCESS);
                            },
                            e -> {
                                Timber.e(e);
                                completionCallback.accept(Status.ERROR);
                            })
            );
    }

    // === FACTORY

    public static class HinaDataSourceFactory extends androidx.paging.DataSource.Factory<Integer, Content> {
        private final String query;
        private final CompositeDisposable compositeDisposable;
        private final Consumer<Integer> completionCallback;
        private final Consumer<List<Content>> interceptor;

        HinaDataSourceFactory(
                CompositeDisposable cd,
                @NonNull Consumer<Integer> completionCallback,
                @Nullable Consumer<List<Content>> interceptor
        ) {
            compositeDisposable = cd;
            this.completionCallback = completionCallback;
            query = "";
            this.interceptor = interceptor;
        }

        HinaDataSourceFactory(
                CompositeDisposable cd,
                @NonNull String query,
                @NonNull Consumer<Integer> completionCallback,
                @Nullable Consumer<List<Content>> interceptor
        ) {
            compositeDisposable = cd;
            this.completionCallback = completionCallback;
            this.query = query;
            this.interceptor = interceptor;
        }

        @NonNull
        public DataSource<Integer, Content> create() {
            return new HinaDataSource(compositeDisposable, query, completionCallback, interceptor);
        }
    }
}
