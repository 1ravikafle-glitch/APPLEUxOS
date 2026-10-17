package com.appleux.launcher.apps

import android.Manifest
import android.content.ContentProviderOperation
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
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
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.tokens.ColorTokens

/**
 * iOS Contacts: grouped list (starred first), detail with call/message
 * rows, add + edit + favorite toggle — all on ContactsContract.
 */
class ContactsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout

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
        if (!have(Manifest.permission.READ_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 21)
        }
        showList()
    }

    private fun have(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private data class Entry(val id: Long, val name: String, val starred: Boolean, val phone: String?)

    private fun loadAll(): List<Entry> {
        if (!have(Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = mutableListOf<Entry>()
        runCatching {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.STARRED,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER),
                null, null,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1).orEmpty()
                    val star = c.getInt(2) == 1
                    var phone: String? = null
                    if (c.getInt(3) > 0) {
                        contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                            arrayOf(id.toString()), null
                        )?.use { p ->
                            if (p.moveToFirst()) phone = p.getString(0)
                        }
                    }
                    out.add(Entry(id, name, star, phone))
                }
            }
        }
        return out.sortedWith(compareByDescending<Entry> { it.starred }.thenBy { it.name.lowercase() })
    }

    private fun showList() {
        root.removeAllViews()
        root.addView(largeTitle("Contacts"))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val all = loadAll()
        if (all.isEmpty()) {
            col.addView(AppleuxAppChrome.emptyRow(this, "No Contacts"))
        }
        for (e in all) {
            col.addView(AppleuxListItem(this).apply {
                setTitle((if (e.starred) "★ " else "") + e.name)
                setSubtitle(e.phone)
                setDividerVisible(true)
                isClickable = true
                setOnClickListener { showDetail(e) }
            })
        }
        val scroll = ScrollView(this)
        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "+ New Contact"
            setOnClickListener { showEditor(null) }
        })
    }

    private fun showDetail(e: Entry) {
        root.removeAllViews()
        root.addView(backRow { showList() })
        val hero = TextView(this).apply {
            text = e.name.ifEmpty { "Unknown" }
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 4)
        }
        root.addView(hero)
        val card = AppleuxCard(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        e.phone?.let { num ->
            col.addView(AppleuxListItem(this).apply {
                setTitle("Call")
                setSubtitle(num)
                setDividerVisible(true)
                isClickable = true
                setOnClickListener {
                    runCatching {
                        startActivity(android.content.Intent(
                            android.content.Intent.ACTION_DIAL,
                            android.net.Uri.parse("tel:$num")))
                    }
                }
            })
            col.addView(AppleuxListItem(this).apply {
                setTitle("Message")
                setSubtitle(num)
                setDividerVisible(true)
                isClickable = true
                setOnClickListener {
                    startActivity(android.content.Intent(
                        android.content.Intent.ACTION_SENDTO,
                        android.net.Uri.parse("smsto:$num")))
                }
            })
        }
        col.addView(AppleuxListItem(this).apply {
            setTitle(if (e.starred) "★ Remove Favorite" else "☆ Add to Favorites")
            setDividerVisible(false)
            isClickable = true
            setOnClickListener { setStarred(e, !e.starred); showList() }
        })
        card.addView(col)
        root.addView(card)
        root.addView(Button(this).apply {
            text = "Edit"
            setOnClickListener { showEditor(e) }
        })
    }

    private fun setStarred(e: Entry, star: Boolean) {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 22)
            return
        }
        runCatching {
            val uri = android.content.ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI, e.id)
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (star) 1 else 0)
            }
            contentResolver.update(uri, values, null, null)
        }
    }

    private fun showEditor(existing: Entry?) {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 23)
            return
        }
        root.removeAllViews()
        root.addView(backRow { if (existing != null) showDetail(existing) else showList() })
        root.addView(largeTitle(if (existing != null) "Edit Contact" else "New Contact"))
        val nameField = EditText(this).apply {
            hint = "Name"
            setText(existing?.name.orEmpty())
            isSingleLine = true
        }
        val phoneField = EditText(this).apply {
            hint = "Phone"
            setText(existing?.phone.orEmpty())
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        root.addView(nameField)
        root.addView(phoneField)
        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                saveContact(existing?.id,
                    nameField.text.toString(), phoneField.text.toString())
                showList()
            }
        })
    }

    private fun saveContact(id: Long?, name: String, phone: String) {
        if (name.isBlank()) {
            Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            if (id == null) {
                val ops = arrayListOf(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build(),
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                        .build()
                )
                if (phone.isNotBlank()) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build())
                }
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } else {
                // Update display name + first phone row.
                contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                    arrayOf(id.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val values = android.content.ContentValues().apply {
                            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                        }
                        contentResolver.update(
                            ContactsContract.Data.CONTENT_URI, values,
                            "${ContactsContract.Data._ID}=?", arrayOf(c.getLong(0).toString()))
                    }
                }
            }
        }.onFailure {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
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
}
