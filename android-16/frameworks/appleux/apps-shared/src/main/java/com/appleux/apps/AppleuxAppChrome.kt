package com.appleux.apps

import android.app.Activity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.design.theme.AppleuxTheme

/**
 * System-level AppleUX chrome for apps: user dark-mode pref wins (like the
 * launcher — a fresh activity reading only platform night mode flips theme
 * between screens), plus top cutout inset so titles never sit under the
 * status bar.
 */
object AppleuxAppChrome {
    fun apply(activity: Activity, root: LinearLayout) {
        AppleuxTheme.applyToActivity(activity)
        AppleuxRuntimeConfig.reload(activity)
        AppleuxTheme.isDarkMode = AppleuxRuntimeConfig.isDarkModeEnabled
        AppleuxTheme.applySystemBars(activity)
        root.setBackgroundColor(AppleuxTheme.background.resolved)
        root.setPadding(
            root.paddingLeft,
            root.paddingTop + cutoutTop(activity),
            root.paddingRight,
            root.paddingBottom
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top.coerceAtLeast(cutoutTop(activity)),
                bars.right, bars.bottom)
            insets
        }
    }

    private fun cutoutTop(activity: Activity): Int = runCatching {
        val disp = activity.display ?: return 0
        76 * activity.resources.displayMetrics.densityDpi / 440
    }.getOrDefault(0)

    fun emptyRow(activity: Activity, text: String): android.widget.TextView =
        android.widget.TextView(activity).apply {
            setText(text)
            textSize = 15f
            setTextColor(com.appleux.design.tokens.ColorTokens.secondaryLabel)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 48, 0, 48)
        }

    /** iOS green circular call button (not a full-width bar). */
    fun callButton(activity: Activity, onCall: () -> Unit): android.widget.ImageButton {
        val d = activity.resources.displayMetrics.density
        return android.widget.ImageButton(activity).apply {
            val glyph = com.appleux.design.components.AppleuxIconRegistry
                .getDrawableRes("system_phone")
                ?.let { androidx.core.content.ContextCompat.getDrawable(activity, it) }
            setImageDrawable(glyph)
            setColorFilter(com.appleux.design.tokens.ColorTokens.white)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(com.appleux.design.tokens.ColorTokens.systemGreen)
            }
            val s = (72 * d).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = (16 * d).toInt()
                bottomMargin = (8 * d).toInt()
            }
            isClickable = true
            setOnClickListener { onCall() }
            contentDescription = "Call"
        }
    }
}
