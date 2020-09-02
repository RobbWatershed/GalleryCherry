package me.devsaki.hentoid.viewholders;

import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.retrofit.HinaServer;
import me.devsaki.hentoid.util.ContentHelper;

import static androidx.core.view.ViewCompat.requireViewById;

public class ImageFileItem extends AbstractItem<ImageFileItem.ImageViewHolder> {

    @IntDef({ViewType.LIBRARY, ViewType.ONLINE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int LIBRARY = 0;
        int ONLINE = 1;
    }

    private final ImageFile image;
    private final @ViewType
    int viewType;
    private boolean isCurrent;
    private static final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    public ImageFileItem(@NonNull ImageFile image, @ViewType int viewType) {
        this.image = image;
        this.viewType = viewType;
        setIdentifier(image.hashCode());
    }

    public ImageFile getImage() {
        return image;
    }

    public void setCurrent(boolean current) {
        this.isCurrent = current;
    }

    public boolean isFavourite() {
        return image.isFavourite();
    }


    @NotNull
    @Override
    public ImageViewHolder getViewHolder(@NotNull View view) {
        return new ImageViewHolder(view, viewType);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_viewer_gallery_image;
    }

    @Override
    public int getType() {
        return R.id.gallery_image;
    }


    public static class ImageViewHolder extends FastAdapter.ViewHolder<ImageFileItem> {

        private final TextView pageNumberTxt;
        private final ImageView image;
        private final ImageButton favouriteBtn;
        private final @ViewType
        int viewType;

        ImageViewHolder(@NonNull View view, @ViewType int viewType) {
            super(view);
            pageNumberTxt = requireViewById(view, R.id.viewer_gallery_pagenumber_text);
            image = requireViewById(view, R.id.viewer_gallery_image);
            favouriteBtn = requireViewById(view, R.id.viewer_gallery_favourite_btn);
            this.viewType = viewType;
        }


        @Override
        public void bindView(@NotNull ImageFileItem item, @NotNull List<?> list) {
            if (ViewType.LIBRARY == viewType) {
                String currentBegin = item.isCurrent ? ">" : "";
                String currentEnd = item.isCurrent ? "<" : "";
                pageNumberTxt.setText(String.format("%sPage %s%s", currentBegin, item.image.getOrder(), currentEnd));
                if (item.isCurrent) pageNumberTxt.setTypeface(null, Typeface.BOLD);
                updateFavourite(item.isFavourite());
            } else {
                pageNumberTxt.setVisibility(View.GONE);
                favouriteBtn.setVisibility(View.GONE);
            }

            String uri = item.image.getFileUri();
            // Hack to display thumbs when retrieving online images from Hina
            if (item.image.getDownloadParams().contains("hina-id")) {
                Map<String, String> downloadParams = ContentHelper.parseDownloadParams(item.image.getDownloadParams());
                uri = HinaServer.getThumbFor(downloadParams.get("hina-id"), item.image.getOrder() - 1);
            }

            Glide.with(image)
                    .load(Uri.parse(uri))
                    .apply(glideRequestOptions)
                    .into(image);
        }

        void updateFavourite(boolean isFavourite) {
            if (isFavourite) {
                favouriteBtn.setImageResource(R.drawable.ic_fav_full);
            } else {
                favouriteBtn.setImageResource(R.drawable.ic_fav_empty);
            }
        }

        public View getFavouriteButton() {
            return favouriteBtn;
        }

        @Override
        public void unbindView(@NotNull ImageFileItem item) {
            // Unload resources & cancel any pending load
            Glide.with(image).clear(image);
        }
    }
}
