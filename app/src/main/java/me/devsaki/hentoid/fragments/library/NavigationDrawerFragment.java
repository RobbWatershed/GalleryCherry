package me.devsaki.hentoid.fragments.library;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.activities.DrawerEditActivity;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.json.UpdateInfo;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewholders.DrawerItem;

import static androidx.core.view.ViewCompat.requireViewById;

public final class NavigationDrawerFragment extends Fragment {

    private LibraryActivity parentActivity;

    private final ItemAdapter<DrawerItem> drawerAdapter = new ItemAdapter<>();
    private final FastAdapter<DrawerItem> fastAdapter = FastAdapter.with(drawerAdapter);

    private UpdateEvent updateInfo;


    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> onSharedPreferenceChanged(k);


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        parentActivity = (LibraryActivity) context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_navigation_drawer, container, false);

        View btn = requireViewById(rootView, R.id.drawer_prefs_btn);
        btn.setOnClickListener(v -> onPrefsClick());

        btn = requireViewById(rootView, R.id.drawer_edit_btn);
        btn.setOnClickListener(v -> onEditClick());

        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(p));
        RecyclerView recyclerView = requireViewById(rootView, R.id.drawer_list);
        recyclerView.setAdapter(fastAdapter);

        updateItems();

        Preferences.registerPrefsChangedListener(prefsListener);

        return rootView;
    }

    private void updateItems() {
        List<DrawerItem> drawerItems = new ArrayList<>();

        List<Site> activeSites = Preferences.getActiveSites();
        for (Site s : activeSites) drawerItems.add(new DrawerItem(s));

        drawerItems.add(new DrawerItem("QUEUE", R.drawable.ic_action_download, QueueActivity.class));
        drawerItems.add(new DrawerItem("ABOUT", R.drawable.ic_info, AboutActivity.class));

        drawerAdapter.clear();
        drawerAdapter.add(0, drawerItems);
        applyFlagsAndAlerts();
    }

    private boolean onItemClick(int position) {
        DrawerItem item = drawerAdapter.getAdapterItem(position);
        launchActivity(item.getActivityClass());
        return true;
    }

    private void launchActivity(@NonNull Class activityClass) {
        Intent intent = new Intent(parentActivity, activityClass);
        Bundle bundle = ActivityOptionsCompat
                .makeCustomAnimation(parentActivity, R.anim.fade_in, R.anim.fade_out)
                .toBundle();
        ContextCompat.startActivity(parentActivity, intent, bundle);

        parentActivity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        parentActivity.closeNavigationDrawer();
    }

    private void showFlagAboutItem() {
        // About is always last
        int aboutItemPos = drawerAdapter.getAdapterItemCount() - 1;
        if (aboutItemPos > -1) {
            DrawerItem item = drawerAdapter.getAdapterItem(aboutItemPos);
            item.setFlagNew(true);
            fastAdapter.notifyItemChanged(aboutItemPos);
        }
    }

    private void showFlagAlerts(Map<Site, UpdateInfo.SourceAlert> alerts) {
        List<DrawerItem> menuItems = drawerAdapter.getAdapterItems();
        int index = 0;
        for (DrawerItem menuItem : menuItems) {
            if (menuItem.getSite() != null && alerts.containsKey(menuItem.getSite())) {
                UpdateInfo.SourceAlert alert = alerts.get(menuItem.getSite());
                if (alert != null) {
                    menuItem.setAlertStatus(alert.getStatus());
                    fastAdapter.notifyItemChanged(index);
                }
            }
            index++;
        }
    }

    private void applyFlagsAndAlerts() {
        if (null == updateInfo) return;

        // Display the "new update available" flag
        if (updateInfo.hasNewVersion) showFlagAboutItem();

        // Display the site alert flags, if any
        if (!updateInfo.sourceAlerts.isEmpty()) showFlagAlerts(updateInfo.sourceAlerts);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateEvent(UpdateEvent event) {
        updateInfo = event;
        applyFlagsAndAlerts();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        Preferences.unregisterPrefsChangedListener(prefsListener);
    }

    private void onPrefsClick() {
        launchActivity(PrefsActivity.class);
    }

    private void onEditClick() {
        launchActivity(DrawerEditActivity.class);
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(String key) {
        if (Preferences.Key.ACTIVE_SITES.equals(key)) updateItems();
    }
}
