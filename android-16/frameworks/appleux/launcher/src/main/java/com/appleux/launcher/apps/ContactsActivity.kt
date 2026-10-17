package com.appleux.launcher.apps

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.VideoProfile
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.components.AppleuxButton
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxIconRegistry
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxSurface
import com.appleux.design.components.AppleuxText
import com.appleux.design.components.LayoutUtils
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * iOS 27 Contacts.
 *
 * ## IPSW provenance
 *
 * Every fact this surface is grounded in comes from the iOS 27 IPSW plists:
 *
 *  - `all-plists/root/System/Library/AppPlaceholders/Contacts.app/Info.plist`
 *    and `all-plists/root/private/var/staged_system_apps/Contacts.app/Info.plist`
 *    — `CFBundleIdentifier = com.apple.MobileAddressBook` (the bundle this
 *    replaces), `NSUserActivityTypes = com.apple.Contacts.ViewingCard` and
 *    `com.apple.Contacts.EditingCard` (the detail and editor below),
 *    `UIApplicationShortcutItems = "create-new-contact"` (the new-contact FAB),
 *    `CFBundleURLTypes = contact, contacts-sensitive`, and
 *    `CFBundleDocumentTypes = public.vcard` (the vCard import/export below).
 *  - `all-plists/root/System/Library/NanoPreferenceBundles/Applications/
 *    NanoContactsBridgeSettingsPaired.bundle/NanoContactsBridgeSettingsPaired.plist`
 *    — the three Contacts settings: **Sort Order** (`contactsSortOrder`,
 *    FIRST/LAST), **Display Order** (`NSPersonNameDefaultDisplayNameOrder`) and
 *    **Short Name** (`PersonShortName`). Sort Order is what the grouping letter
 *    follows; the list reads `ContactsContract.DISPLAY_NAME`, which the platform
 *    already formats per the other two settings.
 *
 * ## iOS 27 chrome
 *
 * Grouped sections with a single-letter header, 48dp rows, 40dp circular avatar
 * + 17sp body name + chevron, a 36dp rounded search pill, a 56dp systemBlue
 * new-contact FAB bottom-right, and a 34sp large title. Every size, colour and
 * type value comes from the shared tokens — nothing is hard-coded here.
 *
 * ## iOS 27 features added here
 *
 *  - **Contact Posters** — a full-screen gradient poster with an overlaid name
 *    and an editor (gradient, font, photo placement). The list avatar is a
 *    circular crop of the poster; `ContactsPosterStore` exposes the same
 *    gradient to the incoming-call screen. Configs persist in a private
 *    SharedPreferences store.
 *  - **Smart Duplicate Merging** — groups contacts by exact name, phone and
 *    email; shows a side-by-side comparison with conflicts highlighted; merges
 *    fields, keeps both numbers/emails, and records an undoable history.
 *  - **Enhanced Search** — matches name, phone, email, company, nickname and
 *    notes; a draggable A–Z index bar; section headers with counts; live
 *    suggestions; All / Favorites / Recents filter chips.
 *  - **Contact Profile** — a 200dp poster with the name overlaid, a four-button
 *    quick-action row (Call / Message / Video / Email), a details card of icon +
 *    label + value + action rows, and Edit / Share chrome.
 *  - **Groups** — Family / Friends / Work / custom groups with member counts,
 *    add/remove and group messaging.
 *  - **Import / Export** — vCard parse and generate, and vCard sharing.
 */
class ContactsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout

    /** Current search text; survives a rebuild of the list screen. */
    private var query: String = ""

    /** The active filter chip. */
    private var filter: Filter = Filter.ALL

    /** The scrolling list body. Rebuilt in place so the search field keeps focus. */
    private var listColumn: LinearLayout? = null

    /** Live search suggestions, rebuilt with the list. */
    private var suggestionColumn: LinearLayout? = null

    /** The scroll container, so the index bar can scroll to a section. */
    private var scrollView: ScrollView? = null

    /** The search field, so [showList] can restore [query] after a rebuild. */
    private var searchField: EditText? = null

    /** Section letter -> top offset inside [listColumn], recorded after layout. */
    private val sectionHeaderTops = mutableMapOf<String, Int>()

    private val posterPrefs by lazy { getSharedPreferences(POSTER_PREFS, MODE_PRIVATE) }
    private val groupPrefs by lazy { getSharedPreferences(GROUP_PREFS, MODE_PRIVATE) }
    private val mergePrefs by lazy { getSharedPreferences(MERGE_PREFS, MODE_PRIVATE) }

    /**
     * Recently Deleted. Deleting a contact writes a full snapshot here and
     * *then* removes it from the provider, so a restore can put the contact
     * back exactly as it was instead of asking the user to retype it.
     */
    private val trashPrefs by lazy { getSharedPreferences(TRASH_PREFS, MODE_PRIVATE) }
    private val trash = mutableListOf<TrashedContact>()

    private lateinit var openDocument: ActivityResultLauncher<Array<String>>

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
        openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importVcf(it) }
        }
        if (!have(Manifest.permission.READ_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 21)
        }
        // Purge before the first render, so the section can never offer a
        // restore for a contact that was already auto-deleted.
        trash.addAll(loadTrash())
        purgeExpiredTrash()
        showList()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        showList()
    }

    private fun have(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    // ---- Data model ---------------------------------------------------------

    /** One contact row, enriched with the fields the new surfaces render. */
    private data class Entry(
        val id: Long,
        val name: String,
        val starred: Boolean,
        val phone: String?,
        val phones: List<String> = emptyList(),
        val emails: List<String> = emptyList(),
        val company: String? = null,
        val nickname: String? = null,
        val notes: String? = null,
        val birthday: String? = null,
        val addresses: List<String> = emptyList(),
        val lastContacted: Long = 0L
    )

    /** Mutable accumulator so seven provider queries can fill one [Entry]. */
    private class EntryBuilder(
        val id: Long,
        val name: String,
        val starred: Boolean,
        val lastContacted: Long
    ) {
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val addresses = mutableListOf<String>()
        var company: String? = null
        var nickname: String? = null
        var notes: String? = null
        var birthday: String? = null

        fun build() = Entry(
            id, name, starred, phones.firstOrNull(), phones.toList(), emails.toList(),
            company, nickname, notes, birthday, addresses.toList(), lastContacted)
    }

    /** Loads every contact plus the data rows the new screens read. */
    private fun loadAll(): List<Entry> {
        if (!have(Manifest.permission.READ_CONTACTS)) return emptyList()
        val builders = linkedMapOf<Long, EntryBuilder>()
        runCatching {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.STARRED,
                    ContactsContract.Contacts.LAST_TIME_CONTACTED),
                null, null,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    builders[id] = EntryBuilder(id, c.getString(1).orEmpty(), c.getInt(2) == 1, c.getLong(3))
                }
            }
            queryData(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER)) { id, v -> builders[id]?.phones?.add(v) }
            queryData(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS)) { id, v -> builders[id]?.emails?.add(v) }
            queryData(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID,
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)) { id, v -> builders[id]?.addresses?.add(v) }
            queryData(ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Organization.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Organization.COMPANY),
                "${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)) { id, v ->
                if (builders[id]?.company.isNullOrBlank()) builders[id]?.company = v
            }
            queryData(ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Nickname.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Nickname.NAME),
                "${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)) { id, v ->
                if (builders[id]?.nickname.isNullOrBlank()) builders[id]?.nickname = v
            }
            queryData(ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Note.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Note.NOTE),
                "${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)) { id, v ->
                if (builders[id]?.notes.isNullOrBlank()) builders[id]?.notes = v
            }
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.CommonDataKinds.Event.MIMETYPE}=? AND " +
                    "${ContactsContract.CommonDataKinds.Event.TYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val v = c.getString(1).orEmpty()
                    if (v.isNotEmpty() && builders[id]?.birthday.isNullOrBlank()) builders[id]?.birthday = v
                }
            }
        }
        return builders.values.map { it.build() }
    }

    private fun queryData(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        onRow: (Long, String) -> Unit
    ) {
        runCatching {
            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val v = c.getString(1).orEmpty()
                    if (v.isNotEmpty()) onRow(id, v)
                }
            }
        }
    }

    // ---- List screen --------------------------------------------------------

    private fun showList() {
        root.removeAllViews()
        root.addView(largeTitle("Contacts"))
        root.addView(toolbarRow())
        root.addView(searchBar())

        val sug = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        suggestionColumn = sug
        root.addView(sug)
        root.addView(filterChips())

        val stage = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val listCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        listColumn = listCol
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scrollView = scroll
        scroll.addView(listCol)
        content.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        stage.addView(content)
        val bar = IndexBarView(this)
        bar.onLetter = { scrollToLetter(it) }
        stage.addView(bar, FrameLayout.LayoutParams(
            dp(ContactsTokens.INDEX_BAR_WIDTH_DP),
            FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = dp(SpacingTokens.INSETS_XS)
        })
        stage.addView(fab())
        root.addView(stage)

        populateList()
        // Restore the previous query. The watcher re-runs populateList, which
        // is idempotent, so this does not disturb the freshly built list.
        searchField?.setText(query)
    }

    /** Rebuilds the suggestions and list body — the search field keeps focus. */
    private fun populateList() {
        val col = listColumn ?: return
        col.removeAllViews()
        sectionHeaderTops.clear()

        val all = loadAll()
        val filtered = applyFilter(all)
        val shown = if (query.isBlank()) filtered else filtered.filter { matchesQuery(it, query) }

        renderSuggestions(shown)

        if (shown.isEmpty()) {
            col.addView(AppleuxAppChrome.emptyRow(
                this, if (all.isEmpty()) "No Contacts" else "No Results"))
            // Still render the trash: deleting your only contact empties the
            // list, and without this the contact would be unrecoverable.
            appendTrashSection(col)
            return
        }

        val sections = buildSections(shown)
        sections.forEachIndexed { si, (header, items) ->
            col.addView(sectionHeader(header, items.size).apply { tag = "$SECTION_TAG$header" })
            val lastItem = items.size - 1
            items.forEachIndexed { ii, e ->
                col.addView(contactRow(e))
                if (ii < lastItem) col.addView(divider())
            }
            if (si < sections.size - 1) col.addView(sectionSpacer())
        }
        appendTrashSection(col)
        recordSectionTops()
    }

    private fun applyFilter(all: List<Entry>): List<Entry> = when (filter) {
        Filter.ALL -> all
        Filter.FAVORITES -> all.filter { it.starred }
        Filter.RECENTS -> all.sortedByDescending { it.lastContacted }.take(MAX_RECENTS)
    }

    /** Matches name, phone, email, company, nickname and notes. */
    private fun matchesQuery(e: Entry, q: String): Boolean {
        val needle = q.trim()
        if (needle.isEmpty()) return true
        val haystack = listOfNotNull(e.name, e.phone, e.company, e.nickname, e.notes) +
            e.phones + e.emails
        return haystack.any { it.contains(needle, ignoreCase = true) }
    }

    /** Up to five live suggestions, shown as you type. */
    private fun renderSuggestions(shown: List<Entry>) {
        val sug = suggestionColumn ?: return
        sug.removeAllViews()
        if (query.isBlank()) return
        shown.take(MAX_SUGGESTIONS).forEach { e ->
            sug.addView(AppleuxListItem(this).apply {
                setTitle(e.name.ifEmpty { "Unknown" })
                setSubtitle(e.phone ?: e.emails.firstOrNull() ?: e.company ?: "")
                setIconDrawable(tintedDrawable("system_search", ColorTokens.systemBlue))
                setDividerVisible(false)
                setOnClickListener { showDetail(e) }
            })
        }
    }

    /**
     * Favorites first (iOS puts starred contacts in their own top section),
     * then one section per initial, `A`–`Z`, with non-letters under `#`.
     * The grouping letter follows the display name's first character, which is
     * iOS's default FIRST sort (`contactsSortOrder = 0`).
     */
    private fun buildSections(items: List<Entry>): List<Pair<String, List<Entry>>> {
        val out = mutableListOf<Pair<String, List<Entry>>>()
        if (filter == Filter.RECENTS) {
            out.add("Recents" to items)
            return out
        }
        val favorites = items.filter { it.starred }.sortedBy { it.name.lowercase() }
        if (favorites.isNotEmpty()) out.add("Favorites" to favorites)

        val byLetter = items.filter { !it.starred }.groupBy { sectionLetter(it.name) }
        for (letter in byLetter.keys.sorted()) {
            out.add(letter to byLetter[letter]!!.sortedBy { it.name.lowercase() })
        }
        return out
    }

    private fun sectionLetter(name: String): String {
        val first = name.trim().firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }

    private fun sectionHeader(text: String, count: Int? = null) = AppleuxText(this).apply {
        // A single-letter section header is shown uppercased; the "Favorites"
        // section keeps its title case. A count is appended when provided.
        val label = if (count != null) "$text $count" else text
        setText(if (count == null && text.length == 1) text.uppercase() else label)
        applyTypography(TypographyTokens.headline) // 17sp semibold
        setTextColor(ColorTokens.secondaryLabel)
        setPadding(
            dp(SpacingTokens.INSETS_XL),
            dp(SpacingTokens.CONTACTS_SECTION_HEADER_TOP),
            dp(SpacingTokens.INSETS_XL),
            dp(SpacingTokens.CONTACTS_SECTION_HEADER_BOTTOM)
        )
    }

    /** A smaller header for editor / tool screens. */
    private fun groupHeader(text: String) = AppleuxText(this).apply {
        setText(text)
        applyTypography(TypographyTokens.headline)
        setTextColor(ColorTokens.label)
        setPadding(
            dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD),
            dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_SM))
    }

    /**
     * One contact row: 40dp circular avatar, 17sp body name, chevron. 48dp tall,
     * with a hairline separator inset to the name's left edge. The avatar is a
     * circular crop of the contact's poster when one exists.
     */
    private fun contactRow(e: Entry): LinearLayout {
        val padH = dp(SpacingTokens.INSETS_XL)
        val avatarSize = dp(SpacingTokens.INSETS_XXXL5)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(SizingTokens.CONTROL_SIZE_XXL))
            setPadding(padH, 0, padH, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(ColorTokens.systemBackground)
            }

            addView(avatar(e.name, SpacingTokens.INSETS_XXXL5, posterColorsFor(e)).apply {
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    marginEnd = dp(SpacingTokens.INSETS_MD)
                }
            })
            addView(AppleuxText(this@ContactsActivity).apply {
                text = e.name.ifEmpty { "Unknown" }
                applyTypography(TypographyTokens.body)
                setTextColor(ColorTokens.label)
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(chevron())

            isClickable = true
            isFocusable = true
            setOnClickListener { showDetail(e) }
        }
    }

    private fun chevron(): View = ImageView(this).apply {
        AppleuxIconRegistry.getDrawableRes("system_chevron_right")?.let {
            setImageDrawable(ContextCompat.getDrawable(this@ContactsActivity, it)
                ?.mutate()?.apply { setTint(ColorTokens.tertiaryLabel) })
        }
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(
            dp(IconTokens.SIZE_SMALL), dp(IconTokens.SIZE_SMALL)
        ).apply { marginStart = dp(SpacingTokens.INSETS_MD) }
    }

    /** Inset separator, aligned to the name's left edge (past the avatar). */
    private fun divider(): View = View(this).apply {
        setBackgroundColor(ColorTokens.separator)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply {
            marginStart = dp(SpacingTokens.INSETS_XL) +
                dp(SpacingTokens.INSETS_XXXL5) + dp(SpacingTokens.INSETS_MD)
        }
    }

    private fun sectionSpacer(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(SpacingTokens.sectionSpacing))
    }

    // ---- Search ------------------------------------------------------------

    /** 36dp rounded pill over the list, with a search glyph and live filter. */
    private fun searchBar(): View {
        val hPad = dp(SpacingTokens.INSETS_XL)
        val vPad = dp(SpacingTokens.INSETS_MD)
        val pill = AppleuxSurface(this).apply {
            material = AppleuxSurface.MaterialType.SYSTEM_MATERIAL
            cornerRadius = LayoutUtils.dpToPxFloat(
                SpacingTokens.INSETS_XXXL4 / 2f, this@ContactsActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(SpacingTokens.INSETS_XXXL4)
            ).apply { setMargins(hPad, vPad, hPad, vPad) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(SpacingTokens.INSETS_MD), 0,
                dp(SpacingTokens.INSETS_MD), 0)
        }
        row.addView(tintedIcon("system_search", ColorTokens.secondaryLabel,
            IconTokens.SIZE_SMALL).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(IconTokens.SIZE_SMALL), dp(IconTokens.SIZE_SMALL)
            ).apply { marginEnd = dp(SpacingTokens.INSETS_SM) }
        })

        val field = EditText(this).apply {
            hint = "Search"
            setHintTextColor(ColorTokens.tertiaryLabel)
            setTextColor(ColorTokens.label)
            setBackground(null)
            isSingleLine = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TypographyTokens.body.size)
            typeface = AppleuxText.typefaceForWeight(TypographyTokens.body.fontWeight)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty()
                    populateList()
                }
            })
        }
        searchField = field
        row.addView(field)
        pill.addView(row)
        return pill
    }

    /** All / Favorites / Recents filter chips. */
    private fun filterChips(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_SM),
                dp(SpacingTokens.INSETS_XL), 0)
        }
        Filter.values().forEach { f ->
            addView(AppleuxButton(this@ContactsActivity).apply {
                setText(f.label)
                setButtonStyle(if (filter == f) AppleuxButton.Style.PRIMARY
                else AppleuxButton.Style.SECONDARY)
                setSize(AppleuxButton.Size.SMALL)
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(SpacingTokens.INSETS_SM)
                }
                setOnClickListener { filter = f; populateList() }
            })
        }
    }

    /** Records each section header's top offset so the index bar can scroll. */
    private fun recordSectionTops() {
        val col = listColumn ?: return
        col.post {
            sectionHeaderTops.clear()
            for (i in 0 until col.childCount) {
                val child = col.getChildAt(i)
                val tag = child.tag as? String ?: continue
                if (tag.startsWith(SECTION_TAG)) {
                    sectionHeaderTops[tag.removePrefix(SECTION_TAG)] = child.top
                }
            }
        }
    }

    private fun scrollToLetter(letter: String) {
        val top = sectionHeaderTops[letter] ?: return
        scrollView?.smoothScrollTo(0, top)
    }

    /**
     * The draggable A–Z index bar. One custom view rather than a stack of
     * labels so a continuous drag moves through letters without the touch
     * being handed off to a child.
     */
    private inner class IndexBarView(context: Context) : View(context) {
        private val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toList()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var selected = -1

        var onLetter: ((String) -> Unit)? = null

        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            val r = dp(SpacingTokens.INSETS_MD).toFloat()
            bgPaint.color = ColorTokens.systemFillSecondary
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, bgPaint)

            val cell = height.toFloat() / letters.size
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(11f).toFloat()
            for (i in letters.indices) {
                paint.color = if (i == selected) ColorTokens.systemBlue
                else ColorTokens.secondaryLabel
                val cy = cell * i + cell / 2f
                val fm = paint.fontMetrics
                val ty = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText(letters[i].toString(), width / 2f, ty, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val cell = height.toFloat() / letters.size
                    val idx = (event.y / cell).toInt().coerceIn(0, letters.size - 1)
                    if (idx != selected) {
                        selected = idx
                        invalidate()
                        onLetter?.invoke(letters[idx].toString())
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    selected = -1
                    invalidate()
                }
            }
            return true
        }
    }

    // ---- Recently Deleted ----------------------------------------------------

    private fun loadTrash(): List<TrashedContact> {
        val raw = runCatching {
            JSONArray(trashPrefs.getString(KEY_TRASH, "[]")!!)
        }.getOrDefault(JSONArray())
        val out = mutableListOf<TrashedContact>()
        for (i in 0 until raw.length()) {
            raw.optJSONObject(i)?.let { out.add(TrashedContact.fromJson(it)) }
        }
        return out
    }

    private fun saveTrash() {
        // Newest first and bounded: written on the main thread on every swipe.
        trash.sortByDescending { it.deletedAt }
        while (trash.size > MAX_TRASH_ENTRIES) trash.removeAt(trash.size - 1)
        trashPrefs.edit()
            .putString(KEY_TRASH, JSONArray(trash.map { it.toJson() }).toString())
            .apply()
    }

    private fun confirmMoveToTrash(e: Entry) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete ${e.name.ifEmpty { "Contact" }}?")
            .setMessage(
                "This contact moves to Recently Deleted, where it stays for 30 " +
                    "days. Swipe right on it there to restore.")
            .setPositiveButton("Delete") { _, _ -> moveContactToTrash(e) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Persist the snapshot, *then* delete. The order matters: a crash between
     * the two steps would otherwise destroy the contact with no record of it.
     */
    private fun moveContactToTrash(e: Entry) {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 27)
            return
        }
        trash.add(0, TrashedContact(
            name = e.name,
            phones = e.phones,
            emails = e.emails,
            account = accountNameOf(e.id),
            deletedAt = System.currentTimeMillis(),
            starred = e.starred,
            company = e.company,
            nickname = e.nickname,
            notes = e.notes,
            birthday = e.birthday,
            addresses = e.addresses
        ))
        saveTrash()
        deleteContact(e.id)
        toast("${e.name.ifEmpty { "Contact" }} moved to Recently Deleted")
        showList()
    }

    /** The account a contact belongs to, captured so the restore can report it. */
    private fun accountNameOf(contactId: Long): String = runCatching {
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.CONTACT_ID}=?",
            arrayOf(contactId.toString()), null
        )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" } ?: ""
    }.getOrDefault("")

    /**
     * Re-inserts the snapshot as a fresh raw contact. The provider assigns a
     * new contact id, which is unavoidable — the original row is gone — so the
     * record is dropped only after the insert actually succeeds.
     */
    private fun restoreTrashedContact(record: TrashedContact) {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 27)
            return
        }
        runCatching { insertSnapshot(record.toSnapshot()) }
            .onFailure { toast("Restore failed") }
            .onSuccess {
                trash.remove(record)
                saveTrash()
                toast("Restored ${record.name.ifEmpty { "contact" }}")
                showList()
            }
    }

    /**
     * Nothing is left in the provider to delete — the contact was removed when
     * it was trashed — so this discards the snapshot and is irreversible.
     */
    private fun deleteTrashedContactPermanently(record: TrashedContact) {
        trash.remove(record)
        saveTrash()
        toast("Deleted permanently")
        showList()
    }

    /** 30-day auto-purge, run at start-up before the section is rendered. */
    private fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_DAYS * MILLIS_PER_DAY
        val kept = trash.filter { it.deletedAt > cutoff }
        if (kept.size == trash.size) return
        trash.clear()
        trash.addAll(kept)
        saveTrash()
    }

    /** "17 Oct 2026" — the date half of iOS's "Deleted <date>" row. */
    private fun formatDeletedDate(deletedAt: Long): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(deletedAt))

    /**
     * The Recently Deleted section, appended below the A–Z sections.
     *
     * Rendered only when the trash is non-empty: iOS hides the section rather
     * than showing an empty one, and on a clean install a permanently empty
     * "Recently Deleted (0)" header reads as broken UI.
     *
     * The rows are a RecyclerView even though everything else here is plain
     * child views, because ItemTouchHelper needs real ViewHolders to drive the
     * swipe. Nested scrolling is off so it measures as one opaque block
     * inside the surrounding ScrollView instead of fighting it for drags.
     */
    private fun appendTrashSection(col: LinearLayout) {
        if (trash.isEmpty()) return

        col.addView(sectionSpacer())
        col.addView(sectionHeader("Recently Deleted", trash.size))

        val trashAdapter = TrashAdapter().apply { submit(trash) }
        val list = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(this@ContactsActivity)
            adapter = trashAdapter
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
        }
        attachTrashSwipe(list, trashAdapter)
        col.addView(list)
    }

    /**
     * Swipe actions, iOS semantics: right restores (systemGreen), left deletes
     * permanently (systemRed). The row is painted with the action colour as
     * the finger moves, so the colour is legible before the user commits.
     */
    private fun attachTrashSwipe(recycler: RecyclerView, trashAdapter: TrashAdapter) {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val position = vh.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val record = trashAdapter.itemAt(position) ?: return
                if (direction == ItemTouchHelper.RIGHT) {
                    restoreTrashedContact(record)
                } else {
                    deleteTrashedContactPermanently(record)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val row = vh.itemView
                when {
                    dX > 0f -> row.setBackgroundColor(ColorTokens.systemGreen)
                    dX < 0f -> row.setBackgroundColor(ColorTokens.systemRed)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        })
        helper.attachToRecyclerView(recycler)
    }

    /**
     * Recently Deleted rows: initials avatar, name, "Deleted <date>" — the
     * same three-part shape as a normal contact row, so the section reads as
     * part of the list rather than an afterthought.
     */
    private inner class TrashAdapter : RecyclerView.Adapter<TrashAdapter.Holder>() {

        private var items: List<TrashedContact> = emptyList()

        fun submit(list: List<TrashedContact>) {
            items = list
            notifyDataSetChanged()
        }

        fun itemAt(position: Int): TrashedContact? = items.getOrNull(position)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(this@ContactsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(SizingTokens.CONTROL_SIZE_XXL)
                )
                setPadding(
                    dp(SpacingTokens.INSETS_XL), 0, dp(SpacingTokens.INSETS_XL), 0)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(ColorTokens.systemBackground)
                }
            }

            val avatarSize = dp(SpacingTokens.INSETS_XXXL5)
            val av = avatar("", SpacingTokens.INSETS_XXXL5).apply {
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    marginEnd = dp(SpacingTokens.INSETS_MD)
                }
            }

            val textColumn = LinearLayout(this@ContactsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameView = AppleuxText(this@ContactsActivity).apply {
                applyTypography(TypographyTokens.body)
                setTextColor(ColorTokens.label)
                maxLines = 1
            }
            val subtitleView = AppleuxText(this@ContactsActivity).apply {
                applyTypography(TypographyTokens.footnote)
                setTextColor(ColorTokens.secondaryLabel)
                maxLines = 1
            }
            textColumn.addView(nameView)
            textColumn.addView(subtitleView)

            row.addView(av)
            row.addView(textColumn)
            return Holder(row, av, nameView, subtitleView)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.avatar.text = initialsOf(item.name)
            holder.avatar.setTextColor(ColorTokens.white)
            // Stable per-name colour, matching the live rows.
            (holder.avatar.background as? GradientDrawable)
                ?.setColor(avatarColor(item.name))
            holder.name.text = item.name.ifEmpty { "Unknown" }
            holder.subtitle.text = "Deleted ${formatDeletedDate(item.deletedAt)}"
            // Reset the swipe tint so a recycled row is not left green/red.
            holder.itemView.setBackgroundColor(ColorTokens.systemBackground)
        }

        override fun getItemCount(): Int = items.size

        inner class Holder(
            itemView: View,
            val avatar: AppleuxText,
            val name: AppleuxText,
            val subtitle: AppleuxText
        ) : RecyclerView.ViewHolder(itemView)
    }

    // ---- Tool chrome (Groups / Duplicates / Import) -------------------------

    private fun toolbarRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(SpacingTokens.INSETS_XL), 0, dp(SpacingTokens.INSETS_XL), 0)
        }
        addView(toolbarButton("Groups") { showGroups() })
        addView(toolbarButton("Duplicates") { showDuplicates() })
        addView(toolbarButton("Import / Export") { showImportExport() })
    }

    private fun toolbarButton(label: String, onClick: () -> Unit): AppleuxButton =
        AppleuxButton(this).apply {
            setText(label)
            setButtonStyle(AppleuxButton.Style.TERTIARY)
            setSize(AppleuxButton.Size.SMALL)
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(SpacingTokens.INSETS_SM)
            }
            setOnClickListener { onClick() }
        }

    // ---- New-contact FAB ---------------------------------------------------

    /** 56dp systemBlue circle, bottom-right, per the create-new-contact shortcut. */
    private fun fab(): View {
        val size = dp(SpacingTokens.INSETS_XXXL9)
        val margin = dp(SpacingTokens.INSETS_XL)
        val button = AppleuxSurface(this).apply {
            surfaceColor = ColorTokens.systemBlue
            cornerRadius = LayoutUtils.dpToPxFloat(
                SpacingTokens.INSETS_XXXL9 / 2f, this@ContactsActivity)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                setMargins(margin, margin, margin, margin)
            }
            contentDescription = "New Contact"
            setOnClickListener { showEditor(null) }
        }
        button.addView(ImageView(this).apply {
            AppleuxIconRegistry.getDrawableRes("system_add")?.let {
                setImageDrawable(ContextCompat.getDrawable(this@ContactsActivity, it))
            }
            layoutParams = FrameLayout.LayoutParams(
                dp(IconTokens.SIZE_LARGE), dp(IconTokens.SIZE_LARGE)
            ).apply { gravity = Gravity.CENTER }
            contentDescription = "New Contact"
        })
        return button
    }

    // ---- Detail (iOS 27 profile) -------------------------------------------

    private fun showDetail(e: Entry) {
        root.removeAllViews()
        root.addView(detailTopBar(e))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col)

        col.addView(buildPosterView(e, dp(ContactsTokens.POSTER_HEIGHT_DP)))
        col.addView(quickActionsRow(e))
        col.addView(detailsCard(e), cardParams())
        col.addView(groupsCard(e), cardParams())
        col.addView(AppleuxButton(this).apply {
            setText("Edit Poster")
            setButtonStyle(AppleuxButton.Style.SECONDARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = cardParams()
            setOnClickListener { showPosterEditor(e) }
        })

        root.addView(scroll)
    }

    /** Share (top-left, systemBlue outline) + Edit (top-right, systemBlue). */
    private fun detailTopBar(e: Entry): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(SizingTokens.NAVIGATION_BAR_HEIGHT))
        setPadding(dp(SpacingTokens.INSETS_XL), 0, dp(SpacingTokens.INSETS_XL), 0)

        addView(AppleuxButton(this@ContactsActivity).apply {
            setText("Share")
            setButtonStyle(AppleuxButton.Style.TERTIARY)
            setSize(AppleuxButton.Size.SMALL)
            borderWidth = dp(1f).toFloat()
            borderColor = ColorTokens.systemBlue
            setTextColor(ColorTokens.systemBlue)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { shareContact(e) }
        })
        addView(View(this@ContactsActivity).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        addView(AppleuxButton(this@ContactsActivity).apply {
            setText("Edit")
            setButtonStyle(AppleuxButton.Style.PRIMARY)
            setSize(AppleuxButton.Size.SMALL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { showEditor(e) }
        })
    }

    /** The 200dp gradient poster with the name overlaid. */
    private fun buildPosterView(e: Entry, heightPx: Int): View {
        val config = loadPoster(e.id) ?: ContactPoster(e.id, 0, "Regular", "Center", true)
        val posterGravity = when (config.placement) {
            "Top" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            "Bottom" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            else -> Gravity.CENTER
        }
        val container = FrameLayout(this).apply {
            background = posterDrawable(config)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
            setPadding(0, dp(SpacingTokens.INSETS_XL), 0, dp(SpacingTokens.INSETS_XL))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = posterGravity
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        if (config.showPhoto) {
            content.addView(avatar(e.name, SpacingTokens.INSETS_XXXL11).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(SpacingTokens.INSETS_XXXL11), dp(SpacingTokens.INSETS_XXXL11)).apply {
                    bottomMargin = dp(SpacingTokens.INSETS_MD)
                }
            })
        }
        content.addView(AppleuxText(this).apply {
            text = e.name.ifEmpty { "Unknown" }
            applyTypography(TypographyTokens.largeTitle) // 34sp
            setTextColor(ColorTokens.white)
            typeface = posterTypeface(config.fontStyle)
            gravity = Gravity.CENTER
            maxLines = 2
        })
        container.addView(content)
        return container
    }

    /** Four circular quick actions: Call, Message, Video, Email. */
    private fun quickActionsRow(e: Entry): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(SpacingTokens.INSETS_MD), 0, dp(SpacingTokens.INSETS_MD))
        }
        addView(quickAction("system_phone", "Call", ColorTokens.systemGreen) {
            e.phone?.let { dial(it) }
        })
        addView(quickAction("system_messages", "Message", ColorTokens.systemBlue) {
            e.phone?.let { message(it) }
        })
        addView(quickAction("system_camera", "Video", ColorTokens.systemIndigo) {
            e.phone?.let { videoCall(it) }
        })
        addView(quickAction("system_mail", "Email", ColorTokens.systemTeal) {
            e.emails.firstOrNull()?.let { email(it) }
        })
    }

    private fun quickAction(
        icon: String,
        label: String,
        tint: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val size = dp(ContactsTokens.QUICK_ACTION_SIZE_DP)
        col.addView(AppleuxSurface(this).apply {
            surfaceColor = ColorTokens.systemFillSecondary
            cornerRadius = LayoutUtils.dpToPxFloat(
                ContactsTokens.QUICK_ACTION_SIZE_DP / 2f, this@ContactsActivity)
            layoutParams = LinearLayout.LayoutParams(size, size)
            addView(ImageView(this@ContactsActivity).apply {
                AppleuxIconRegistry.getDrawableRes(icon)?.let {
                    setImageDrawable(ContextCompat.getDrawable(this@ContactsActivity, it)
                        ?.mutate()?.apply { setTint(tint) })
                }
                layoutParams = FrameLayout.LayoutParams(
                    dp(IconTokens.SIZE_MEDIUM), dp(IconTokens.SIZE_MEDIUM)
                ).apply { gravity = Gravity.CENTER }
            })
        })
        col.addView(AppleuxText(this).apply {
            text = label
            applyTypography(TypographyTokens.caption1)
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(SpacingTokens.INSETS_SM)
            }
        })
        return col
    }

    /** The details card: one icon + label + value + action row per field. */
    private fun detailsCard(e: Entry): AppleuxCard {
        val card = AppleuxCard(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        e.phones.forEachIndexed { i, num ->
            if (i > 0) col.addView(thinDivider())
            col.addView(detailRow("system_phone", "mobile", num,
                "system_phone", ColorTokens.systemGreen) { dial(num) })
        }
        e.emails.forEachIndexed { i, mail ->
            if (i > 0 || e.phones.isNotEmpty()) col.addView(thinDivider())
            col.addView(detailRow("system_mail", "email", mail,
                "system_mail", ColorTokens.systemBlue) { email(mail) })
        }
        e.addresses.forEachIndexed { i, addr ->
            if (i > 0 || e.phones.isNotEmpty() || e.emails.isNotEmpty()) col.addView(thinDivider())
            col.addView(detailRow("system_location", "address", addr,
                "system_location", ColorTokens.systemBlue) { maps(addr) })
        }
        e.birthday?.let { b ->
            if (col.childCount > 0) col.addView(thinDivider())
            col.addView(detailRow("system_calendar", "birthday", b, null, 0, null))
        }
        e.company?.let { c ->
            if (col.childCount > 0) col.addView(thinDivider())
            col.addView(detailRow("system_grid", "company", c, null, 0, null))
        }
        e.nickname?.let { n ->
            if (col.childCount > 0) col.addView(thinDivider())
            col.addView(detailRow("system_person", "nickname", n, null, 0, null))
        }
        e.notes?.let { n ->
            if (col.childCount > 0) col.addView(thinDivider())
            col.addView(detailRow("system_info", "notes", n, null, 0, null))
        }

        col.addView(thinDivider())
        col.addView(AppleuxListItem(this).apply {
            setTitle(if (e.starred) "Remove from Favorites" else "Add to Favorites")
            setIconDrawable(tintedDrawable("system_star", ColorTokens.systemYellow))
            setDividerVisible(false)
            setOnClickListener {
                setStarred(e, !e.starred)
                showDetail(e.copy(starred = !e.starred))
            }
        })

        // Trash-first delete. The snapshot is written to the trash index before
        // the provider row is removed, so the contact stays recoverable for
        // 30 days instead of vanishing on the first tap.
        col.addView(thinDivider())
        col.addView(AppleuxListItem(this).apply {
            setTitle("Delete Contact")
            setIconDrawable(tintedDrawable("system_trash", ColorTokens.systemRed))
            setDividerVisible(false)
            setOnClickListener { confirmMoveToTrash(e) }
        })

        card.addView(col)
        return card
    }

    private fun detailRow(
        iconName: String,
        label: String,
        value: String,
        actionIcon: String?,
        actionTint: Int,
        onAction: (() -> Unit)?
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD),
                dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD))
        }
        row.addView(tintedIcon(iconName, ColorTokens.secondaryLabel, IconTokens.SIZE_MEDIUM))
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(SpacingTokens.INSETS_MD)
            }
        }
        textCol.addView(AppleuxText(this).apply {
            text = label.replaceFirstChar { it.uppercase() }
            applyTypography(TypographyTokens.caption1)
            setTextColor(ColorTokens.secondaryLabel)
        })
        textCol.addView(AppleuxText(this).apply {
            text = value
            applyTypography(TypographyTokens.body)
            setTextColor(ColorTokens.label)
        })
        row.addView(textCol)
        if (actionIcon != null && onAction != null) {
            row.addView(circularIconButton(actionIcon, actionTint,
                ColorTokens.systemFillSecondary, SizingTokens.CONTROL_SIZE_LG, onAction))
        }
        return row
    }

    /** The groups this contact belongs to, plus an add-to-group action. */
    private fun groupsCard(e: Entry): AppleuxCard {
        val card = AppleuxCard(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val mine = loadGroups().filter { it.value.contains(e.id) }.keys

        col.addView(AppleuxText(this).apply {
            text = "Groups"
            applyTypography(TypographyTokens.headline)
            setTextColor(ColorTokens.label)
            setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD),
                dp(SpacingTokens.INSETS_XL), 0)
        })
        if (mine.isEmpty()) {
            col.addView(AppleuxText(this).apply {
                text = "No groups"
                applyTypography(TypographyTokens.footnote)
                setTextColor(ColorTokens.secondaryLabel)
                setPadding(dp(SpacingTokens.INSETS_XL), 0,
                    dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_MD))
            })
        } else {
            mine.forEachIndexed { i, name ->
                if (i > 0) col.addView(thinDivider())
                col.addView(AppleuxListItem(this).apply {
                    setTitle(name)
                    setIconDrawable(tintedDrawable("system_person", ColorTokens.systemBlue))
                    setDividerVisible(false)
                    setOnClickListener { showGroupDetail(name) }
                })
            }
        }
        col.addView(AppleuxButton(this).apply {
            setText("Add to Group")
            setButtonStyle(AppleuxButton.Style.TERTIARY)
            setSize(AppleuxButton.Size.SMALL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_SM),
                    0, dp(SpacingTokens.INSETS_MD))
            }
            setOnClickListener { assignContactToGroup(e) }
        })
        card.addView(col)
        return card
    }

    private fun actionRow(
        title: String,
        subtitle: String?,
        tint: Int,
        iconName: String,
        onClick: () -> Unit
    ) = AppleuxListItem(this).apply {
        setIconDrawable(tintedDrawable(iconName, tint))
        setTitle(title)
        setSubtitle(subtitle)
        setDividerVisible(false)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun thinDivider(): View = View(this).apply {
        setBackgroundColor(ColorTokens.separator)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { marginStart = dp(SpacingTokens.INSETS_XL) + dp(IconTokens.SIZE_MEDIUM) }
    }

    private fun cardParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            val m = dp(SpacingTokens.INSETS_XL)
            setMargins(m, dp(SpacingTokens.INSETS_MD), m, 0)
        }

    private fun circularIconButton(
        icon: String,
        tint: Int,
        fill: Int,
        sizeDp: Float,
        onClick: () -> Unit
    ): View {
        val size = dp(sizeDp)
        return AppleuxSurface(this).apply {
            surfaceColor = fill
            cornerRadius = LayoutUtils.dpToPxFloat(sizeDp / 2f, this@ContactsActivity)
            layoutParams = LinearLayout.LayoutParams(size, size)
            addView(ImageView(this@ContactsActivity).apply {
                AppleuxIconRegistry.getDrawableRes(icon)?.let {
                    setImageDrawable(ContextCompat.getDrawable(this@ContactsActivity, it)
                        ?.mutate()?.apply { setTint(tint) })
                }
                layoutParams = FrameLayout.LayoutParams(
                    dp(IconTokens.SIZE_MEDIUM), dp(IconTokens.SIZE_MEDIUM)
                ).apply { gravity = Gravity.CENTER }
            })
            isClickable = true
            isFocusable = true
            contentDescription = icon
            setOnClickListener { onClick() }
        }
    }

    private fun dial(num: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
        }
    }

    private fun message(num: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")))
        }
    }

    private fun videoCall(num: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).apply {
                putExtra("android.telecom.extra.START_CALL_WITH_VIDEO_STATE",
                    VideoProfile.STATE_BIDIRECTIONAL)
            })
        }.onFailure { toast("Cannot start video call") }
    }

    private fun email(addr: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$addr")))
        }.onFailure { toast("Cannot open mail") }
    }

    private fun maps(addr: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + Uri.encode(addr))))
        }.onFailure { toast("Cannot open maps") }
    }

    private fun setStarred(e: Entry, star: Boolean) {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 22)
            return
        }
        runCatching {
            val uri = android.content.ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI, e.id)
            val values = ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (star) 1 else 0)
            }
            contentResolver.update(uri, values, null, null)
        }
    }

    // ---- Contact posters ---------------------------------------------------

    private fun loadPoster(contactId: Long): ContactPoster? =
        ContactsPosterStore.load(this, contactId)

    private fun posterDrawable(config: ContactPoster): GradientDrawable {
        val gradients = ContactsPosterStore.gradients()
        val pair = gradients.getOrElse(config.gradientIndex) { gradients.first() }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(pair.first, pair.second)).apply {
            shape = GradientDrawable.RECTANGLE
        }
    }

    private fun posterTypeface(style: String): Typeface = when (style) {
        "Serif" -> Typeface.SERIF
        "Monospace" -> Typeface.MONOSPACE
        "Rounded" -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        "Semibold" -> AppleuxText.typefaceForWeight(600)
        else -> AppleuxText.typefaceForWeight(400)
    }

    private fun posterColorsFor(e: Entry): Pair<Int, Int>? {
        val poster = loadPoster(e.id) ?: return null
        val gradients = ContactsPosterStore.gradients()
        return gradients.getOrNull(poster.gradientIndex) ?: gradients.firstOrNull()
    }

    /** Full-screen poster view. */
    private fun showPoster(e: Entry) {
        root.removeAllViews()
        val config = loadPoster(e.id) ?: ContactPoster(e.id, 0, "Regular", "Center", true)

        val stage = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        stage.addView(FrameLayout(this).apply {
            background = posterDrawable(config)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)
            addView(LinearLayout(this@ContactsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT)
                if (config.showPhoto) {
                    addView(avatar(e.name, SpacingTokens.INSETS_XXXL15).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            dp(SpacingTokens.INSETS_XXXL15),
                            dp(SpacingTokens.INSETS_XXXL15)).apply {
                            bottomMargin = dp(SpacingTokens.INSETS_XL)
                        }
                    })
                }
                addView(AppleuxText(this@ContactsActivity).apply {
                    text = e.name.ifEmpty { "Unknown" }
                    applyTypography(TypographyTokens.largeTitle)
                    setTextColor(ColorTokens.white)
                    typeface = posterTypeface(config.fontStyle)
                    gravity = Gravity.CENTER
                })
            })
        })

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.TOP }
            setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_XXXL),
                dp(SpacingTokens.INSETS_XL), 0)
        }
        bar.addView(backRow { showDetail(e) })
        bar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        bar.addView(AppleuxButton(this).apply {
            setText("Edit")
            setButtonStyle(AppleuxButton.Style.TERTIARY)
            setSize(AppleuxButton.Size.SMALL)
            setTextColor(ColorTokens.white)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { showPosterEditor(e) }
        })
        stage.addView(bar)
        root.addView(stage)
    }

    /** Poster editor: gradient, font, photo placement and visibility. */
    private fun showPosterEditor(e: Entry) {
        root.removeAllViews()
        root.addView(backRow { showPoster(e) })
        root.addView(largeTitle("Contact Poster"))

        var config = loadPoster(e.id) ?: ContactPoster(e.id, 0, "Regular", "Center", true)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col)

        val preview = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ContactsTokens.POSTER_HEIGHT_DP))
            setPadding(0, dp(SpacingTokens.INSETS_XL), 0, 0)
        }
        fun refreshPreview() {
            preview.removeAllViews()
            preview.addView(buildPosterView(e, dp(ContactsTokens.POSTER_HEIGHT_DP)))
        }
        refreshPreview()
        col.addView(preview)

        col.addView(groupHeader("Gradient"))
        val gradientRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(SpacingTokens.INSETS_XL), 0, dp(SpacingTokens.INSETS_XL), 0)
            }
        }
        ContactsPosterStore.gradients().forEachIndexed { i, pair ->
            gradientRow.addView(View(this).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(pair.first, pair.second)).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(
                        SpacingTokens.INSETS_MD, this@ContactsActivity)
                }
                layoutParams = LinearLayout.LayoutParams(
                    dp(SpacingTokens.INSETS_XXXL7), dp(SpacingTokens.INSETS_XXXL7)).apply {
                    marginEnd = dp(SpacingTokens.INSETS_SM)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    config = config.copy(gradientIndex = i)
                    refreshPreview()
                }
            })
        }
        col.addView(gradientRow)

        col.addView(groupHeader("Font"))
        col.addView(chipRow("Font", POSTER_FONTS, config.fontStyle) {
            config = config.copy(fontStyle = it)
            refreshPreview()
        })
        col.addView(groupHeader("Photo Placement"))
        col.addView(chipRow("Placement", POSTER_PLACEMENTS, config.placement) {
            config = config.copy(placement = it)
            refreshPreview()
        })
        col.addView(chipRow("Photo", listOf("Show", "Hide"), if (config.showPhoto) "Show" else "Hide") {
            config = config.copy(showPhoto = it == "Show")
            refreshPreview()
        })

        root.addView(scroll)
        root.addView(AppleuxButton(this).apply {
            setText("Save Poster")
            setButtonStyle(AppleuxButton.Style.PRIMARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = cardParams().apply { bottomMargin = dp(SpacingTokens.INSETS_XL) }
            setOnClickListener {
                ContactsPosterStore.save(this@ContactsActivity, config)
                toast("Poster saved")
                showPoster(e)
            }
        })
    }

    private fun chipRow(
        title: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_SM),
                dp(SpacingTokens.INSETS_XL), 0)
        }
        options.forEach { opt ->
            addView(AppleuxButton(this@ContactsActivity).apply {
                setText(opt)
                setButtonStyle(if (opt == selected) AppleuxButton.Style.PRIMARY
                else AppleuxButton.Style.SECONDARY)
                setSize(AppleuxButton.Size.SMALL)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(SpacingTokens.INSETS_SM)
                }
                setOnClickListener { onSelect(opt) }
            })
        }
    }

    // ---- Duplicate merging -------------------------------------------------

    private data class DuplicateGroup(val key: String, val reason: String, val entries: List<Entry>)

    /** Groups contacts by exact name, phone and email. */
    private fun findDuplicates(all: List<Entry>): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        val claimed = mutableSetOf<Long>()

        all.filter { it.name.isNotBlank() }
            .groupBy { it.name.trim().lowercase() }
            .filter { it.value.size > 1 }
            .toSortedMap()
            .forEach { (key, list) ->
                groups.add(DuplicateGroup(key, "Same name", list.sortedBy { it.name.lowercase() }))
                claimed += list.map { it.id }
            }

        val phoneMap = linkedMapOf<String, MutableList<Entry>>()
        all.filter { !claimed.contains(it.id) }.forEach { e ->
            e.phones.forEach { p ->
                phoneMap.getOrPut(normalizePhone(p)) { mutableListOf() }.add(e)
            }
        }
        phoneMap.filter { it.key.isNotEmpty() && it.value.distinctBy { it.id }.size > 1 }
            .toSortedMap()
            .forEach { (key, list) ->
                val es = list.distinctBy { it.id }
                groups.add(DuplicateGroup(key, "Same phone number", es))
                claimed += es.map { it.id }
            }

        val emailMap = linkedMapOf<String, MutableList<Entry>>()
        all.filter { !claimed.contains(it.id) }.forEach { e ->
            e.emails.forEach { m ->
                emailMap.getOrPut(m.trim().lowercase()) { mutableListOf() }.add(e)
            }
        }
        emailMap.filter { it.key.isNotEmpty() && it.value.distinctBy { it.id }.size > 1 }
            .toSortedMap()
            .forEach { (key, list) ->
                val es = list.distinctBy { it.id }
                groups.add(DuplicateGroup(key, "Same email", es))
                claimed += es.map { it.id }
            }

        return groups
    }

    private fun normalizePhone(p: String): String {
        val digits = p.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /** Field labels present with conflicting values across a duplicate group. */
    private fun mergeConflicts(group: DuplicateGroup): List<String> {
        val fields = mutableListOf<String>()
        if (group.entries.map { it.name.trim().lowercase() }.distinct().size > 1) fields.add("name")
        if (group.entries.mapNotNull { it.company?.trim()?.lowercase() }
                .filter { it.isNotEmpty() }.distinct().size > 1) fields.add("company")
        if (group.entries.mapNotNull { it.nickname?.trim()?.lowercase() }
                .filter { it.isNotEmpty() }.distinct().size > 1) fields.add("nickname")
        if (group.entries.mapNotNull { it.birthday?.trim()?.lowercase() }
                .filter { it.isNotEmpty() }.distinct().size > 1) fields.add("birthday")
        return fields
    }

    private fun showDuplicates() {
        root.removeAllViews()
        root.addView(backRow { showList() })
        root.addView(largeTitle("Duplicates"))

        val groups = findDuplicates(loadAll())
        if (groups.isEmpty()) {
            root.addView(AppleuxAppChrome.emptyRow(this, "No Duplicates"))
        } else {
            groups.forEach { group ->
                val card = AppleuxCard(this)
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                col.addView(groupHeader("${group.reason} · ${group.entries.size}"))
                col.addView(comparisonRow(group))
                val conflicts = mergeConflicts(group)
                if (conflicts.isNotEmpty()) {
                    col.addView(AppleuxText(this).apply {
                        text = "Conflicts: " + conflicts.joinToString(", ")
                        applyTypography(TypographyTokens.footnote)
                        setTextColor(ColorTokens.systemOrange)
                        setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_SM),
                            dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_SM))
                    })
                }
                col.addView(AppleuxButton(this).apply {
                    setText("Merge ${group.entries.size} Contacts")
                    setButtonStyle(AppleuxButton.Style.PRIMARY)
                    setSize(AppleuxButton.Size.SMALL)
                    layoutParams = cardParams().apply {
                        bottomMargin = dp(SpacingTokens.INSETS_MD)
                    }
                    setOnClickListener { mergeGroup(group) }
                })
                card.addView(col)
                root.addView(card, cardParams())
            }
        }

        val m = dp(SpacingTokens.INSETS_XL)
        root.addView(AppleuxButton(this).apply {
            setText("Undo Last Merge")
            setButtonStyle(AppleuxButton.Style.TERTIARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(m, dp(SpacingTokens.INSETS_LG), m, dp(SpacingTokens.INSETS_XL))
            }
            setOnClickListener { undoMerge() }
        })
    }

    /** Side-by-side comparison of the first two contacts in a group. */
    private fun comparisonRow(group: DuplicateGroup): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        setPadding(dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_SM),
            dp(SpacingTokens.INSETS_XL), dp(SpacingTokens.INSETS_SM))
        group.entries.take(2).forEachIndexed { i, e ->
            if (i > 0) {
                addView(View(this@ContactsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dp(SpacingTokens.INSETS_MD), 1)
                })
            }
            addView(LinearLayout(this@ContactsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(AppleuxText(this@ContactsActivity).apply {
                    text = e.name.ifEmpty { "Unknown" }
                    applyTypography(TypographyTokens.headline)
                    setTextColor(ColorTokens.label)
                })
                (e.phone ?: e.emails.firstOrNull())?.let {
                    addView(AppleuxText(this@ContactsActivity).apply {
                        text = it
                        applyTypography(TypographyTokens.footnote)
                        setTextColor(ColorTokens.secondaryLabel)
                    })
                }
                e.company?.let {
                    addView(AppleuxText(this@ContactsActivity).apply {
                        text = it
                        applyTypography(TypographyTokens.footnote)
                        setTextColor(ColorTokens.secondaryLabel)
                    })
                }
            })
        }
    }

    /** Keeps the first contact, folds the rest in, records an undo snapshot. */
    private fun mergeGroup(group: DuplicateGroup) {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 26)
            return
        }
        if (group.entries.size < 2) return
        val kept = group.entries.first()
        val removed = group.entries.drop(1)
        val keptSnapshot = snapshotOf(kept)
        val removedSnapshots = removed.map { snapshotOf(it) }

        runCatching {
            removed.forEach { r ->
                r.phones.forEach { upsertData(kept.id, PhoneMime, PhoneNumberColumn, it) }
                r.emails.forEach { upsertData(kept.id, EmailMime, EmailAddressColumn, it) }
                r.addresses.forEach {
                    upsertData(kept.id, PostalMime, PostalAddressColumn, it)
                }
                if (kept.company.isNullOrBlank()) {
                    upsertData(kept.id, OrgMime, OrgCompanyColumn, r.company.orEmpty())
                }
                if (kept.nickname.isNullOrBlank()) {
                    upsertData(kept.id, NicknameMime, NicknameNameColumn, r.nickname.orEmpty())
                }
                if (kept.notes.isNullOrBlank()) {
                    upsertData(kept.id, NoteMime, NoteColumn, r.notes.orEmpty())
                }
                if (kept.birthday.isNullOrBlank()) {
                    upsertData(kept.id, EventMime, EventStartColumn, r.birthday.orEmpty())
                }
                deleteContact(r.id)
            }
            recordMerge(MergeRecord(keptSnapshot, removedSnapshots))
        }.onFailure { toast("Merge failed") }
        toast("Merged ${group.entries.size} contacts")
        showDuplicates()
    }

    /** Deletes the merged contact and restores both pre-merge snapshots. */
    private fun undoMerge() {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 25)
            return
        }
        val records = loadMergeRecords()
        val last = records.lastOrNull()
        if (last == null) {
            toast("Nothing to undo")
            return
        }
        records.removeAt(records.size - 1)
        saveMergeRecords(records)
        runCatching {
            deleteContact(last.kept.id)
            insertSnapshot(last.kept)
            last.removed.forEach { insertSnapshot(it) }
        }.onFailure { toast("Undo failed") }
        toast("Merge undone")
        showDuplicates()
    }

    private fun snapshotOf(e: Entry) = ContactSnapshot(
        e.id, e.name, e.starred, e.phones, e.emails,
        e.company, e.nickname, e.notes, e.birthday, e.addresses)

    private fun loadMergeRecords(): MutableList<MergeRecord> {
        val a = runCatching {
            JSONArray(mergePrefs.getString(KEY_MERGES, "[]")!!)
        }.getOrDefault(JSONArray())
        return mergeRecordsFromJson(a).toMutableList()
    }

    private fun saveMergeRecords(records: List<MergeRecord>) {
        mergePrefs.edit()
            .putString(KEY_MERGES, JSONArray(records.map { it.toJson() }).toString())
            .apply()
    }

    private fun recordMerge(record: MergeRecord) {
        val records = loadMergeRecords()
        records.add(record)
        saveMergeRecords(records)
    }

    // ---- Groups ------------------------------------------------------------

    private fun loadGroups(): MutableMap<String, MutableList<Long>> {
        val map = linkedMapOf<String, MutableList<Long>>()
        runCatching {
            val json = JSONObject(groupPrefs.getString(KEY_GROUPS, DEFAULT_GROUPS_JSON)!!)
            json.keys().forEach { k ->
                map[k] = json.optJSONArray(k).toStringList().map { it.toLong() }.toMutableList()
            }
        }
        return map
    }

    private fun saveGroups(map: Map<String, List<Long>>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, JSONArray(v.map { it.toString() })) }
        groupPrefs.edit().putString(KEY_GROUPS, json.toString()).apply()
    }

    private fun showGroups() {
        root.removeAllViews()
        root.addView(backRow { showList() })
        root.addView(largeTitle("Groups"))

        val groups = loadGroups()
        if (groups.isEmpty()) {
            root.addView(AppleuxAppChrome.emptyRow(this, "No Groups"))
        } else {
            val card = AppleuxCard(this)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            groups.keys.forEachIndexed { i, name ->
                if (i > 0) col.addView(thinDivider())
                col.addView(AppleuxListItem(this).apply {
                    setTitle(name)
                    setSubtitle("${groups[name]?.size ?: 0} members")
                    setIconDrawable(tintedDrawable("system_person", ColorTokens.systemBlue))
                    setDividerVisible(false)
                    setOnClickListener { showGroupDetail(name) }
                })
            }
            card.addView(col)
            root.addView(card, cardParams())
        }

        root.addView(AppleuxButton(this).apply {
            setText("New Group")
            setButtonStyle(AppleuxButton.Style.SECONDARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = cardParams().apply { bottomMargin = dp(SpacingTokens.INSETS_XL) }
            setOnClickListener { promptNewGroup() }
        })
    }

    private fun promptNewGroup() {
        val input = EditText(this).apply { hint = "Group name" }
        android.app.AlertDialog.Builder(this)
            .setTitle("New Group")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val groups = loadGroups()
                    groups[name] = mutableListOf()
                    saveGroups(groups)
                    showGroups()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGroupDetail(name: String) {
        root.removeAllViews()
        root.addView(backRow { showGroups() })
        root.addView(largeTitle(name))

        val ids = loadGroups()[name] ?: mutableListOf()
        val members = loadAll().filter { ids.contains(it.id) }
        if (members.isEmpty()) {
            root.addView(AppleuxAppChrome.emptyRow(this, "No Members"))
        } else {
            val card = AppleuxCard(this)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            members.forEachIndexed { i, m ->
                if (i > 0) col.addView(thinDivider())
                col.addView(AppleuxListItem(this).apply {
                    setTitle(m.name)
                    setSubtitle(m.phone ?: m.emails.firstOrNull() ?: "")
                    setIconDrawable(tintedDrawable("system_person", ColorTokens.systemBlue))
                    setDividerVisible(false)
                    setOnClickListener { showDetail(m) }
                })
            }
            card.addView(col)
            root.addView(card, cardParams())
        }

        val m = dp(SpacingTokens.INSETS_XL)
        root.addView(AppleuxButton(this).apply {
            setText("Add Contact")
            setButtonStyle(AppleuxButton.Style.SECONDARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(m, dp(SpacingTokens.INSETS_MD), m, 0)
            }
            setOnClickListener { addToGroupPicker(name) }
        })
        root.addView(AppleuxButton(this).apply {
            setText("Message Group")
            setButtonStyle(AppleuxButton.Style.PRIMARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(m, dp(SpacingTokens.INSETS_MD), m, 0)
            }
            setOnClickListener { messageGroup(name) }
        })
        root.addView(AppleuxButton(this).apply {
            setText("Remove Members")
            setButtonStyle(AppleuxButton.Style.TERTIARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(m, dp(SpacingTokens.INSETS_MD), m, dp(SpacingTokens.INSETS_XL))
            }
            setOnClickListener { removeMembersPicker(name) }
        })
    }

    private fun addToGroupPicker(name: String) {
        val all = loadAll()
        if (all.isEmpty()) {
            toast("No contacts")
            return
        }
        val groups = loadGroups()
        val current = groups[name] ?: mutableListOf()
        val checked = BooleanArray(all.size) { current.contains(all[it].id) }
        android.app.AlertDialog.Builder(this)
            .setTitle("Add to $name")
            .setMultiChoiceItems(all.map { it.name }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Done") { _, _ ->
                val ids = all.filterIndexed { i, _ -> checked[i] }.map { it.id }.toMutableList()
                groups[name] = ids
                saveGroups(groups)
                showGroupDetail(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeMembersPicker(name: String) {
        val ids = loadGroups()[name] ?: mutableListOf()
        val members = loadAll().filter { ids.contains(it.id) }
        if (members.isEmpty()) {
            toast("No members")
            return
        }
        val checked = BooleanArray(members.size) { false }
        android.app.AlertDialog.Builder(this)
            .setTitle("Remove from $name")
            .setMultiChoiceItems(members.map { it.name }.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Remove") { _, _ ->
                val groups = loadGroups()
                val remaining = members.filterIndexed { i, _ -> !checked[i] }.map { it.id }.toMutableList()
                groups[name] = remaining
                saveGroups(groups)
                showGroupDetail(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun assignContactToGroup(e: Entry) {
        val groups = loadGroups()
        if (groups.isEmpty()) {
            toast("Create a group first")
            return
        }
        val names = groups.keys.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Add ${e.name} to group")
            .setItems(names) { _, which ->
                val name = names[which]
                val ids = groups[name] ?: mutableListOf()
                if (!ids.contains(e.id)) {
                    ids.add(e.id)
                    groups[name] = ids
                    saveGroups(groups)
                }
                showDetail(e)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun messageGroup(name: String) {
        val ids = loadGroups()[name] ?: return
        val numbers = loadAll().filter { ids.contains(it.id) }
            .mapNotNull { it.phone }.filter { it.isNotBlank() }
        if (numbers.isEmpty()) {
            toast("No phone numbers in group")
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_SENDTO,
                Uri.parse("smsto:" + numbers.joinToString(";"))))
        }.onFailure { toast("Cannot message group") }
    }

    // ---- Import / export ---------------------------------------------------

    private fun showImportExport() {
        root.removeAllViews()
        root.addView(backRow { showList() })
        root.addView(largeTitle("Import & Export"))

        val m = dp(SpacingTokens.INSETS_XL)
        root.addView(AppleuxButton(this).apply {
            setText("Import vCard")
            setButtonStyle(AppleuxButton.Style.SECONDARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(m, dp(SpacingTokens.INSETS_MD), m, 0)
            }
            setOnClickListener {
                openDocument.launch(arrayOf("text/vcard", "text/x-vcard", "text/plain", "*/*"))
            }
        })
        root.addView(AppleuxButton(this).apply {
            setText("Export All as vCard")
            setButtonStyle(AppleuxButton.Style.PRIMARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(m, dp(SpacingTokens.INSETS_MD), m, dp(SpacingTokens.INSETS_XL))
            }
            setOnClickListener { exportAll() }
        })
    }

    private fun importVcf(uri: Uri) {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 24)
            return
        }
        runCatching {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return
            val parsed = parseVcf(text)
            if (parsed.isEmpty()) {
                toast("No contacts found")
                return
            }
            val n = insertVcf(parsed)
            toast("Imported $n contacts")
            showList()
        }.onFailure { toast("Import failed") }
    }

    /** Unfolds continuation lines and parses `BEGIN:VCARD` blocks. */
    private fun parseVcf(text: String): List<VcfContact> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val unfolded = mutableListOf<String>()
        for (line in lines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.size - 1] += line.substring(1)
            } else {
                unfolded.add(line)
            }
        }

        val out = mutableListOf<VcfContact>()
        var name = ""
        var company = ""
        var nickname = ""
        var notes = ""
        var birthday = ""
        var inCard = false
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val addresses = mutableListOf<String>()

        for (line in unfolded) {
            val trimmed = line.trim()
            when {
                trimmed.equals("BEGIN:VCARD", true) -> {
                    inCard = true
                    name = ""; company = ""; nickname = ""; notes = ""; birthday = ""
                    phones.clear(); emails.clear(); addresses.clear()
                }
                trimmed.equals("END:VCARD", true) -> {
                    if (inCard) {
                        out.add(VcfContact(
                            name, phones.toList(), emails.toList(),
                            company.ifEmpty { null }, nickname.ifEmpty { null },
                            notes.ifEmpty { null }, birthday.ifEmpty { null },
                            addresses.toList()))
                    }
                    inCard = false
                }
                inCard && trimmed.contains(":") -> {
                    val colon = trimmed.indexOf(':')
                    val left = trimmed.substring(0, colon)
                    val value = trimmed.substring(colon + 1)
                    when (left.substringBefore(';').uppercase()) {
                        "FN" -> name = value
                        "N" -> if (name.isEmpty()) {
                            name = value.split(";").filter { it.isNotBlank() }
                                .reversed().joinToString(" ")
                        }
                        "TEL" -> phones.add(value)
                        "EMAIL" -> emails.add(value)
                        "ORG" -> company = value.split(";").firstOrNull().orEmpty()
                        "NICKNAME" -> nickname = value
                        "NOTE" -> notes = value
                        "BDAY" -> birthday = value
                        "ADR" -> addresses.add(value.split(";")
                            .filter { it.isNotBlank() }.joinToString(", "))
                    }
                }
            }
        }
        return out
    }

    private fun insertVcf(contacts: List<VcfContact>): Int {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 24)
            return 0
        }
        var count = 0
        contacts.forEach { c ->
            if (c.name.isBlank() && c.phones.isEmpty()) return@forEach
            runCatching {
                val ops = arrayListOf(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build(),
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            c.name.ifBlank { c.phones.firstOrNull() ?: "Contact" })
                        .build()
                )
                c.phones.forEach { ops.add(phoneOp(it)) }
                c.emails.forEach { ops.add(emailOp(it)) }
                if (!c.company.isNullOrBlank()) ops.add(companyOp(c.company))
                if (!c.nickname.isNullOrBlank()) ops.add(nicknameOp(c.nickname))
                if (!c.notes.isNullOrBlank()) ops.add(noteOp(c.notes))
                if (!c.birthday.isNullOrBlank()) ops.add(birthdayOp(c.birthday))
                c.addresses.forEach {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, it)
                        .build())
                }
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                count++
            }
        }
        return count
    }

    private fun buildVcf(e: Entry): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\n")
        sb.append("VERSION:3.0\n")
        sb.append("FN:").append(vcfEscape(e.name)).append("\n")
        val parts = e.name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val family = parts.lastOrNull().orEmpty()
        val given = parts.dropLast(1).joinToString(" ")
        sb.append("N:").append(vcfEscape(family)).append(";")
            .append(vcfEscape(given)).append(";;;\n")
        e.phones.forEach { sb.append("TEL;TYPE=CELL:").append(vcfEscape(it)).append("\n") }
        e.emails.forEach { sb.append("EMAIL;TYPE=INTERNET:").append(vcfEscape(it)).append("\n") }
        e.company?.let { if (it.isNotBlank()) sb.append("ORG:").append(vcfEscape(it)).append("\n") }
        e.nickname?.let { if (it.isNotBlank()) sb.append("NICKNAME:").append(vcfEscape(it)).append("\n") }
        e.notes?.let { if (it.isNotBlank()) sb.append("NOTE:").append(vcfEscape(it)).append("\n") }
        e.birthday?.let { if (it.isNotBlank()) sb.append("BDAY:").append(vcfEscape(it)).append("\n") }
        e.addresses.forEach { sb.append("ADR;TYPE=HOME:").append(vcfEscape(it)).append("\n") }
        sb.append("END:VCARD\n")
        return sb.toString()
    }

    private fun vcfEscape(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;")
            .replace(",", "\\,").replace("\n", "\\n")

    private fun exportAll() {
        val all = loadAll()
        if (all.isEmpty()) {
            toast("No contacts")
            return
        }
        val content = all.joinToString("\n") { buildVcf(it) }
        val file = writeExport(content, "contacts")
        toast("Exported ${all.size} contacts to ${file.name}")
        shareVcf(content, "contacts")
    }

    private fun writeExport(content: String, name: String): File {
        val dir = File(filesDir, "exports")
        dir.mkdirs()
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "contact" }
        val file = File(dir, "$safe.vcf")
        file.writeText(content)
        return file
    }

    private fun shareContact(e: Entry) {
        shareVcf(buildVcf(e), e.name.ifEmpty { "Contact" })
    }

    private fun shareVcf(content: String, name: String) {
        val file = writeExport(content, name)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/vcard"
            putExtra(Intent.EXTRA_SUBJECT, name)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Share $name")) }
            .onFailure { toast("Cannot share") }
    }

    // ---- Editor ------------------------------------------------------------

    private fun showEditor(existing: Entry?) {
        if (!have(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), 23)
            return
        }
        root.removeAllViews()
        root.addView(backRow { if (existing != null) showDetail(existing) else showList() })
        root.addView(largeTitle(if (existing != null) "Edit Contact" else "New Contact"))

        val m = dp(SpacingTokens.INSETS_XL)
        fun field(hint: String, initial: String, inputType: Int = InputType.TYPE_CLASS_TEXT) =
            EditText(this).apply {
                this.hint = hint
                setText(initial)
                isSingleLine = true
                this.inputType = inputType
                setTextColor(ColorTokens.label)
                setHintTextColor(ColorTokens.tertiaryLabel)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TypographyTokens.body.size)
                typeface = AppleuxText.typefaceForWeight(TypographyTokens.body.fontWeight)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(m, dp(SpacingTokens.INSETS_MD), m, 0)
                }
            }

        val nameField = field("Name", existing?.name.orEmpty())
        val phoneField = field("Phone", existing?.phone.orEmpty(), InputType.TYPE_CLASS_PHONE)
        val emailField = field("Email", existing?.emails?.firstOrNull().orEmpty(),
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val companyField = field("Company", existing?.company.orEmpty())
        val nicknameField = field("Nickname", existing?.nickname.orEmpty())
        val notesField = field("Notes", existing?.notes.orEmpty())
        val birthdayField = field("Birthday", existing?.birthday.orEmpty())

        root.addView(nameField)
        root.addView(phoneField)
        root.addView(emailField)
        root.addView(companyField)
        root.addView(nicknameField)
        root.addView(notesField)
        root.addView(birthdayField)
        root.addView(AppleuxButton(this).apply {
            setText("Save")
            setButtonStyle(AppleuxButton.Style.PRIMARY)
            setSize(AppleuxButton.Size.MEDIUM)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(m, dp(SpacingTokens.INSETS_LG), m, dp(SpacingTokens.INSETS_XL))
            }
            setOnClickListener {
                saveContact(
                    existing,
                    nameField.text.toString(),
                    phoneField.text.toString(),
                    emailField.text.toString(),
                    companyField.text.toString(),
                    nicknameField.text.toString(),
                    notesField.text.toString(),
                    birthdayField.text.toString())
                showList()
            }
        })
    }

    private fun saveContact(
        existing: Entry?,
        name: String,
        phone: String,
        email: String,
        company: String,
        nickname: String,
        notes: String,
        birthday: String
    ) {
        if (name.isBlank()) {
            toast("Name required")
            return
        }
        runCatching {
            if (existing == null) {
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
                if (phone.isNotBlank()) ops.add(phoneOp(phone))
                if (email.isNotBlank()) ops.add(emailOp(email))
                if (company.isNotBlank()) ops.add(companyOp(company))
                if (nickname.isNotBlank()) ops.add(nicknameOp(nickname))
                if (notes.isNotBlank()) ops.add(noteOp(notes))
                if (birthday.isNotBlank()) ops.add(birthdayOp(birthday))
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } else {
                upsertData(existing.id, StructuredNameMime, StructuredNameDisplayColumn, name)
                upsertData(existing.id, PhoneMime, PhoneNumberColumn, phone)
                upsertData(existing.id, EmailMime, EmailAddressColumn, email)
                upsertData(existing.id, OrgMime, OrgCompanyColumn, company)
                upsertData(existing.id, NicknameMime, NicknameNameColumn, nickname)
                upsertData(existing.id, NoteMime, NoteColumn, notes)
                upsertData(existing.id, EventMime, EventStartColumn, birthday)
            }
        }.onFailure { toast("Save failed") }
    }

    /** Updates the first row of a mime type, inserts when absent, deletes when blank. */
    private fun upsertData(contactId: Long, mimeType: String, valueColumn: String, value: String) {
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(contactId.toString(), mimeType), null
        )?.use { c ->
            if (value.isBlank()) {
                if (c.moveToFirst()) {
                    contentResolver.delete(ContactsContract.Data.CONTENT_URI,
                        "${ContactsContract.Data._ID}=?", arrayOf(c.getLong(0).toString()))
                }
            } else if (c.moveToFirst()) {
                val values = ContentValues().apply { put(valueColumn, value) }
                contentResolver.update(ContactsContract.Data.CONTENT_URI, values,
                    "${ContactsContract.Data._ID}=?", arrayOf(c.getLong(0).toString()))
            } else {
                val values = ContentValues().apply {
                    put(ContactsContract.Data.CONTACT_ID, contactId)
                    put(ContactsContract.Data.MIMETYPE, mimeType)
                    put(valueColumn, value)
                    if (mimeType == PhoneMime) {
                        put(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    }
                    if (mimeType == EventMime) {
                        put(ContactsContract.CommonDataKinds.Event.TYPE,
                            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
                    }
                }
                contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
            }
        }
    }

    private fun phoneOp(number: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            .build()

    private fun emailOp(address: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, address)
            .build()

    private fun companyOp(company: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
            .build()

    private fun nicknameOp(nickname: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, nickname)
            .build()

    private fun noteOp(notes: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
            .build()

    private fun birthdayOp(birthday: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, birthday)
            .withValue(ContactsContract.CommonDataKinds.Event.TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)
            .build()

    /** Re-inserts a snapshot as a fresh raw contact; returns the new contact id. */
    private fun insertSnapshot(s: ContactSnapshot): Long {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.STARRED, if (s.starred) 1 else 0)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    s.name.ifBlank { "Contact" })
                .build()
        )
        s.phones.forEach { ops.add(phoneOp(it)) }
        s.emails.forEach { ops.add(emailOp(it)) }
        s.addresses.forEach {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, it)
                .build())
        }
        if (!s.company.isNullOrBlank()) ops.add(companyOp(s.company))
        if (!s.nickname.isNullOrBlank()) ops.add(nicknameOp(s.nickname))
        if (!s.notes.isNullOrBlank()) ops.add(noteOp(s.notes))
        if (!s.birthday.isNullOrBlank()) ops.add(birthdayOp(s.birthday))
        val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return results.firstOrNull()?.uri?.lastPathSegment?.toLongOrNull() ?: -1L
    }

    private fun deleteContact(id: Long) {
        runCatching {
            contentResolver.delete(ContactsContract.Contacts.CONTENT_URI,
                "${ContactsContract.Contacts._ID}=?", arrayOf(id.toString()))
        }
    }

    // ---- Shared chrome -----------------------------------------------------

    private fun largeTitle(text: String) = AppleuxText(this).apply {
        setText(text)
        applyTypography(TypographyTokens.largeTitle)
        setTextColor(ColorTokens.label)
        setPadding(
            dp(SpacingTokens.INSETS_XL),
            dp(SpacingTokens.INSETS_XL),
            dp(SpacingTokens.INSETS_XL),
            dp(SpacingTokens.INSETS_MD)
        )
    }

    private fun backRow(back: () -> Unit) = Button(this, null,
        android.R.attr.borderlessButtonStyle).apply {
        text = "‹ Back"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TypographyTokens.body.size)
        setTextColor(ColorTokens.systemBlue)
        gravity = Gravity.START
        setPadding(dp(SpacingTokens.INSETS_XL), 0, dp(SpacingTokens.INSETS_XL), 0)
        setOnClickListener { back() }
    }

    // ---- Helpers -----------------------------------------------------------

    /** Circular initial avatar, tinted from a stable per-name system colour. */
    private fun avatar(
        name: String,
        sizeDp: Float,
        colors: Pair<Int, Int>? = null
    ): AppleuxText {
        val size = dp(sizeDp)
        return AppleuxText(this).apply {
            text = initialsOf(name)
            applyTypography(TypographyTokens.title3)
            setTextColor(ColorTokens.white)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (colors != null) setColors(intArrayOf(colors.first, colors.second))
                else setColor(avatarColor(name))
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    private fun avatarColor(name: String): Int {
        val palette = intArrayOf(
            ColorTokens.systemBlue, ColorTokens.systemGreen, ColorTokens.systemOrange,
            ColorTokens.systemPurple, ColorTokens.systemPink, ColorTokens.systemTeal,
            ColorTokens.systemIndigo
        )
        val hash = name.hashCode().let { if (it < 0) -it else it }
        return palette[hash % palette.size]
    }

    private fun tintedIcon(name: String, tint: Int, sizeDp: Float): ImageView =
        ImageView(this).apply {
            AppleuxIconRegistry.getDrawableRes(name)?.let {
                setImageDrawable(ContextCompat.getDrawable(this@ContactsActivity, it)
                    ?.mutate()?.apply { setTint(tint) })
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    private fun tintedDrawable(name: String, tint: Int): Drawable? {
        val res = AppleuxIconRegistry.getDrawableRes(name) ?: return null
        return ContextCompat.getDrawable(this, res)?.mutate()?.apply { setTint(tint) }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Float): Int = LayoutUtils.dpToPx(value, this)

    private enum class Filter(val label: String) {
        ALL("All"), FAVORITES("Favorites"), RECENTS("Recents")
    }

    private companion object {
        const val POSTER_PREFS = "appleux_contacts_posters"
        const val GROUP_PREFS = "appleux_contacts_groups"
        const val MERGE_PREFS = "appleux_contacts_merges"

        /** Recently Deleted: SharedPreferences file `contacts_trash`, one JSON
         *  array of [TrashedContact] under [KEY_TRASH]. */
        const val TRASH_PREFS = "contacts_trash"
        const val KEY_TRASH = "trash"
        const val TRASH_RETENTION_DAYS = 30L
        const val MAX_TRASH_ENTRIES = 200
        const val MILLIS_PER_DAY = 86_400_000L

        const val KEY_GROUPS = "groups"
        const val KEY_MERGES = "merges"
        const val DEFAULT_GROUPS_JSON = "{\"Family\":[],\"Friends\":[],\"Work\":[]}"
        const val SECTION_TAG = "section:"

        const val MAX_SUGGESTIONS = 5
        const val MAX_RECENTS = 50

        // Content-provider column aliases, so the editor and merge paths read
        // as data rather than a wall of fully-qualified constants.
        const val PhoneMime = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        const val PhoneNumberColumn = ContactsContract.CommonDataKinds.Phone.NUMBER
        const val EmailMime = ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
        const val EmailAddressColumn = ContactsContract.CommonDataKinds.Email.ADDRESS
        const val PostalMime = ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
        const val PostalAddressColumn =
            ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS
        const val OrgMime = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        const val OrgCompanyColumn = ContactsContract.CommonDataKinds.Organization.COMPANY
        const val NicknameMime = ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
        const val NicknameNameColumn = ContactsContract.CommonDataKinds.Nickname.NAME
        const val NoteMime = ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
        const val NoteColumn = ContactsContract.CommonDataKinds.Note.NOTE
        const val EventMime = ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
        const val EventStartColumn = ContactsContract.CommonDataKinds.Event.START_DATE
        const val StructuredNameMime =
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        const val StructuredNameDisplayColumn =
            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME

        val POSTER_FONTS = listOf("Regular", "Semibold", "Serif", "Monospace", "Rounded")
        val POSTER_PLACEMENTS = listOf("Top", "Center", "Bottom")
    }
}

/** One contact parsed from a vCard. */
private data class VcfContact(
    val name: String,
    val phones: List<String>,
    val emails: List<String>,
    val company: String?,
    val nickname: String?,
    val notes: String?,
    val birthday: String?,
    val addresses: List<String>
)

/** Full field snapshot taken before a merge so it can be undone. */
private data class ContactSnapshot(
    val id: Long,
    val name: String,
    val starred: Boolean,
    val phones: List<String>,
    val emails: List<String>,
    val company: String?,
    val nickname: String?,
    val notes: String?,
    val birthday: String?,
    val addresses: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("starred", starred)
        put("phones", JSONArray(phones))
        put("emails", JSONArray(emails))
        put("company", company.orEmpty())
        put("nickname", nickname.orEmpty())
        put("notes", notes.orEmpty())
        put("birthday", birthday.orEmpty())
        put("addresses", JSONArray(addresses))
    }

    companion object {
        fun fromJson(o: JSONObject): ContactSnapshot = ContactSnapshot(
            o.optLong("id"),
            o.optString("name"),
            o.optBoolean("starred"),
            o.optJSONArray("phones").toStringList(),
            o.optJSONArray("emails").toStringList(),
            o.optString("company").ifEmpty { null },
            o.optString("nickname").ifEmpty { null },
            o.optString("notes").ifEmpty { null },
            o.optString("birthday").ifEmpty { null },
            o.optJSONArray("addresses").toStringList()
        )
    }
}

/**
 * One Recently Deleted contact.
 *
 * The trash index stores `{name, phones[], emails[], account, deletedAt}`.
 * The remaining fields are carried too, because [toSnapshot] feeds the
 * existing `insertSnapshot` path and a restore that dropped company, notes,
 * birthday or addresses would be a half-restore the user would notice.
 *
 * There is no id: the provider row is deleted when the contact is trashed, so
 * a restore always mints a new contact id.
 */
private data class TrashedContact(
    val name: String,
    val phones: List<String>,
    val emails: List<String>,
    val account: String,
    val deletedAt: Long,
    val starred: Boolean = false,
    val company: String? = null,
    val nickname: String? = null,
    val notes: String? = null,
    val birthday: String? = null,
    val addresses: List<String> = emptyList()
) {
    /** Rebuilt as the snapshot the pre-existing restore path understands. */
    fun toSnapshot() = ContactSnapshot(
        id = -1L,
        name = name,
        starred = starred,
        phones = phones,
        emails = emails,
        company = company,
        nickname = nickname,
        notes = notes,
        birthday = birthday,
        addresses = addresses
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("phones", JSONArray(phones))
        put("emails", JSONArray(emails))
        put("account", account)
        put("deletedAt", deletedAt)
        put("starred", starred)
        put("company", company ?: JSONObject.NULL)
        put("nickname", nickname ?: JSONObject.NULL)
        put("notes", notes ?: JSONObject.NULL)
        put("birthday", birthday ?: JSONObject.NULL)
        put("addresses", JSONArray(addresses))
    }

    companion object {
        fun fromJson(o: JSONObject): TrashedContact = TrashedContact(
            name = o.optString("name"),
            phones = o.optJSONArray("phones").toStringList(),
            emails = o.optJSONArray("emails").toStringList(),
            account = o.optString("account"),
            deletedAt = o.optLong("deletedAt"),
            starred = o.optBoolean("starred"),
            company = o.optString("company").ifEmpty { null },
            nickname = o.optString("nickname").ifEmpty { null },
            notes = o.optString("notes").ifEmpty { null },
            birthday = o.optString("birthday").ifEmpty { null },
            addresses = o.optJSONArray("addresses").toStringList()
        )
    }
}

/** One undoable merge: the kept contact plus every contact folded into it. */
private data class MergeRecord(
    val kept: ContactSnapshot,
    val removed: List<ContactSnapshot>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("kept", kept.toJson())
        put("removed", JSONArray(removed.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject): MergeRecord = MergeRecord(
            ContactSnapshot.fromJson(o.optJSONObject("kept") ?: JSONObject()),
            snapshotsFromJson(o.optJSONArray("removed"))
        )
    }
}

private fun snapshotsFromJson(a: JSONArray?): List<ContactSnapshot> {
    if (a == null) return emptyList()
    val out = mutableListOf<ContactSnapshot>()
    for (i in 0 until a.length()) {
        a.optJSONObject(i)?.let { out.add(ContactSnapshot.fromJson(it)) }
    }
    return out
}

private fun mergeRecordsFromJson(a: JSONArray?): List<MergeRecord> {
    if (a == null) return emptyList()
    val out = mutableListOf<MergeRecord>()
    for (i in 0 until a.length()) {
        a.optJSONObject(i)?.let { out.add(MergeRecord.fromJson(it)) }
    }
    return out
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until length()) out.add(optString(i))
    return out
}

/**
 * Contact poster configuration: gradient pair, font, photo placement and
 * visibility, persisted per contact id.
 */
data class ContactPoster(
    val contactId: Long,
    val gradientIndex: Int,
    val fontStyle: String,
    val placement: String,
    val showPhoto: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("contactId", contactId)
        put("gradientIndex", gradientIndex)
        put("fontStyle", fontStyle)
        put("placement", placement)
        put("showPhoto", showPhoto)
    }

    companion object {
        fun fromJson(o: JSONObject): ContactPoster = ContactPoster(
            o.optLong("contactId"),
            o.optInt("gradientIndex"),
            o.optString("fontStyle", "Regular"),
            o.optString("placement", "Center"),
            o.optBoolean("showPhoto", true)
        )
    }
}

