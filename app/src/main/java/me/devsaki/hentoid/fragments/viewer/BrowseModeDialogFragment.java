package me.devsaki.hentoid.fragments.viewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

import static androidx.core.view.ViewCompat.requireViewById;

public class BrowseModeDialogFragment extends DialogFragment {

    public static void invoke(Fragment parent) {
        BrowseModeDialogFragment fragment = new BrowseModeDialogFragment();
        fragment.setCancelable(false);
        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_viewer_browse_mode_chooser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        View ltrButton = requireViewById(rootView, R.id.chooseHorizontalLtr);
        ltrButton.setOnClickListener(v -> chooseBrowseMode(Preferences.Constant.PREF_VIEWER_BROWSE_LTR));

        View rtlButton = requireViewById(rootView, R.id.chooseHorizontalRtl);
        rtlButton.setOnClickListener(v -> chooseBrowseMode(Preferences.Constant.PREF_VIEWER_BROWSE_RTL));

        View verticalButton = requireViewById(rootView, R.id.chooseVertical);
        verticalButton.setOnClickListener(v -> chooseBrowseMode(Preferences.Constant.PREF_VIEWER_BROWSE_TTB));
    }

    private void chooseBrowseMode(int browseMode) {
        Preferences.setViewerBrowseMode(browseMode);
        getParent().onBrowseModeChange();
        dismiss();
    }

    private Parent getParent() {
        return (Parent) getParentFragment();
    }

    public interface Parent {
        void onBrowseModeChange();
    }
}
