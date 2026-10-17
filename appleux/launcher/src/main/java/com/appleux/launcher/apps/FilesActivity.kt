package com.appleux.launcher.apps

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem

/** iOS-lookalike Files shell: ACTION_OPEN_DOCUMENT browser + picked URI. */
class FilesActivity : AppCompatActivity() {
    private lateinit var row: AppleuxListItem
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        row = AppleuxListItem(this).apply {
            setTitle("No file open"); setSubtitle("Pick via Storage Access Framework")
        }
        val card = AppleuxCard(this).apply { addView(row) }
        val pick = Button(this).apply {
            text = "Browse"
            setOnClickListener {
                runCatching {
                    @Suppress("DEPRECATION") startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                        }, 4)
                }
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(card); addView(pick)
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
    @Deprecated("SAF result uses the legacy callback")
    override fun onActivityResult(rc: Int, ok: Int, d: Intent?) {
        super.onActivityResult(rc, ok, d)
        if (rc == 4 && ok == RESULT_OK) {
            val u = d?.data
            row.setTitle(u?.lastPathSegment ?: "File")
            row.setSubtitle(u.toString())
        }
    }
}