/**
 * Persistence for contact posters, shared with the incoming-call screen so a
 * caller's poster can be their call background.
 *
 * The gradient list is resolved on every call rather than cached: [ColorTokens]
 * members are theme-dependent getters, so a list captured at class-init time
 * would freeze the palette across a dark-mode flip.
 */
object ContactsPosterStore {
    private const val PREFS = "appleux_contacts_posters"
    private const val KEY = "posters"

    fun gradients(): List<Pair<Int, Int>> = listOf(
        ColorTokens.systemBlue to ColorTokens.systemIndigo,
        ColorTokens.systemPink to ColorTokens.systemPurple,
        ColorTokens.systemOrange to ColorTokens.systemPink,
        ColorTokens.systemTeal to ColorTokens.systemBlue,
        ColorTokens.systemGreen to ColorTokens.systemTeal,
        ColorTokens.systemPurple to ColorTokens.systemIndigo,
        ColorTokens.systemYellow to ColorTokens.systemOrange,
        ColorTokens.systemRed to ColorTokens.systemPink
    )

    fun save(context: Context, poster: ContactPoster) {
        val all = loadAll(context)
        all.put(poster.contactId.toString(), poster.toJson())
        prefs(context).edit().putString(KEY, all.toString()).apply()
    }

    fun load(context: Context, contactId: Long): ContactPoster? =
        loadAll(context).optJSONObject(contactId.toString())
            ?.let { ContactPoster.fromJson(it) }

