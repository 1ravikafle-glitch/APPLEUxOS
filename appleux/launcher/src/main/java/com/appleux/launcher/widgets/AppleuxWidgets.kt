package com.appleux.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.launcher.R

const val ACTION_SHOW_CC = "com.appleux.launcher.widgets.SHOW_CC"

private fun isDark(context: Context): Boolean {
    AppleuxRuntimeConfig.reload(context)
    return AppleuxRuntimeConfig.isDarkModeEnabled
}

/**
 * Widget tap deep-links to the relevant content (HIG system experiences §5),
 * not to a generic surface: clock -> Clock app, calendar -> Calendar app,
 * battery -> Settings root. First resolvable package wins; nothing happens
 * when none resolve (no dead tap target is better than a wrong one, and the
 * CC broadcast path below stays as the manual test entry).
 */
private fun bindTapToApp(
    context: Context,
    views: RemoteViews,
    rootId: Int,
    vararg pkgs: String
) {
    val pm = context.packageManager
    val launch = pkgs.firstNotNullOfOrNull { runCatching {
        pm.getLaunchIntentForPackage(it)?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull() } ?: return
    val pi = android.app.PendingIntent.getActivity(
        context, rootId,
        launch,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            android.app.PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(rootId, pi)
}

private fun handleShowCc(context: Context) {
    runCatching {
        val svc = android.content.Intent(
            "com.appleux.systemui.SHOW_CONTROL_CENTER"
        ).apply {
            component = android.content.ComponentName(
                "com.appleux.systemui",
                "com.appleux.systemui.AppleuxSystemUIService"
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(svc)
        } else {
            @Suppress("DEPRECATION")
            context.startService(svc)
        }
    }
}

/** Clock widget (medium): TextClock auto-updates, no polling, no permissions. */
class AppleuxClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val dark = isDark(context)
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setInt(
                R.id.widget_clock_root, "setBackgroundResource",
                if (dark) R.drawable.widget_card_dark else R.drawable.widget_card_light
            )
            val primary = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val secondary = if (dark) 0x99FFFFFF.toInt() else 0xFF6E6E73.toInt()
            views.setTextColor(R.id.widget_clock_time, primary)
            views.setTextColor(R.id.widget_clock_date, secondary)
            bindTapToApp(context, views, R.id.widget_clock_root,
                "com.google.android.deskclock", "com.android.deskclock")
            mgr.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action == ACTION_SHOW_CC) handleShowCc(context)
        else super.onReceive(context, intent)
    }
}

/** Battery widget (small): pushed from the launcher process on sticky broadcasts. */
class AppleuxBatteryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) pushBatteryLevel(context, mgr, id, readStickyLevel(context))
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action == ACTION_SHOW_CC) handleShowCc(context)
        else super.onReceive(context, intent)
    }

    companion object {
        fun pushBatteryLevel(context: Context, mgr: AppWidgetManager, id: Int, pct: Int) {
            val dark = isDark(context)
            val views = RemoteViews(context.packageName, R.layout.widget_battery)
            views.setInt(
                R.id.widget_battery_root, "setBackgroundResource",
                if (dark) R.drawable.widget_card_dark else R.drawable.widget_card_light
            )
            val primary = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val secondary = if (dark) 0x99FFFFFF.toInt() else 0xFF6E6E73.toInt()
            views.setTextColor(R.id.widget_battery_level, primary)
            views.setTextColor(R.id.widget_battery_label, secondary)
            views.setTextViewText(R.id.widget_battery_level, "$pct%")
            views.setProgressBar(R.id.widget_battery_bar, 100, pct.coerceIn(0, 100), false)
            bindTapToApp(context, views, R.id.widget_battery_root, "com.appleux.settings")
            mgr.partiallyUpdateAppWidget(id, views)
        }

        fun readStickyLevel(context: Context): Int {
            val b = context.registerReceiver(null, android.content.IntentFilter(
                android.content.Intent.ACTION_BATTERY_CHANGED
            )) ?: return -1
            val l = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val s = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            return if (l >= 0 && s > 0) (l * 100 / s) else -1
        }
    }
}

/** Calendar widget (medium): date numeral + weekday, zero permissions. */
class AppleuxCalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val dark = isDark(context)
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            views.setInt(
                R.id.widget_calendar_root, "setBackgroundResource",
                if (dark) R.drawable.widget_card_dark else R.drawable.widget_card_light
            )
            val primary = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val secondary = if (dark) 0x99FFFFFF.toInt() else 0xFF6E6E73.toInt()
            views.setTextColor(R.id.widget_calendar_weekday, primary)
            views.setTextColor(R.id.widget_calendar_month, secondary)
            bindTapToApp(context, views, R.id.widget_calendar_root,
                "com.google.android.calendar", "com.android.calendar")
            mgr.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action == ACTION_SHOW_CC) handleShowCc(context)
        else super.onReceive(context, intent)
    }
}
