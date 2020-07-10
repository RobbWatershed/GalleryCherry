package me.devsaki.hentoid.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.database.domains.Content;

public class HinaDataSourceFactory extends DataSource.Factory<Integer, Content> {

    HinaDataSource dataSource;
    final MutableLiveData<HinaDataSource> mutableLiveData;

    private final String query;
    private final CompositeDisposable compositeDisposable;

    public HinaDataSourceFactory(
            @NonNull CompositeDisposable compositeDisposable,
            @Nullable String query) {
        mutableLiveData = new MutableLiveData<>();

        this.compositeDisposable = compositeDisposable;

        if (null == query) this.query = "";
        else this.query = query;
    }

    @NonNull
    @Override
    public DataSource<Integer, Content> create() {
        dataSource = new HinaDataSource(compositeDisposable, query);
        mutableLiveData.postValue(dataSource);
        return dataSource;
    }

    public MutableLiveData<HinaDataSource> getMutableLiveData() {
        return mutableLiveData;
    }
}
