package com.appleux.launcher.apps

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.tokens.ColorTokens

/**
 * iOS Messages: thread list + conversation with blue-sent / gray-received
 * bubbles, send via SmsManager. Requests the default-SMS role so sending
 * works without a chooser; without the role it still reads + drafts.
 */
class MessagesActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var threadObserver: ContentObserver? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setContentView(root)
        AppleuxAppChrome.apply(this, root)
        if (!have(Manifest.permission.READ_SMS)) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), 31)
        }
        maybeRequestSmsRole()
        showThreads()
    }

    private fun have(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun isDefaultSms(): Boolean = packageName ==
        Telephony.Sms.getDefaultSmsPackage(this)

    private fun maybeRequestSmsRole() {
        if (isDefaultSms()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java) ?: return
                if (rm.isRoleAvailable(RoleManager.ROLE_SMS) &&
                    !rm.isRoleHeld(RoleManager.ROLE_SMS)
                ) {
                    startActivityForResult(
                        rm.createRequestRoleIntent(RoleManager.ROLE_SMS), 32)
                }
            }
        }
    }

    private data class Thread(val id: Long, val snippet: String, val date: Long, val count: Int)

    private fun loadThreads(): List<Thread> {
        if (!have(Manifest.permission.READ_SMS)) return emptyList()
        val out = mutableListOf<Thread>()
        runCatching {
            contentResolver.query(
                Uri.parse("content://sms/conversations"),
                arrayOf(Telephony.Sms.Conversations._ID,
                    Telephony.Sms.Conversations.SNIPPET,
                    Telephony.Sms.Conversations.DATE,
                    Telephony.Sms.Conversations.MESSAGE_COUNT),
                null, null, "${Telephony.Sms.Conversations.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(Thread(c.getLong(0), c.getString(1).orEmpty(),
                        c.getLong(2), c.getInt(3)))
                }
            }
        }
        // Fallback when the conversations table is absent: group inbox.
        if (out.isEmpty()) {
            runCatching {
                contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null, null, "${Telephony.Sms.DATE} DESC"
                )?.use { c ->
                    val seen = linkedMapOf<String, Thread>()
                    while (c.moveToNext()) {
                        val addr = c.getString(0).orEmpty()
                        seen.getOrPut(addr) {
                            Thread(addr.hashCode().toLong(), c.getString(1).orEmpty(), c.getLong(2), 0)
                        }
                    }
                    out.addAll(seen.values)
                }
            }
        }
        return out
    }

    private fun threadAddress(threadId: Long): String {
        var addr = ""
        runCatching {
            contentResolver.query(
                Uri.parse("content://sms/conversations/$threadId"),
                arrayOf(Telephony.Sms.ADDRESS), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) addr = c.getString(0).orEmpty()
            }
        }
        return addr
    }

    private fun showThreads() {
        root.removeAllViews()
        root.addView(largeTitle("Messages"))
        if (!isDefaultSms()) {
            root.addView(Button(this).apply {
                text = "Set as default SMS app"
                setOnClickListener { maybeRequestSmsRole() }
            })
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (t in loadThreads()) {
            val label = threadAddress(t.id).ifEmpty { "Thread ${t.id}" }
            col.addView(AppleuxListItem(this).apply {
                setTitle(label)
                setSubtitle(t.snippet)
                setDividerVisible(true)
                isClickable = true
                setOnClickListener { showConversation(t.id, label) }
            })
        }
        val scroll = ScrollView(this)
        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "New Message"
            setOnClickListener { compose(null) }
        })
        watchThreads { showThreads() }
    }

    private data class Msg(val body: String, val sent: Boolean, val date: Long)

    private fun loadMessages(threadId: Long, address: String): List<Msg> {
        val out = mutableListOf<Msg>()
        runCatching {
            val uri = Uri.parse("content://sms/conversations/$threadId")
            contentResolver.query(uri,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE),
                null, null, "${Telephony.Sms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    out.add(Msg(c.getString(0).orEmpty(),
                        c.getInt(1) == Telephony.Sms.MESSAGE_TYPE_SENT, c.getLong(2)))
                }
            }
            if (out.isEmpty() && address.isNotEmpty()) {
                for (box in listOf(Telephony.Sms.Inbox.CONTENT_URI, Telephony.Sms.Sent.CONTENT_URI)) {
                    contentResolver.query(box,
                        arrayOf(Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE),
                        "${Telephony.Sms.ADDRESS}=?", arrayOf(address),
                        "${Telephony.Sms.DATE} ASC")?.use { c ->
                        while (c.moveToNext()) {
                            out.add(Msg(c.getString(0).orEmpty(),
                                box == Telephony.Sms.Sent.CONTENT_URI, c.getLong(2)))
                        }
                    }
                }
                out.sortBy { it.date }
            }
        }
        return out
    }

    private fun showConversation(threadId: Long, address: String) {
        root.removeAllViews()
        root.addView(backRow { showThreads() })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        fun render() {
            list.removeAllViews()
            for (m in loadMessages(threadId, address)) {
                list.addView(bubble(m))
            }
            scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
        }
        render()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val field = EditText(this).apply {
            hint = "iMessage"
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val send = Button(this).apply {
            text = "↑"
            setOnClickListener {
                val text = field.text.toString()
                if (text.isBlank()) return@setOnClickListener
                sendSms(address, text)
                field.setText("")
                list.postDelayed({ render() }, 800)
            }
        }
        row.addView(field)
        row.addView(send)
        root.addView(row)
        watchThreads { render() }
    }

    /** iOS bubble: blue right for sent, gray left for received. */
    private fun bubble(m: Msg): TextView {
        return TextView(this).apply {
            text = m.body
            textSize = 16f
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, (8 * resources.displayMetrics.density).toInt(), pad,
                (8 * resources.displayMetrics.density).toInt())
            if (m.sent) {
                setTextColor(ColorTokens.white)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 18f * resources.displayMetrics.density
                    setColor(ColorTokens.systemBlue)
                }
                gravity = Gravity.END
            } else {
                setTextColor(ColorTokens.label)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 18f * resources.displayMetrics.density
                    setColor(ColorTokens.systemFillSecondary)
                }
                gravity = Gravity.START
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(pad * 3, pad / 2, pad * 3, pad / 2)
            }
        }
    }

    private fun compose(prefill: String?) {
        root.removeAllViews()
        root.addView(backRow { showThreads() })
        root.addView(largeTitle("New Message"))
        val to = EditText(this).apply { hint = "To:"; isSingleLine = true }
        val field = EditText(this).apply {
            hint = "Message"
            setText(prefill.orEmpty())
        }
        root.addView(to)
        root.addView(field)
        root.addView(Button(this).apply {
            text = "Send"
            setOnClickListener {
                if (to.text.isNotBlank() && field.text.isNotBlank()) {
                    sendSms(to.text.toString(), field.text.toString())
                    showThreads()
                }
            }
        })
    }

    private fun sendSms(dest: String, text: String) {
        if (!have(Manifest.permission.SEND_SMS)) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 33)
            Toast.makeText(this, "Allow SMS, then send again", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            val sm = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            val parts = sm?.divideMessage(text) ?: listOf(text)
            for (part in parts) {
                sm?.sendTextMessage(dest, null, part, null, null)
            }
            Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Send failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun watchThreads(onChange: () -> Unit) {
        threadObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        threadObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                runOnUiThread { onChange() }
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI, true, threadObserver!!)
        }
    }

    private fun largeTitle(text: String) = TextView(this).apply {
        setText(text)
        textSize = 30f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(ColorTokens.label)
        setPadding(32, 24, 32, 8)
    }

    private fun backRow(back: () -> Unit) = Button(this, null,
        android.R.attr.borderlessButtonStyle).apply {
        text = "‹ Back"
        textSize = 17f
        setTextColor(ColorTokens.systemBlue)
        gravity = Gravity.START
        setOnClickListener { back() }
    }

    override fun onDestroy() {
        threadObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        super.onDestroy()
    }
}
