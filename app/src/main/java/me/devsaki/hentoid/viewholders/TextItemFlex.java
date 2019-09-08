package me.devsaki.hentoid.viewholders;

import android.view.View;
import android.widget.TextView;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;
import me.devsaki.hentoid.R;

public class TextItemFlex extends AbstractFlexibleItem<TextItemFlex.TextItemViewHolder> {

    private final String caption;

    public TextItemFlex(String caption) {
        this.caption = caption;
    }

    public String getCaption() { return this.caption; }


    @Override
    public boolean equals(Object o) {
        if (o instanceof TextItemFlex) {
            TextItemFlex inItem = (TextItemFlex) o;
            return this.caption.equals(inItem.caption);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return caption.hashCode();
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_text;
    }

    @Override
    public TextItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new TextItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, TextItemViewHolder holder, int position, List<Object> payloads) {
        holder.setContent(caption);
    }

    class TextItemViewHolder extends FlexibleViewHolder {

        private final TextView text;

        TextItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            text = view.findViewById(R.id.item_txt);
        }

        void setContent(String caption) {
            text.setText(caption);
        }
    }
}
