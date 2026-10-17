package com.appleux.launcher.apps

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem

/** iOS-lookalike Photos shell: MediaStore image grid, permission-guarded. */
class PhotosActivity : AppCompatActivity() {
    private val perm get() = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val grid = GridLayout(this).apply { columnCount = 3 }
        val empty = AppleuxListItem(this).apply {
            setTitle("No photos"); setSubtitle("Grant media permission to browse")
        }
        val card = AppleuxCard(this).apply { addView(grid) }
        setContentView(ScrollView(this).apply { addView(card) })
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 2)
            grid.addView(empty); return
        }
        loadInto(grid, empty)
    }
    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (r.firstOrNull() == PackageManager.PERMISSION_GRANTED) recreate()
    }
    private fun loadInto(grid: GridLayout, empty: AppleuxListItem) {
        var n = 0
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID),
            null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
            while (c.moveToNext() && n++ < 60) {
                val u = ContentUris.withAppendedId(uri, c.getLong(0))
                grid.addView(ImageView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply { width = 280; height = 280 }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    runCatching { setImageURI(u) }
                })
            }
        }
        if (n == 0) grid.addView(empty)
    }
}
