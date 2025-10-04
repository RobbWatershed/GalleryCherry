package me.devsaki.hentoid.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.viewholders.AttributeViewHolder

/**
 * Adapter for the selected attributes list displayed in the advanced search screen
 * <p>
 * Can only be removed when prerequisites are met : see comments in {@link me.devsaki.hentoid.fragments.SearchBottomSheetFragment}
 */
private val DIFF_CALLBACK: DiffUtil.ItemCallback<Attribute> =
    object : DiffUtil.ItemCallback<Attribute>() {
        override fun areItemsTheSame(
            oldAttr: Attribute, newAttr: Attribute
        ): Boolean {
            return oldAttr.id == newAttr.id
        }

        override fun areContentsTheSame(
            oldAttr: Attribute, newAttr: Attribute
        ): Boolean {
            return oldAttr.name == newAttr.name && oldAttr.type == newAttr.type
        }
    }

class SelectedAttributeAdapter : ListAdapter<Attribute, AttributeViewHolder>(DIFF_CALLBACK) {
    private var onClickListener: View.OnClickListener? = null


    fun setOnClickListener(listener: View.OnClickListener?) {
        onClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttributeViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.item_badge_input, parent, false)
        return AttributeViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttributeViewHolder, position: Int) {
        holder.bindTo(getItem(position), false)
        holder.itemView.setOnClickListener(onClickListener)
    }
}