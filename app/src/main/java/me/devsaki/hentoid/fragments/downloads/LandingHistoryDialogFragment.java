package me.devsaki.hentoid.fragments.downloads;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.SelectableAdapter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.LandingRecord;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.viewholders.TextItemFlex;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 09/2019
 * Launcher dialog for landing page history
 */
public class LandingHistoryDialogFragment extends DialogFragment {

    private static final String SITE = "SITE";
    private static final String LANDING_HISTORY = "LANDING_HISTORY";

    private Site site;
    private FlexibleAdapter<TextItemFlex> adapter;
    private EditText input;


    public static void invoke(FragmentManager fragmentManager, Site site, Context context) {
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        List<LandingRecord> landingHistory = db.selectLandingRecords(site);

        ArrayList<String> urlHistory = new ArrayList<>();
        if (landingHistory != null)
            for (LandingRecord r : landingHistory) urlHistory.add(r.url);

        Bundle args = new Bundle();
        args.putStringArrayList(LANDING_HISTORY, urlHistory);
        args.putLong(SITE, site.getCode());

        LandingHistoryDialogFragment fragment = new LandingHistoryDialogFragment();
        fragment.setArguments(args);
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_landing_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            long siteCode = getArguments().getLong(SITE);
            site = Site.searchByCode(siteCode);

            List<String> foundSitesList = getArguments().getStringArrayList(LANDING_HISTORY);
            if (null == foundSitesList)
                throw new IllegalArgumentException("Landing history not found");

            List<TextItemFlex> items = Stream.of(foundSitesList)
                    .map(TextItemFlex::new)
                    .toList();

            adapter = new FlexibleAdapter<>(null);
            adapter.setMode(SelectableAdapter.Mode.SINGLE);
            adapter.addListener((FlexibleAdapter.OnItemClickListener) this::onItemClick);
            adapter.addItems(0, items);

            RecyclerView recyclerView = requireViewById(view, R.id.landing_history_list);
            recyclerView.setAdapter(adapter);

            View okBtn = requireViewById(view, R.id.landing_history_ok);
            okBtn.setOnClickListener(this::onOkClick);

            input = requireViewById(view, R.id.landing_history_input);
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

    private void recordUrlInDb(@NonNull String url)
    {
        if (null == getActivity()) return;

        ObjectBoxDB db = ObjectBoxDB.getInstance(getActivity());
        LandingRecord record = db.selectLandingRecord(site, url);
        if (null == record) record = new LandingRecord(site, url);
        record.lastAccessDate = new Date().getTime();
        db.insertLandingRecord(record);
    }

    private void launchWebActivity(@NonNull String url) {
        if (null == getActivity()) return;

        Content content = new Content();
        content.setSite(Site.REDDIT);
        content.setUrl(url);
        ContentHelper.viewContent(getActivity(), content, true);
        this.dismiss();
    }
}
