package me.devsaki.hentoid.views

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.skomlach.common.blur.BlurUtil.getActivity
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.WidgetListPickerBinding
import me.devsaki.hentoid.util.getIdForCurrentTheme


class ListPickerView : ConstraintLayout {
    private val binding = WidgetListPickerBinding.inflate(LayoutInflater.from(context), this, true)

    private var onIndexChangeListener: ((Int) -> Unit)? = null
    private var onValueChangeListener: ((String) -> Unit)? = null

    var entries: List<String> = emptyList()
        set(value) {
            field = value.toList()
            index = 0
        }

    var values: List<String> = emptyList()
        set(value) {
            field = value.toList()
        }

    var index: Int = -1
        set(value) {
            selectIndex(value)
            field = value
        }

    var value: String
        set(value) {
            index = values.indexOf(value)
        }
        get() {
            return if (index > -1 && index < values.size) values[index]
            else ""
        }

    var title: String = ""
        set(value) {
            field = value
            binding.title.isVisible = value.isNotEmpty()
            binding.title.text = value
        }


    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int)
            : super(context, attrs, defStyle) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        val arr = context.obtainStyledAttributes(attrs, R.styleable.ListPickerView)
        try {
            val rawTitle = arr.getString(R.styleable.ListPickerView_title)
            if (rawTitle != null) title = rawTitle
            val rawEntries = arr.getTextArray(R.styleable.ListPickerView_entries)
            if (rawEntries != null) entries = rawEntries.map { it.toString() }
            val rawValues = arr.getTextArray(R.styleable.ListPickerView_values)
            if (rawValues != null) values = rawValues.map { it.toString() }

            binding.let {
                it.root.clipToOutline = true
                it.title.isVisible = title.isNotEmpty()
                if (title.isNotEmpty()) it.title.text = title

                it.description.textSize =
                    (if (title.isEmpty()) resources.getDimension(R.dimen.text_body_1)
                    else resources.getDimension(R.dimen.caption)) / resources.displayMetrics.density
                it.description.text = ""

                it.root.setOnClickListener { onClick() }
            }
        } finally {
            arr.recycle()
        }
    }

    fun setOnIndexChangeListener(listener: (Int) -> Unit) {
        onIndexChangeListener = listener
    }

    fun setOnValueChangeListener(listener: (String) -> Unit) {
        onValueChangeListener = listener
    }

    private fun onClick() {
        if (entries.size < 6) { // Basic choice dialog
            val materialDialog = MaterialAlertDialogBuilder(
                context,
                context.getIdForCurrentTheme(R.style.Theme_Light_Dialog)
            )
                .setSingleChoiceItems(
                    entries.toTypedArray(),
                    index,
                    this::onSelect
                )
                .setCancelable(true)
                .create()

            materialDialog.show()
        } else { // Custom filterable dialog
            this.getActivity().let {
                if (it is FragmentActivity) ListPickerDialogFragment.invoke(
                    it,
                    this::onSelect,
                    entries
                )
            }
        }
    }

    private fun onSelect(dialog: DialogInterface, selectedIndex: Int) {
        onSelect(selectedIndex)
        dialog.dismiss()
    }

    private fun onSelect(selectedIndex: Int) {
        index = selectedIndex
        onIndexChangeListener?.invoke(selectedIndex)
        if (value.isNotEmpty()) onValueChangeListener?.invoke(value)
    }

    private fun selectIndex(selectedIndex: Int) {
        if (selectedIndex > -1 && selectedIndex < entries.size)
            binding.description.text = entries[selectedIndex]
    }
}