package com.appleux.design.components

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.EditText
import android.widget.LinearLayout

/** spotlight: system search overlay (frontend; results supplied by host). */
class AppleuxSpotlightView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val field = EditText(context).apply { hint = "Search"; isSingleLine = true }
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    var onQuery: ((String) -> Unit)? = null
    init {
        orientation = VERTICAL
        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) { onQuery?.invoke(s?.toString().orEmpty()) }
        })
    }
    fun submitRows(rows: List<android.view.View>) {
        list.removeAllViews()
        rows.forEach { list.addView(it) }
    }
}
