package com.appleux.launcher.apps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem

/** iOS-lookalike Browser shell: URL bar + ACTION_VIEW. */
class BrowserActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val bar = EditText(this).apply { hint = "https://example.com"; isSingleLine = true }
        val go = Button(this).apply {
            text = "Go"
            setOnClickListener {
                var u = bar.text.toString().trim()
                if (!u.startsWith("http")) u = "https://$u"
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
            }
        }
        val row = AppleuxListItem(this).apply {
            setTitle("System WebView shell"); setSubtitle("Pages open in the platform browser")
        }
        val card = AppleuxCard(this).apply { addView(row) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar); addView(go); addView(card)
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
