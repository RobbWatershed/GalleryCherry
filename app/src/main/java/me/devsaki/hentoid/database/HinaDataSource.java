package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.retrofit.HinaServer;
import timber.log.Timber;

public class HinaDataSource extends PositionalDataSource<Content> {

    private final String query;
    private final CompositeDisposable compositeDisposable;
    private int pageSize = 0;

    public HinaDataSource(@NonNull CompositeDisposable cd, @NonNull final String query) {
        compositeDisposable = cd;
        this.query = query;
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
                                int totalCount = (r.getMaxRes() + 1) * pageSize;
                                int dataSize = r.getGalleries().size();
                                if (position + dataSize != totalCount && dataSize % pageSize != 0) totalCount = position + dataSize;

                                if (initialCallback != null)
                                    initialCallback.onResult(r.getGalleries(), position, totalCount);
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
                                    initialCallback.onResult(r.getGalleries(), (requestedPage - 1) * pageSize, (r.getMaxRes() + 1) * pageSize);
                                if (callback != null)
                                    callback.onResult(r.getGalleries());
                            },
                            Timber::e)
            );
    }

    public LiveData<Integer> count() {
        MutableLiveData<Integer> result = new MutableLiveData<>();
        if (!query.isEmpty())
            compositeDisposable.add(HinaServer.API.search(1, 1, query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            r -> result.postValue(r.getMaxRes() + 1),
                            Timber::e)
            );
        else
            compositeDisposable.add(HinaServer.API.getLatest(1, 1)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            r -> result.postValue(r.getMaxRes() + 1),
                            Timber::e)
            );
        return result;
    }

    // === FACTORY

    public static class HinaDataSource2Factory extends androidx.paging.DataSource.Factory<Integer, Content> {
        private final String query;
        private final CompositeDisposable compositeDisposable;

        HinaDataSource2Factory(CompositeDisposable cd) {
            compositeDisposable = cd;
            query = "";
        }

        HinaDataSource2Factory(CompositeDisposable cd, String query) {
            compositeDisposable = cd;
            this.query = query;
        }

        @NonNull
        public DataSource<Integer, Content> create() {
            return new HinaDataSource(compositeDisposable, query);
        }
    }
}
