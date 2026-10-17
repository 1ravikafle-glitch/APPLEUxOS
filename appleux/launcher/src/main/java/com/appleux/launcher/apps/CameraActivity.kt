package com.appleux.launcher.apps

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem

/** iOS-lookalike Camera shell: delegates capture to ACTION_IMAGE_CAPTURE. */
class CameraActivity : AppCompatActivity() {
    private lateinit var preview: ImageView
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        preview = ImageView(this).apply { minimumHeight = 480 }
        val snap = Button(this).apply {
            text = "Take photo"
            setOnClickListener {
                runCatching {
                    @Suppress("DEPRECATION") startActivityForResult(
                        Intent(MediaStore.ACTION_IMAGE_CAPTURE), 3)
                }
            }
        }
        val note = AppleuxListItem(this).apply {
            setTitle("System camera"); setSubtitle("Capture via platform intent")
        }
        val card = AppleuxCard(this).apply { addView(note) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(preview); addView(snap); addView(card)
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
    @Deprecated("Thumbnail path uses the legacy result callback")
    override fun onActivityResult(rc: Int, ok: Int, d: Intent?) {
        super.onActivityResult(rc, ok, d)
        if (rc == 3 && ok == RESULT_OK) {
            (d?.extras?.get("data") as? Bitmap)?.let { preview.setImageBitmap(it) }
        }
    }
}
