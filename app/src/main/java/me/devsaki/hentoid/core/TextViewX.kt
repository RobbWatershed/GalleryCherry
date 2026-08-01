package me.devsaki.hentoid.core

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.textfield.TextInputLayout
import me.devsaki.hentoid.R
import me.devsaki.hentoid.util.Debouncer
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

fun TextView.setOnTextChangedListener(
    scope: LifecycleCoroutineScope,
    listener: (value: String) -> Unit
) {
    addTextChangedListener(
        object : TextWatcher {
            private val debouncer: Debouncer<String> = Debouncer(scope, 500) { s: String ->
                listener.invoke(s)
            }

            override fun afterTextChanged(s: Editable?) {
                if (s != null) debouncer.submit(s.toString())
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                // Nothing to override here
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                // Nothing to override here
            }
        }
    )
}


fun TextView.setMiddleEllipsis() {
    if (maxLines > 0 && maxLines < Int.MAX_VALUE) {
        layout?.let {
            val lineEndIndex = it.getLineEnd(min(lineCount, maxLines) - 1)
            if (lineEndIndex < text.lastIndex || lineCount > maxLines) {
                val partLength = max(0, (lineEndIndex / 2) - (2 * lineCount - 1))
                val part1 = text.substring(0, partLength)
                val part2 = text.substring(text.lastIndex - partLength)
                text = "$part1…$part2"
            }
        }
    }
}

fun TextInputLayout.checkRange(minValue: Int, maxValue: Int): Boolean {
    val editTxt = this.editText
    require(editTxt != null)
    val errMsg = resources.getString(R.string.range_check, minValue, maxValue)
    val nbMaxDigits = floor(log10(maxValue.toDouble())) + 1
    if (editTxt.text.toString().isEmpty() || editTxt.text.toString().length > nbMaxDigits) {
        this.isErrorEnabled = true
        this.error = errMsg
        return false
    }
    val intValue = editTxt.text.toString().toInt()
    if (intValue !in minValue..maxValue) {
        this.isErrorEnabled = true
        this.error = errMsg
        return false
    }
    this.isErrorEnabled = false
    this.error = null
    return true
}