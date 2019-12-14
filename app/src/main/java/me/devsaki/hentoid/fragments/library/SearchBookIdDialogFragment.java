package me.devsaki.hentoid.fragments.library;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.adapters.SiteAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class SearchBookIdDialogFragment extends DialogFragment {

    private static final String ID = "ID";
    private static final String FOUND_SITES = "FOUND_SITES";

    private String bookId;

    public static void invoke(FragmentManager fragmentManager, String id, ArrayList<Integer> siteCodes) {
        Bundle args = new Bundle();
        args.putString(ID, id);
        args.putIntegerArrayList(FOUND_SITES, siteCodes);

        SearchBookIdDialogFragment fragment = new SearchBookIdDialogFragment();
        fragment.setArguments(args);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_library_search_id, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        if (getArguments() != null) {
            bookId = getArguments().getString(ID, "");
            ArrayList<Integer> foundSitesList = getArguments().getIntegerArrayList(FOUND_SITES);

            TextView title = requireViewById(rootView, R.id.search_bookid_title);
            title.setText(getString(R.string.search_bookid_label, bookId));

            // Not possible for Pururin, e-hentai
            List<Site> sites = new ArrayList<>();
            if (foundSitesList != null) {
//            if (!foundSitesList.contains(Site.TSUMINO.getCode())) sites.add(Site.TSUMINO);
            }

            SiteAdapter siteAdapter = new SiteAdapter();
            siteAdapter.setOnClickListener(this::onItemSelected);
            siteAdapter.add(sites);

            RecyclerView sitesRecycler = requireViewById(rootView, R.id.select_sites);
            sitesRecycler.setAdapter(siteAdapter);
        }
    }

    private static String getUrlFromId(Site site, String id) {
        switch (site) {
//            case TSUMINO:
//                return site.getUrl() + "/Book/Info/" + id + "/";
            case LUSCIOUS:
                return site.getUrl().replace("manga", "albums") + id + "/";
            default:
                return site.getUrl();
        }
    }

    private void onItemSelected(View view) {
        Site s = (Site) view.getTag();

        if (s != null) {
            Intent intent = new Intent(requireContext(), Content.getWebActivityClass(s));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
            builder.setUrl(getUrlFromId(s, bookId));
            intent.putExtras(builder.getBundle());

            requireContext().startActivity(intent);
            this.dismiss();
        }
    }
}
