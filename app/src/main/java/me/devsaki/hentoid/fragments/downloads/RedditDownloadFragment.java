package me.devsaki.hentoid.fragments.downloads;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import me.devsaki.hentoid.R;

public class RedditDownloadFragment extends Fragment {

    static RedditDownloadFragment newInstance() {
        RedditDownloadFragment f = new RedditDownloadFragment();

        Bundle args = new Bundle();
        f.setArguments(args);

        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.fragment_reddit_download, container, false);
    }

}
