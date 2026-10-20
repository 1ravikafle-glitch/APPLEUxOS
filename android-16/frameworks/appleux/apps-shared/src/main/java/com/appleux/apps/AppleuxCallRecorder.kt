package com.appleux.apps

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Shared call-audio capture: direct VOICE_CALL source (priv-app
 * CAPTURE_AUDIO_OUTPUT), mic fallback. No announcement on any path —
 * announcements are a stock-dialer behavior, not a platform one.
 */
object AppleuxCallRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    fun toggle(context: android.content.Context): Boolean {
        return if (isRecording) {
            stop()
            false
        } else {
            start(context)
            isRecording
        }
    }

    fun start(context: android.content.Context): Boolean {
        if (recorder != null) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            val dir = File(context.filesDir, "recordings")
            dir.mkdirs()
            file = File(dir, "call-${System.currentTimeMillis()}.m4a")
            recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()).apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                } catch (e: Exception) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file!!.absolutePath)
                prepare()
                start()
            }
            true
        }.getOrDefault(false).also { ok ->
            if (!ok) {
                recorder = null
                file?.delete()
                file = null
            }
        }
    }

    fun stop() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        if (file?.length() == 0L) {
            file?.delete()
            file = null
        }
    }
}
