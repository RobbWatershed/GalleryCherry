package me.devsaki.hentoid.fragments.viewer;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class GoToPageDialogFragment extends DialogFragment {

    private Parent parent;

    public static void invoke(Fragment parent) {
        GoToPageDialogFragment fragment = new GoToPageDialogFragment();
        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parent = (Parent) getParentFragment();
    }

    @Override
    public void onDestroy() {
        parent = null;
        super.onDestroy();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(Configuration.KEYBOARD_12KEY);

        DialogInterface.OnClickListener positive = (dialog, whichButton) -> {
            if (input.getText().length() > 0)
                parent.goToPage(Integer.parseInt(input.getText().toString()));
        };

        AlertDialog materialDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(input)
                .setPositiveButton(android.R.string.ok, positive)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        materialDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        return materialDialog;
    }

    public interface Parent {
        void goToPage(int pageNum);
    }
}
