package me.devsaki.hentoid.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.viewholders.AttributeViewHolder;

public class SelectedAttributeAdapter extends ListAdapter<Attribute, AttributeViewHolder> {

    private View.OnClickListener onClickListener = null;


    public SelectedAttributeAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnClickListener(View.OnClickListener listener) {
        this.onClickListener = listener;
    }

    @NonNull
    @Override
    public AttributeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chip_choice, parent, false);
        return new AttributeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttributeViewHolder holder, int position) {
        holder.bindTo(getItem(position), true);
        holder.itemView.setOnClickListener(onClickListener);
    }

    private static final DiffUtil.ItemCallback<Attribute> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Attribute>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull Attribute oldAttr, @NonNull Attribute newAttr) {
                    return oldAttr.getId() == newAttr.getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull Attribute oldAttr, @NonNull Attribute newAttr) {
                    return oldAttr.getName().equals(newAttr.getName()) && oldAttr.getType().equals(newAttr.getType());
                }
            };
}
