package me.devsaki.hentoid.fragments.downloads;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.SelectableAdapter;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.LandingRecord;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.viewholders.TextItemFlex;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 09/2019
 * Launcher dialog for landing page history
 */
public class LandingHistoryFragment extends Fragment {

    private static final String SITE = "SITE";
    private static final String LANDING_HISTORY = "LANDING_HISTORY";
    private static final String DEFAULT_URL = "DEFAULT_URL";

    private Site site;

    private Parent callbackActivity;
    private FlexibleAdapter<TextItemFlex> adapter;
    private EditText input;


    static LandingHistoryFragment newInstance(Site site, String defaultUrl) {
        LandingHistoryFragment f = new LandingHistoryFragment();

        ObjectBoxDB db = ObjectBoxDB.getInstance(HentoidApp.getAppContext());
        List<LandingRecord> landingHistory = db.selectLandingRecords(site);

        ArrayList<String> urlHistory = new ArrayList<>();
        if (landingHistory != null)
            for (LandingRecord r : landingHistory) urlHistory.add(r.url);

        Bundle args = new Bundle();
        args.putStringArrayList(LANDING_HISTORY, urlHistory);
        args.putLong(SITE, site.getCode());
        args.putString(DEFAULT_URL, defaultUrl);

        f.setArguments(args);

        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        try {
            callbackActivity = (Parent) getActivity();
        } catch (ClassCastException e) {
            Timber.e(e, "Calling Activity doesn't implement the Parent interface");
        }

        return inflater.inflate(R.layout.fragment_landing_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            String defaultUrl = getArguments().getString(DEFAULT_URL);

            long siteCode = getArguments().getLong(SITE);
            site = Site.searchByCode(siteCode);

            List<String> foundSitesList = getArguments().getStringArrayList(LANDING_HISTORY);
            if (null == foundSitesList)
                throw new IllegalArgumentException("Landing history not found");

            List<TextItemFlex> items = Stream.of(foundSitesList)
                    .map(TextItemFlex::new)
                    .toList();

            // Add default page if empty
            if (items.isEmpty()) items.add(new TextItemFlex(defaultUrl));

            adapter = new FlexibleAdapter<>(null);
            adapter.setMode(SelectableAdapter.Mode.SINGLE);
            adapter.addListener((FlexibleAdapter.OnItemClickListener) this::onItemClick);
            adapter.addItems(0, items);

            RecyclerView recyclerView = requireViewById(view, R.id.landing_history_list);
            recyclerView.setAdapter(adapter);

            View okBtn = requireViewById(view, R.id.landing_history_ok);
            okBtn.setOnClickListener(this::onOkClick);

            input = requireViewById(view, R.id.landing_history_input);
            input.setText(defaultUrl);
        }
    }

    private void onOkClick(View view) {
        String url = input.getText().toString().trim();
        // Remove spaces added around /'s by dumb phone keyboards
        url = url.replace(" /", "/").replace("/ ", "/");

        recordUrlInDb(url);
        launchWebActivity(url);
    }

    private boolean onItemClick(View view, int position) {
        TextItemFlex item = adapter.getItem(position);
        if (null == item) return false;

        recordUrlInDb(item.getCaption());
        launchWebActivity(item.getCaption());
        return true;
    }

    private void recordUrlInDb(@NonNull String relativeUrl) {
        if (null == getActivity()) return;

        ObjectBoxDB db = ObjectBoxDB.getInstance(getActivity());
        LandingRecord record = db.selectLandingRecord(site, relativeUrl);
        if (null == record) record = new LandingRecord(site, relativeUrl);
        record.lastAccessDate = new Date().getTime();
        db.insertLandingRecord(record);
    }

    private void launchWebActivity(@NonNull String relativeUrl) {
        if (null == getActivity()) return;

        String completeUrl = site.getUrl();
        if (!completeUrl.endsWith("/") && !relativeUrl.startsWith("/")) completeUrl += "/";
        completeUrl += relativeUrl;

        callbackActivity.goToUrl(completeUrl);
    }

    public interface Parent {
        void goToUrl(String url);
    }
}
