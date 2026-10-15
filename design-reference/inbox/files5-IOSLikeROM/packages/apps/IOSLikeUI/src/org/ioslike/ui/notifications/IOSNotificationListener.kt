package org.ioslike.ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class NotifItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val time: Long,
    val icon: Bitmap?,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
)

/** Feeds both the Notification Center list and the slide-in-from-left heads-up banner. */
class IOSNotificationListener : NotificationListenerService() {

    companion object {
        private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
        val items: StateFlow<List<NotifItem>> = _items.asStateFlow()

        private val _arrived = MutableSharedFlow<NotifItem>(extraBufferCapacity = 8)
        val arrived: SharedFlow<NotifItem> = _arrived.asSharedFlow()

        @Volatile private var instance: IOSNotificationListener? = null
        fun dismiss(key: String) { instance?.cancelNotification(key) }
        fun dismissAll() { instance?.cancelAllNotifications() }
    }

    private val iconCache = HashMap<String, Bitmap?>()

    override fun onListenerConnected() { instance = this; rebuild() }
    override fun onListenerDisconnected() { instance = null }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        rebuild()
        val r = Ranking()
        val important = rankingMap.getRanking(sbn.key, r) && r.importance >= NotificationManager.IMPORTANCE_HIGH
        if (important) toItem(sbn)?.let { _arrived.tryEmit(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = rebuild()

    private fun rebuild() {
        _items.value = (activeNotifications ?: emptyArray()).mapNotNull(::toItem).sortedByDescending { it.time }
    }

    private fun toItem(sbn: StatusBarNotification): NotifItem? {
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return null
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null
        val pm = packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) { sbn.packageName }
        val icon = iconCache.getOrPut(sbn.packageName) {
            try { pm.getApplicationIcon(sbn.packageName).toBitmap() } catch (_: Exception) { null }
        }
        return NotifItem(
            key = sbn.key, pkg = sbn.packageName, appName = appName,
            title = title.ifEmpty { appName }, text = text,
            time = sbn.postTime, icon = icon, intent = n.contentIntent,
            autoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
        )
    }

    private fun Drawable.toBitmap(size: Int = 96): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size); draw(Canvas(b)); return b
    }
}
