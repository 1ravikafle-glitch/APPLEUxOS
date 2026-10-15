package org.ioslike.ui.controlcenter

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ioslike.ui.notifications.IOSNotificationListener

class MediaState(val title: String, val artist: String, val playing: Boolean, val art: Bitmap?)

/** Now-playing card. Needs notification-listener access (granted by the overlay) to read active sessions. */
class MediaRepo(ctx: Context) {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(ctx, IOSNotificationListener::class.java)
    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state.asStateFlow()
    private var controller: MediaController? = null

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessions, listener)
            pick(msm.getActiveSessions(listener))
        } catch (_: SecurityException) { /* listener access not granted yet */ }
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(sessions) } catch (_: Exception) {}
        controller?.unregisterCallback(cb)
    }

    private fun pick(list: List<MediaController>?) {
        controller?.unregisterCallback(cb)
        controller = list?.firstOrNull()
        controller?.registerCallback(cb)
        publish()
    }

    private fun publish() {
        val c = controller ?: run { _state.value = null; return }
        val md = c.metadata
        _state.value = MediaState(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (_state.value?.playing == true) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}