    /** The poster background for a phone number, or null when there is none. */
    fun backgroundFor(context: Context, number: String?): Drawable? {
        if (number.isNullOrBlank()) return null
        val id = lookupContactId(context, number) ?: return null
        val poster = load(context, id) ?: return null
        val pair = gradients().getOrElse(poster.gradientIndex) { gradients().first() }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(pair.first, pair.second))
    }

    private fun loadAll(context: Context): JSONObject = runCatching {
        JSONObject(prefs(context).getString(KEY, "{}")!!)
    }.getOrDefault(JSONObject())

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun lookupContactId(context: Context, number: String): Long? {
        var id: Long? = null
        runCatching {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
                .buildUpon().appendPath(number).build()
            context.contentResolver.query(uri,
                arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { c ->
                if (c.moveToFirst()) id = c.getLong(0)
            }
        }
        return id
    }
}

/**
 * Contacts metrics that have no design-system counterpart.
 *
 * PROVENANCE — **AppleUX-chosen.** Apple publishes no numeric spec for the
 * contact poster, the quick-action row or the index bar, so these are our
 * measurements of the iOS 27 Contacts surface expressed in dp. The 48dp row and
 * 40dp avatar reuse [SizingTokens]; the 34sp name reuses
 * [TypographyTokens.largeTitle].
 */
