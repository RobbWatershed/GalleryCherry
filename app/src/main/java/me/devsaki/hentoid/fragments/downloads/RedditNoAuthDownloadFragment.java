package me.devsaki.hentoid.fragments.downloads;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.OauthSessionManager;

import static androidx.core.view.ViewCompat.requireViewById;

public class RedditNoAuthDownloadFragment extends Fragment {

    private static final String AUTH_URL =
            "https://www.reddit.com/api/v1/authorize.compact?client_id=%s"
                    + "&response_type=code"
                    + "&state=%s"
                    + "&redirect_uri=%s"
                    + "&duration=permanent"
                    + "&scope=%s";

    private static final String CLIENT_ID = "SYu446X8u5hTTQ";

    private static final String REDIRECT_URI = "https://github.com/RobbWatershed/GalleryCherry";

    private static final String SCOPE = "history identity";


    static RedditNoAuthDownloadFragment newInstance() {
        RedditNoAuthDownloadFragment f = new RedditNoAuthDownloadFragment();

        Bundle args = new Bundle();
        f.setArguments(args);

        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.fragment_reddit_download_noauth, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View button = requireViewById(view, R.id.reddit_noauth_action);
        button.setOnClickListener(v -> onNoAuthClick());
    }

    private void onNoAuthClick()
    {
        OauthSessionManager.OauthSession session = OauthSessionManager.getInstance().addSession(Site.REDDIT);
        session.setState(Double.toString(Math.random()));
        session.setClientId(CLIENT_ID);
        session.setRedirectUri(REDIRECT_URI);

        String authUrl = String.format(AUTH_URL, CLIENT_ID, session.getState(), REDIRECT_URI, SCOPE);
        //Helper.openUrl(requireActivity(), authUrl); // TODO try opening that with Hentoid browser

        Content content = new Content();
        content.setSite(Site.REDDIT);
        content.setUrl(authUrl);
        ContentHelper.viewContentGalleryPage(requireContext(), content, true);
    }
}
