package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.appleux.design.tokens.SpacingTokens

class AppleuxNotificationFooter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = VERTICAL
        setPadding(SpacingTokens.PADDING_MEDIUM, SpacingTokens.PADDING_SMALL, SpacingTokens.PADDING_MEDIUM, SpacingTokens.PADDING_MEDIUM)
    }
}
