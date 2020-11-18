package me.devsaki.hentoid.fragments.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.retrofit.HinaServer;
import me.devsaki.hentoid.viewholders.ImageFileItem;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 07/2020
 * Info dialog for download errors details
 */
public class GalleryDialogFragment extends DialogFragment {

    private static final String ID = "ID";

    private final ItemAdapter<ImageFileItem> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<ImageFileItem> fastAdapter = FastAdapter.with(itemAdapter);

    private Parent parent;
    private Content content;
    private Disposable disposable;

    public static void invoke(FragmentActivity parent, String id) {
        GalleryDialogFragment fragment = new GalleryDialogFragment();

        Bundle args = new Bundle();
        args.putString(ID, id);
        fragment.setArguments(args);

        fragment.show(parent.getSupportFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parent = (Parent) getActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_library_gallery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        String id = getArguments().getString(ID, "");
        if (id.isEmpty()) throw new IllegalArgumentException("No ID found");

        RecyclerView recyclerView = requireViewById(view, R.id.library_gallery_recycler);
        recyclerView.setAdapter(fastAdapter);
        new FastScrollerBuilder(recyclerView).build();

        Toolbar toolbar = requireViewById(view, R.id.library_gallery_toolbar);
        toolbar.setNavigationOnClickListener(v -> dismiss());

        toolbar.setOnMenuItemClickListener(clickedMenuItem -> {
            if (clickedMenuItem.getItemId() == R.id.action_download) {
                download();
            }
            return true;
        });

        disposable = HinaServer.API.getGallery(id,
                BuildConfig.RAPIDAPI_KEY,
                HinaServer.HINA_RAPIDAPI_HOST)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        g -> loadGallery(g.toContent()),
                        Timber::e);
    }

    private void loadGallery(@NonNull final Content content) {
        disposable.dispose();

        if (null == content.getImageFiles()) return;

        this.content = content;
        FastAdapterDiffUtil.INSTANCE.set(itemAdapter,
                Stream.of(content.getImageFiles())
                        .filterNot(i -> i.getName().equalsIgnoreCase("thumb"))
                        .map(i -> new ImageFileItem(i, ImageFileItem.ViewType.ONLINE))
                        .toList()
        );
    }

    private void download() {
        if (null == content) return;

        parent.downloadContent(content);
        dismiss();
    }

    public interface Parent {
        void downloadContent(final Content content);
    }
}
