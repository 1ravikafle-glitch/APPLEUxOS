package com.appleux.apps

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Rational
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem

/** Video player with public-API Picture-in-Picture (iOS PiP equivalent). */
class AppleuxVideoPlayerActivity : AppCompatActivity() {
    private lateinit var video: VideoView
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        video = VideoView(this)
        col.addView(video, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f))
        val pip = Button(this).apply {
            text = "Picture in Picture"
            setOnClickListener { enterPip() }
        }
        col.addView(pip)
        val first = firstVideo()
        if (first != null) {
            video.setVideoURI(first)
            video.start()
        } else {
            col.addView(AppleuxCard(this).apply {
                addView(AppleuxListItem(this@AppleuxVideoPlayerActivity).apply {
                    setTitle("No videos"); setSubtitle("Shoot or download a video first")
                })
            })
        }
        setContentView(col)
    }
    private fun firstVideo(): Uri? = runCatching {
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID), null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            if (c.moveToFirst()) Uri.withAppendedPath(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
            else null
        }
    }.getOrNull()
    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(9, 16))
                    .build()
            )
        }
    }
    override fun onUserLeaveHint() {
        // iOS-like: leaving with video playing drops into PiP automatically.
        if (::video.isInitialized && video.isPlaying) enterPip()
        super.onUserLeaveHint()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { video.setVideoURI(it); video.start() }
    }
}
