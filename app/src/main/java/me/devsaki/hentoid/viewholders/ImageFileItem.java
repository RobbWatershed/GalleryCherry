package me.devsaki.hentoid.viewholders;

import static androidx.core.view.ViewCompat.requireViewById;
import static me.devsaki.hentoid.util.ImageHelper.tintBitmap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IExpandable;
import com.mikepenz.fastadapter.IParentItem;
import com.mikepenz.fastadapter.ISubItem;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImageItemBundle;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.retrofit.HinaDetails;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImageHelper;
import me.devsaki.hentoid.util.ThemeHelper;

public class ImageFileItem extends AbstractItem<ImageFileItem.ImageViewHolder> implements IExpandable<ImageFileItem.ImageViewHolder>, INestedItem<ImageFileItem.ImageViewHolder> {

    @IntDef({ViewType.LIBRARY, ViewType.ONLINE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int LIBRARY = 0;
        int ONLINE = 1;
    }

    private final ImageFile image;
    private final @ViewType
    int viewType;
    private final Chapter chapter;
    private final boolean showChapter;
    private boolean isCurrent;
    private boolean expanded = false;

    private static final RequestOptions glideRequestOptions;


    static {
        Context context = HentoidApp.getInstance();
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);

        Bitmap bmp = ImageHelper.getBitmapFromResource(context, R.drawable.ic_cherry_icon);
        Drawable d = new BitmapDrawable(context.getResources(), tintBitmap(bmp, tintColor));

        glideRequestOptions = new RequestOptions()
                .centerInside()
                .placeholder(d);
    }

    public ImageFileItem(@NonNull ImageFile image, boolean showChapter, @ViewType int viewType) {
        this.image = image;
        this.viewType = viewType;
        if (image.getLinkedChapter() != null)
            this.chapter = image.getLinkedChapter();
        else
            this.chapter = new Chapter(1, "", "Chapter 1"); // Default display when nothing is set
        this.showChapter = showChapter;
        setIdentifier(image.uniqueHash());
    }

    // Return a copy, not the original instance that has to remain in synch with its visual representation
    public ImageFile getImage() {
        return image;
    }

    public void setCurrent(boolean current) {
        this.isCurrent = current;
    }

    public boolean isFavourite() {
        return image.isFavourite();
    }

    public int getChapterOrder() {
        return chapter.getOrder();
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

    @Override
    public boolean isAutoExpanding() {
        return true;
    }

    @Override
    public boolean isExpanded() {
        return expanded;
    }

    @Override
    public void setExpanded(boolean b) {
        expanded = b;
    }

    @NonNull
    @Override
    public List<ISubItem<?>> getSubItems() {
        return Collections.emptyList();
    }

    @Override
    public void setSubItems(@NonNull List<ISubItem<?>> list) {
        // Nothing
    }

    @Nullable
    @Override
    public IParentItem<?> getParent() {
        return null;
    }

    @Override
    public void setParent(@Nullable IParentItem<?> iParentItem) {
        // Nothing
    }

    @Override
    public int getLevel() {
        return 1;
    }


    public static class ImageViewHolder extends FastAdapter.ViewHolder<ImageFileItem> {

        private static final String HEART_SYMBOL = "❤";

        private final TextView pageNumberTxt;
        private final ImageView image;
        private final ImageView checkedIndicator;
        private final @ViewType
        int viewType;
        private final TextView chapterOverlay;

        ImageViewHolder(@NonNull View view, @ViewType int viewType) {
            super(view);
            pageNumberTxt = requireViewById(view, R.id.viewer_gallery_pagenumber_text);
            image = requireViewById(view, R.id.viewer_gallery_image);
            checkedIndicator = requireViewById(view, R.id.checked_indicator);
            this.viewType = viewType;
            chapterOverlay = requireViewById(view, R.id.chapter_overlay);
        }


        @Override
        public void bindView(@NotNull ImageFileItem item, @NotNull List<?> payloads) {

            // Payloads are set when the content stays the same but some properties alone change
            if (!payloads.isEmpty()) {
                Bundle bundle = (Bundle) payloads.get(0);
                ImageItemBundle.Parser bundleParser = new ImageItemBundle.Parser(bundle);

                Boolean boolValue = bundleParser.isFavourite();
                if (boolValue != null) item.image.setFavourite(boolValue);

                Integer intValue = bundleParser.getChapterOrder();
                if (intValue != null) item.chapter.setOrder(intValue);
            }

            updateText(item);

            // Checkmark
            if (item.isSelected()) checkedIndicator.setVisibility(View.VISIBLE);
            else checkedIndicator.setVisibility(View.GONE);

            String uri = item.image.getFileUri();
            // Hack to display thumbs when retrieving online images from Hina
            if (item.image.getDownloadParams().contains("hina-id"))
                uri = HinaDetails.getThumbFor(item.image.getUrl());

            // Chapter overlay
            if (item.showChapter) {
                String chapterText = String.format(Locale.ENGLISH, "Chp %d", item.chapter.getOrder());
                if (item.chapter.getOrder() == Integer.MAX_VALUE)
                    chapterText = ""; // Don't show temp values
                chapterOverlay.setText(chapterText);
                chapterOverlay.setBackgroundColor(
                        chapterOverlay.getResources().getColor(
                                (0 == item.chapter.getOrder() % 2) ? R.color.black_opacity_50 : R.color.white_opacity_25
                        )
                );
                chapterOverlay.setVisibility(View.VISIBLE);
            } else chapterOverlay.setVisibility(View.GONE);

            // Image
            Glide.with(image)
                    .load(uri)
                    .signature(new ObjectKey(item.image.uniqueHash()))
                    .apply(glideRequestOptions)
                    .into(image);
        }

        private void updateText(@NotNull ImageFileItem item) {
            String currentBegin = item.isCurrent ? ">" : "";
            String currentEnd = item.isCurrent ? "<" : "";
            String isFavourite = item.isFavourite() ? HEART_SYMBOL : "";
            pageNumberTxt.setText(String.format("%sPage %s%s%s", currentBegin, item.image.getOrder(), isFavourite, currentEnd));
            if (item.isCurrent) pageNumberTxt.setTypeface(null, Typeface.BOLD);
        }

        @Override
        public void unbindView(@NotNull ImageFileItem item) {
            if (image != null && Helper.isValidContextForGlide(image))
                Glide.with(image).clear(image);
        }
    }
}
