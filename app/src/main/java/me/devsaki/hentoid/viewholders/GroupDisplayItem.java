package me.devsaki.hentoid.viewholders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.drag.IExtendedDraggable;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.mikepenz.fastadapter.swipe.ISwipeable;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.GroupItem;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.ui.BlinkAnimation;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ThemeHelper;

import static androidx.core.view.ViewCompat.requireViewById;
import static me.devsaki.hentoid.util.ImageHelper.tintBitmap;

public class GroupDisplayItem extends AbstractItem<GroupDisplayItem.GroupViewHolder> implements IExtendedDraggable, ISwipeable {

    private static final RequestOptions glideRequestOptions;

    @IntDef({ViewType.LIBRARY, ViewType.LIBRARY_GRID, ViewType.LIBRARY_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {
        int LIBRARY = 0;
        int LIBRARY_GRID = 1;
        int LIBRARY_EDIT = 2;
    }

    // Group
    private final Group group;
    private final @ViewType
    int viewType;
    private final boolean isEmpty;

    // Drag, drop & swipe
    private final ItemTouchHelper touchHelper;
    private int swipeDirection = 0;
    private boolean isSwipeable = true;
    private Runnable undoSwipeAction; // Action to run when hitting the "undo" button


    static {
        Context context = HentoidApp.getInstance();
        int tintColor = ThemeHelper.getColor(context, R.color.light_gray);

        Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_hentoid_trans);
        Drawable d = new BitmapDrawable(context.getResources(), tintBitmap(bmp, tintColor));

        glideRequestOptions = new RequestOptions()
                .centerInside()
                .error(d);
    }

    public GroupDisplayItem(Group group, @Nullable ItemTouchHelper touchHelper, @ViewType int viewType) {
        this.group = group;
        this.viewType = viewType;
        this.touchHelper = touchHelper;
        isEmpty = (null == group);
    }


    public Group getGroup() {
        return group;
    }

    @NotNull
    @Override
    public GroupViewHolder getViewHolder(@NotNull View view) {
        return new GroupViewHolder(view, viewType);
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public ItemTouchHelper getTouchHelper() {
        return touchHelper;
    }

    @Override
    public int getLayoutRes() {
        return (ViewType.LIBRARY_GRID == viewType) ? R.layout.item_library_group_grid : R.layout.item_library_group;
    }

    @Override
    public int getType() {
        return R.id.group;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public View getDragView(@NotNull RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof GroupViewHolder)
            return ((GroupViewHolder) viewHolder).ivReorder;
        else return null;
    }

    @Override
    public boolean isDraggable() {
        return (ViewType.LIBRARY_EDIT == viewType);
    }

    @Override
    public boolean isSwipeable() {
        return isSwipeable;
    }


    static class GroupViewHolder extends FastAdapter.ViewHolder<GroupDisplayItem> {

        private final View baseLayout;
        private final TextView title;
        private ImageView ivCover;
        private View ivReorder;

        GroupViewHolder(View view, @ContentItem.ViewType int viewType) {
            super(view);
            baseLayout = requireViewById(view, R.id.item);
            title = requireViewById(view, R.id.tvTitle);

            if (viewType == ViewType.LIBRARY_EDIT) {
                ivReorder = requireViewById(view, R.id.ivReorder);
            } else { // LIBRARY
                ivCover = requireViewById(view, R.id.ivCover);
            }
        }


        @Override
        public void bindView(@NotNull GroupDisplayItem item, @NotNull List<?> list) {

            baseLayout.setVisibility(item.isEmpty ? View.GONE : View.VISIBLE);
            if (item.getGroup() != null && item.getGroup().isBeingDeleted())
                baseLayout.startAnimation(new BlinkAnimation(500, 250));
            else
                baseLayout.clearAnimation();

            if (ivReorder != null) {
                ivReorder.setVisibility(View.VISIBLE);
                DragDropUtil.bindDragHandle(this, item);
            }

            if (item.group.picture != null && ivCover != null) {
                ImageFile cover = item.group.picture.getTarget();
                if (cover != null) attachCover(cover);
            }
            List<GroupItem> items = item.group.items;
            title.setText(String.format("%s%s", item.group.name, (null == items || items.isEmpty()) ? "" : " (" + items.size() + ")"));
        }

        private void attachCover(@NonNull ImageFile cover) {
            String thumbLocation = "";
            if (cover.getStatus().equals(StatusContent.DOWNLOADED) || cover.getStatus().equals(StatusContent.MIGRATED) || cover.getStatus().equals(StatusContent.EXTERNAL))
                thumbLocation = cover.getFileUri();
            if (thumbLocation.isEmpty()) thumbLocation = cover.getUrl();

            if (thumbLocation.isEmpty()) return;

            ivCover.setVisibility(View.VISIBLE);
            if (thumbLocation.startsWith("http"))
                Glide.with(ivCover)
                        .load(thumbLocation)
                        .apply(glideRequestOptions)
                        .into(ivCover);
            else
                Glide.with(ivCover)
                        .load(Uri.parse(thumbLocation))
                        .apply(glideRequestOptions)
                        .into(ivCover);
        }

        @Override
        public void unbindView(@NotNull GroupDisplayItem item) {
            if (ivCover != null && Helper.isValidContextForGlide(ivCover))
                Glide.with(ivCover).clear(ivCover);
        }
    }
}
