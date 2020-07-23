package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.domains.Content;

public class HinaDataSourceFactory extends DataSource.Factory<Integer, Content> {

    private final String query;
    private final CompositeDisposable compositeDisposable;

    public HinaDataSourceFactory(
            @NonNull CompositeDisposable compositeDisposable) {
        this.compositeDisposable = compositeDisposable;
        this.query = "";
    }

    public HinaDataSourceFactory(
            @NonNull CompositeDisposable compositeDisposable,
            @NonNull String query) {
        this.compositeDisposable = compositeDisposable;
        this.query = query;
    }

    @NonNull
    @Override
    public DataSource<Integer, Content> create() {
        /*
        HinaDataSource dataSource = new HinaDataSource(compositeDisposable, query);
        mutableLiveData.postValue(dataSource);
        return dataSource;
         */
        return new HinaDataSource(compositeDisposable, query);
    }
}
