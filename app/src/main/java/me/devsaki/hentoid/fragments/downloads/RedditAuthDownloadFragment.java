package me.devsaki.hentoid.fragments.downloads;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import me.devsaki.hentoid.R;

public class RedditAuthDownloadFragment extends Fragment {

    static RedditAuthDownloadFragment newInstance() {
        RedditAuthDownloadFragment f = new RedditAuthDownloadFragment();

        Bundle args = new Bundle();
        f.setArguments(args);

        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.fragment_reddit_download_auth, container, false);
    }

}
