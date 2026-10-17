package com.appleux.launcher.apps

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxCard
import java.util.Calendar

/** iOS-lookalike Calendar shell: month grid + ACTION_INSERT event. */
class CalendarActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val cal = Calendar.getInstance()
        val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val grid = GridLayout(this).apply { columnCount = 7 }
        for (d in listOf("S","M","T","W","T","F","S")) grid.addView(TextView(this).apply { text = d })
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        repeat((first.get(Calendar.DAY_OF_WEEK) - 1) % 7) {
            grid.addView(TextView(this).apply { text = "" })
        }
        for (day in 1..max) grid.addView(TextView(this).apply { text = "$day     " })
        val card = AppleuxCard(this).apply { addView(grid) }
        val add = Button(this).apply {
            text = "New event"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 3600000)
                })
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; addView(card); addView(add)
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
