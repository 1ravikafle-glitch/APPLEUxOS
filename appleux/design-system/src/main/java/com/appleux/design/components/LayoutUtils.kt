package com.appleux.design.components

import android.content.Context
import android.util.TypedValue

object LayoutUtils {
    fun dpToPx(dp: Float, context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    fun dpToPxFloat(dp: Float, context: Context): Float {
        val density = context.resources.displayMetrics.density
        return dp * density
    }

    fun spToPx(sp: Float, context: Context): Float {
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        return sp * scaledDensity
    }

    fun pxToDp(px: Float, context: Context): Float {
        val density = context.resources.displayMetrics.density
        return px / density
    }

    fun getScreenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    fun getScreenHeight(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    fun getDensity(context: Context): Float {
        return context.resources.displayMetrics.density
    }
}
