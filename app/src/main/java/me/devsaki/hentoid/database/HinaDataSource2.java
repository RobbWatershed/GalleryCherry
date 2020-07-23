package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.retrofit.HinaServer;
import timber.log.Timber;

public class HinaDataSource2 extends PositionalDataSource<Content> {

    private final String query;
    private final CompositeDisposable compositeDisposable;
    private int pageSize = 0;

    public HinaDataSource2(@NonNull CompositeDisposable cd, @NonNull final String query) {
        compositeDisposable = cd;
        this.query = query;
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<Content> callback) {
        this.pageSize = params.pageSize;
        Timber.i(">> loadInitial %s", params.requestedStartPosition);
        createItemsObservable((params.requestedStartPosition / pageSize) + 1, params.requestedLoadSize, callback, null);
    }

    @Override
    public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<Content> callback) {
        Timber.i(">> loadRange %s", params.startPosition);
        createItemsObservable((params.startPosition / pageSize) + 1, params.loadSize, null, callback);
    }

    private void createItemsObservable(
            int requestedPage,
            int loadSize,
            @Nullable PositionalDataSource.LoadInitialCallback<Content> initialCallback,
            @Nullable PositionalDataSource.LoadRangeCallback<Content> callback
    ) {
        Timber.i(">> createItemsObservable %s", requestedPage);
        if (!query.isEmpty())
            compositeDisposable.add(HinaServer.API.search(requestedPage, pageSize, query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            r -> {
                                if (initialCallback != null)
                                    initialCallback.onResult(r.getGalleries(), (requestedPage - 1) * pageSize, r.getMaxRes() * pageSize);
                                if (callback != null)
                                    callback.onResult(r.getGalleries());
                            },
                            Timber::e)
            );
        else
            compositeDisposable.add(HinaServer.API.getLatest(requestedPage, pageSize)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            r -> {
                                if (initialCallback != null)
                                    initialCallback.onResult(r.getGalleries(), (requestedPage - 1) * pageSize, r.getMaxRes()* pageSize);
                                if (callback != null)
                                    callback.onResult(r.getGalleries());
                            },
                            Timber::e)
            );
    }

    // === FACTORY

    public static class HinaDataSource2Factory extends androidx.paging.DataSource.Factory<Integer, Content> {
        private final String query;
        private final CompositeDisposable compositeDisposable;

        HinaDataSource2Factory(CompositeDisposable cd) {
            compositeDisposable = cd;
            query = "";
        }

        HinaDataSource2Factory(String query, CompositeDisposable cd) {
            compositeDisposable = cd;
            this.query = query;
        }

        @NonNull
        public DataSource<Integer, Content> create() {
            return new HinaDataSource2(compositeDisposable, query);
        }
    }
}
