package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PageKeyedDataSource;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.json.hina.HinaResult;
import me.devsaki.hentoid.retrofit.HinaServer;
import timber.log.Timber;

public class HinaDataSource extends PageKeyedDataSource<Integer, Content> {

    private static final int ITEMS_PER_PAGE = 10; // TODO

    private final CompositeDisposable compositeDisposable;
    private final String query;

    public HinaDataSource(@NonNull CompositeDisposable cd) {
        compositeDisposable = cd;
        this.query = "";
    }

    public HinaDataSource(@NonNull CompositeDisposable cd, @NonNull final String query) {
        compositeDisposable = cd;
        this.query = query;
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams<Integer> params, @NonNull LoadInitialCallback<Integer, Content> callback) {
        createObservable(1, 2, callback, null);
    }

    @Override
    public void loadBefore(@NonNull LoadParams<Integer> params, @NonNull LoadCallback<Integer, Content> callback) {
        int page = params.key;
        createObservable(page, page + 1, null, callback);
    }

    @Override
    public void loadAfter(@NonNull LoadParams<Integer> params, @NonNull LoadCallback<Integer, Content> callback) {
        int page = params.key;
        createObservable(page, page - 1, null, callback);
    }

    private void createObservable(
            int requestedPage,
            int adjacentPage,
            @Nullable LoadInitialCallback<Integer, Content> initialCallback,
            @Nullable LoadCallback<Integer, Content> callback
    ) {
        if (!query.isEmpty())
            compositeDisposable.add(HinaServer.API.search(requestedPage, ITEMS_PER_PAGE, query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            r -> {
                                if (initialCallback != null)
                                    initialCallback.onResult(r.getGalleries(), null, adjacentPage);
                                if (callback != null)
                                    callback.onResult(r.getGalleries(), adjacentPage);
                            },
                            Timber::e)
            );
        else
            compositeDisposable.add(HinaServer.API.getLatest(requestedPage, ITEMS_PER_PAGE)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            r -> {
                                if (initialCallback != null)
                                    initialCallback.onResult(r.getGalleries(), null, adjacentPage);
                                if (callback != null)
                                    callback.onResult(r.getGalleries(), adjacentPage);
                            },
                            Timber::e)
            );
    }
}