object ContactsTokens {
    /** Poster height in the contact profile: 200dp. */
    const val POSTER_HEIGHT_DP: Float = 200f

    /** Quick-action circle diameter. */
    const val QUICK_ACTION_SIZE_DP: Float = 56f

    /** A–Z index bar width. */
    const val INDEX_BAR_WIDTH_DP: Float = 20f
}

/**
 * Values transcribed from the iOS 27 `Contacts.app` `Info.plist` files in the
 * IPSW harvest.
 */
private object ContactsInfo {
    /** CFBundleIdentifier — the bundle this activity replaces. */
    const val BUNDLE_ID = "com.apple.MobileAddressBook"

    /** CFBundleDisplayName — the large navigation title. */
    const val DISPLAY_NAME = "Contacts"

    /** NSUserActivityTypes — the detail and editor user activities. */
    object Activities {
        const val VIEWING_CARD = "com.apple.Contacts.ViewingCard"
        const val EDITING_CARD = "com.apple.Contacts.EditingCard"
    }

    /** UIApplicationShortcutItems — the new-contact shortcut. */
    object Shortcuts {
        const val CREATE_NEW_CONTACT = "com.apple.contacts.create-new-contact"
    }

    /** CFBundleURLTypes — the private URL schemes. */
    object Schemes {
        const val CONTACT = "contact"
        const val CONTACTS_SENSITIVE = "contacts-sensitive"
    }

    /** CFBundleDocumentTypes — the vCard document type. */
    const val VCARD_MIME = "public.vcard"
}
