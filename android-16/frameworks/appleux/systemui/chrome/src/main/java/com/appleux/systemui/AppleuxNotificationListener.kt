package com.appleux.systemui

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.appleux.design.components.AppleuxNotificationShade

/**
 * Feeds REAL platform notifications into the AppleUX shade.
 *
 * ============================ MOVED INTO SYSTEMUI ============================
 * This was a separate com.appleux.systemui app, sharing its process with
 * AppleuxSystemUIService so a plain in-memory bridge would do. That app is
 * deleted (deliverables/m23, 2026-10-17). This class now lives in
 * com.android.systemui as module appleux-systemui-chrome, declared in
 * frameworks/base/packages/SystemUI/AndroidManifest.xml.
 *
 * Declaring it inside SystemUI is also what makes it work WITHOUT the user
 * granting notification access in Settings. BIND_NOTIFICATION_LISTENER_SERVICE
 * is a signature permission SystemUI itself holds, so a listener declared in
 * SystemUI's manifest is connected by the platform rather than by a consent
 * dialog. The overlay app's listener only ever ran if the user had approved it
 * by hand - so this is a functional gain, not just tidiness.
 *
 * Android channels, grouping keys, flags and actions are preserved 1:1 (charter
 * §3D: never break the platform notification model for looks).
 */
class AppleuxNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        AppleuxNotificationBus.attach(this)
        pushActive()
    }

    override fun onListenerDisconnected() {
        AppleuxNotificationBus.detach(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        pushActive()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        pushActive()
    }

    private fun pushActive() {
        val list = try {
            activeNotifications?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        AppleuxNotificationBus.publish(list.mapNotNull { toData(it) })
    }

    /** Republishes the live list on demand (see AppleuxNotificationBus.refresh). */
    fun refreshNow() = pushActive()

    private fun toData(sbn: StatusBarNotification): AppleuxNotificationShade.NotificationData? {
        val n = sbn.notification ?: return null
        val extras = n.extras ?: return null
        // Ongoing foreground-service notifications stay (matches platform).
        val clearable = (n.flags and Notification.FLAG_ONGOING_EVENT) == 0 &&
            (n.flags and Notification.FLAG_NO_CLEAR) == 0
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: sbn.packageName
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""
        val actions = n.actions?.mapNotNull { a ->
            val t = a.title?.toString() ?: return@mapNotNull null
            val pi = a.actionIntent ?: return@mapNotNull null
            AppleuxNotificationShade.NotificationAction(t, pi)
        } ?: emptyList()
        return AppleuxNotificationShade.NotificationData(
            id = sbn.key,
            title = title,
            subtitle = text,
            timestamp = android.text.format.DateFormat.format(
                "h:mm", sbn.postTime
            ).toString(),
            isClearable = clearable,
            key = sbn.key,
            packageName = sbn.packageName,
            icon = iconFor(sbn),
            actions = actions,
            contentIntent = n.contentIntent
        )
    }

    /**
     * iOS always shows the posting app's icon. Android's small icon is a
     * monochrome silhouette, so it is tinted with the notification's accent
     * colour to read as a badge rather than a black blob. Falls back to the
     * real application icon when no small icon is set.
     */
    private fun iconFor(sbn: StatusBarNotification): android.graphics.drawable.Drawable? {
        val n = sbn.notification
        val accent = if (n.color != 0) n.color else DEFAULT_ACCENT
        n.smallIcon?.loadDrawable(this)?.let { small ->
            small.setTint(accent)
            return small
        }
        return runCatching { packageManager.getApplicationIcon(sbn.packageName) }
            .getOrNull()
    }

    private companion object {
        /** iOS blue, used when the notification declares no accent colour. */
        const val DEFAULT_ACCENT = 0xFF0A84FF.toInt()
    }
}

/**
 * Same-process bridge: the listener publishes snapshots; the SystemUI
 * service subscribes and renders. Dismiss/clear requests flow back to the
 * live listener instance.
 */
class AppleuxNotificationBus private constructor() {
    companion object {
        private var listener: AppleuxNotificationListener? = null
        private var subscriber: ((List<AppleuxNotificationShade.NotificationData>) -> Unit)? = null

        fun attach(l: AppleuxNotificationListener) {
            listener = l
        }

        fun detach(l: AppleuxNotificationListener) {
            if (listener === l) listener = null
        }

        fun subscribe(fn: (List<AppleuxNotificationShade.NotificationData>) -> Unit) {
            subscriber = fn
        }

        fun publish(data: List<AppleuxNotificationShade.NotificationData>) {
            subscriber?.invoke(data)
        }

        fun requestDismiss(key: String) {
            try {
                listener?.cancelNotification(key)
            } catch (e: Exception) {}
        }

        /**
         * Republish the live list. Used when a swipe is refused: the row is
         * already off screen, so the snapshot has to be re-sent or the
         * ongoing notification would look dismissed without being dismissed.
         */
        fun refresh() {
            try {
                listener?.refreshNow()
            } catch (e: Exception) {}
        }

        fun requestClearAll() {
            try {
                listener?.cancelAllNotifications()
            } catch (e: Exception) {}
        }
    }
}
