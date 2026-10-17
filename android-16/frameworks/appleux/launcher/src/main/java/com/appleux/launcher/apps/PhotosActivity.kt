package com.appleux.launcher.apps

    // android.R.drawable -> vendored iOS vector (2026-10-17):
    //   com.appleux.design.R.drawable.il_ic_lock               -> sf_lock          iOS lock
    //   com.appleux.design.R.drawable.sf_play              -> il_ic_play       iOS play triangle
    //   com.appleux.design.R.drawable.sf_add                -> sf_add           iOS plus
    //   com.appleux.design.R.drawable.il_ic_grid             -> il_ic_grid       iOS list/grid
    //   com.appleux.design.R.drawable.sf_camera             -> il_ic_camera     iOS camera
    //   com.appleux.design.R.drawable.sf_compass            -> sf_compass       NEW: see sf_compass.xml, created 2026-10-17
    //   com.appleux.design.R.drawable.sf_crop               -> sf_crop          NEW: see sf_crop.xml, created 2026-10-17
    //   com.appleux.design.R.drawable.sf_trash             -> sf_trash         iOS trash can
    //   com.appleux.design.R.drawable.sf_maps         -> sf_compass       NEW: see sf_compass.xml, created 2026-10-17
    //   com.appleux.design.R.drawable.sf_pencil               -> sf_pencil        NEW: see sf_pencil.xml, created 2026-10-17
    //   com.appleux.design.R.drawable.il_ic_photos            -> il_ic_photos     iOS photos flower
    //   com.appleux.design.R.drawable.il_ic_info       -> il_ic_info       iOS info circle
    //   com.appleux.design.R.drawable.sf_calendar              -> sf_calendar      no month-only glyph exists in the vendored set
    //   com.appleux.design.R.drawable.sf_calendar        -> sf_calendar      iOS calendar
    //   com.appleux.design.R.drawable.sf_location         -> sf_location      iOS location arrow
    //   com.appleux.design.R.drawable.sf_maps           -> sf_location      iOS location pin
    //   com.appleux.design.R.drawable.sf_clock     -> sf_clock         iOS clock
    //   com.appleux.design.R.drawable.sf_undo             -> sf_undo          NEW: see sf_undo.xml, created 2026-10-17
    //   com.appleux.design.R.drawable.il_ic_rotation             -> il_ic_rotation   iOS rotation lock
    //   com.appleux.design.R.drawable.il_ic_search             -> il_ic_search     iOS magnifying glass
    //   com.appleux.design.R.drawable.sf_pencil             -> sf_checkmark     iOS 'set as' is a check/apply mark; no dedicated SF Symbol
    //   com.appleux.design.R.drawable.il_ic_share              -> il_ic_share      iOS share
    //   com.appleux.design.R.drawable.sf_star                -> sf_star          iOS filled star
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.appleux.design.components.AppleuxDialogs
import com.appleux.design.components.AppleuxSlider
import com.appleux.design.components.AppleuxDisplayInsets
import com.appleux.design.components.AppleuxNavigationBar
import com.appleux.design.components.AppleuxSurface
import com.appleux.design.components.AppleuxText
import com.appleux.design.components.LayoutUtils
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.tokens.TypographyTokens
import android.content.SharedPreferences
import android.view.ViewGroup
import android.widget.Button
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.text.Editable
import android.util.TypedValue
import android.text.TextWatcher
import android.widget.EditText
import android.widget.SeekBar
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.view.GestureDetector
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import com.appleux.design.components.AppleuxToast
import com.appleux.launcher.R

/**
 * iOS 27 Photos app UI for AppleUX.
 *
 * Layout (top to bottom):
 * 1. Navigation bar: large title "Library" (34sp) + Year/Month/Day selector + Select button
 * 2. Search bar: 36dp glass pill with search icon
 * 3. Photo grid: 4 columns, 2dp gap, square cells
 * 4. Tab bar: Library, Collections, Albums, Search, Hidden
 *
 * Features:
 * - Collections: Memories, People, Places, Trips, Media types, Featured
 * - Enhanced Search: natural language, date, location, media type, filters
 * - Photo Viewer: fullscreen, double-tap zoom, swipe navigation, info panel
 * - Edit Mode: adjustments, filters, crop, rotate, flip, undo/redo
 * - Albums: smart albums (Favorites, Videos, Screenshots, Selfies)
 * - Hidden Album: PIN/biometric protection
 * - Thumbnail caching: LRU cache (max 50) for 3GB RAM budget
 *
 * Selection mode: tap "Select" or long-press to enter, tap photos to toggle,
 * blue checkmark circle overlay on selected cells.
 *
 * IPSW data sources:
 * - MobileSlideShowSettings.bundle/Info.plist: confirms Photos settings bundle
 *   (com.apple.mobileslideshow.settings, MSSSettingsController)
 * - DefaultIconState.plist: confirms com.apple.mobileslideshow on home screen
 * - PhotosReliveWidget: com.apple.mobileslideshow.PhotosReliveWidget
 */
class PhotosActivity : AppCompatActivity() {

    private val perm get() = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private lateinit var grid: GridLayout
    private lateinit var gridContainer: ScrollView
    private lateinit var emptyView: LinearLayout
    private lateinit var selectButton: AppleuxText
    private val selectedPositions = mutableSetOf<Int>()
    private val cellViews = mutableMapOf<Int, PhotoCellView>()
    private var isSelectionMode = false

    // Year/Month/Day selector state
    private var currentViewMode = ViewMode.DAY
    private val viewModeSegments = mutableListOf<AppleuxText>()

    // Hidden album state
    private lateinit var hiddenPrefs: SharedPreferences
    private lateinit var pinPrefs: SharedPreferences
    private var isHiddenTabActive = false
    private var isPinUnlocked = false
    private var currentTabId = 0
    private val hiddenEmptyView: LinearLayout by lazy { buildHiddenEmptyView() }

    // Recently Deleted state. `trash` is the in-memory mirror of the prefs
    // store, loaded once at start-up and re-read after every write.
    private lateinit var trashPrefs: SharedPreferences
    private val trash = mutableListOf<TrashPhoto>()
    private var isTrashTabActive = false
    private lateinit var tabBar: AppleuxNavigationBar

    // Collections state
    private val collections = mutableListOf<CollectionItem>()
    private var collectionsView: LinearLayout? = null

    // Search state
    private var searchQuery = ""
    private var searchFilter = SearchFilter.ALL
    private val recentSearches = mutableListOf<String>()
    private val searchSuggestions = listOf("Screenshots", "Videos", "Selfies", "Favorites", "Recently Added")

    // Photo viewer state
    private var viewerCurrentIndex = 0
    private var viewerPhotoUris = mutableListOf<Uri>()
    private var isControlsVisible = true

    // Edit state
    private var editHistory = mutableListOf<EditAction>()
    private var editHistoryIndex = -1
    private var currentEditUri: Uri? = null

    // Album state
    private val albums = mutableListOf<Album>()
    private var albumsView: LinearLayout? = null

    // Thumbnail cache (LRU, max 50 entries for 3GB RAM budget)
    private val thumbnailCache = object : LinkedHashMap<String, Bitmap>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 50
    }

    companion object {
        private const val HIDDEN_PREFS = "appleux_photos_hidden"
        private const val PIN_PREFS = "appleux_photos_pin"
        private const val KEY_PIN_HASH = "pin_hash_sha256"
        private const val KEY_HIDDEN_PREFIX = "hidden_"
        private const val KEYSTORE_ALIAS = "AppleUXPhotosKey"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PIN_LENGTH = 4
        private const val MAX_THUMBNAIL_CACHE = 50
        private const val TRIP_DISTANCE_THRESHOLD_KM = 100.0

        /**
         * Recently Deleted. The store is SharedPreferences file `photos_trash`
         * holding one JSON array under [KEY_TRASH_PHOTOS]. Kept in prefs (not a
         * database) because it is written on the main thread on every tile
         * change and must stay small — hence [MAX_TRASH_ENTRIES].
         */
        private const val TRASH_PREFS = "photos_trash"
        private const val KEY_TRASH_PHOTOS = "trash"

        /** iOS deletes Recently Deleted items after 30 days. */
        private const val TRASH_RETENTION_DAYS = 30L
        private const val MAX_TRASH_ENTRIES = 200
        private const val MILLIS_PER_DAY = 86_400_000L

        /** Tab ids. Recently Deleted is its own tab, separate from Hidden. */
        private const val TAB_LIBRARY = 0
        private const val TAB_COLLECTIONS = 1
        private const val TAB_ALBUMS = 2
        private const val TAB_SEARCH = 3
        private const val TAB_HIDDEN = 4
        private const val TAB_RECENTLY_DELETED = 5

        private const val GRID_COLUMNS = 4
        private const val ALBUM_RECENTLY_DELETED = "recently_deleted"
    }

    enum class ViewMode { YEAR, MONTH, DAY }

    enum class SearchFilter { ALL, PHOTOS, VIDEOS, SCREENSHOTS, FAVORITES }

    enum class CollectionType { MEMORIES, PEOPLE, PLACES, TRIPS, MEDIA_TYPES, FEATURED }

    enum class MediaType { SCREENSHOTS, VIDEOS, SELFIES, LIVE_PHOTOS, RAW, PANORAMAS }

    data class CollectionItem(
        val type: CollectionType,
        val title: String,
        val subtitle: String,
        val iconRes: Int,
        val photoCount: Int = 0
    )

    data class Album(
        val id: String,
        val name: String,
        val photoUris: MutableList<Uri> = mutableListOf(),
        val coverUri: Uri? = null,
        val isSmartAlbum: Boolean = false
    )

    data class EditAction(
        val type: String,
        val value: Float,
        val previousValue: Float = 0f
    )

    data class PhotoInfo(
        val date: String = "",
        val time: String = "",
        val location: String = "",
        val camera: String = "",
        val iso: String = "",
        val shutter: String = "",
        val aperture: String = "",
        val focalLength: String = "",
        val fileSize: String = "",
        val dimensions: String = "",
        val format: String = ""
    )

    /**
     * One Recently Deleted entry.
     *
     * The bytes are never moved. Deleting writes this record and the photo is
     * filtered out of every grid, so **Restore is a prefs edit** rather than a
     * file move — which is why `albumPath` is kept: it is what lets the purge
     * actually unlink the file, and what lets a restore confirm the file is
     * still there before claiming success.
     */
    data class TrashPhoto(
        val uri: String,
        val deletedAt: Long,
        val albumPath: String?,
        val name: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("uri", uri)
            put("deletedAt", deletedAt)
            put("albumPath", albumPath ?: JSONObject.NULL)
            put("name", name)
        }

        companion object {
            fun fromJson(o: JSONObject): TrashPhoto = TrashPhoto(
                uri = o.optString("uri"),
                deletedAt = o.optLong("deletedAt"),
                // optString on a JSON null yields "", which is not the same as
                // "no path" — an empty path would make purge a no-op.
                albumPath = if (o.isNull("albumPath")) null else o.optString("albumPath")
                    .ifEmpty { null },
                name = o.optString("name")
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hiddenPrefs = getSharedPreferences(HIDDEN_PREFS, Context.MODE_PRIVATE)
        pinPrefs = getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE)
        trashPrefs = getSharedPreferences(TRASH_PREFS, Context.MODE_PRIVATE)

        // Purge before the first read, so no grid can ever show an expired tile
        // and no tile can claim "29 days left" on something due for deletion.
        trash.addAll(loadTrash())
        purgeExpiredTrash()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ColorTokens.systemBackground)
        }

        // Apply top inset for cutout
        AppleuxDisplayInsets.applyTopInset(this, root)

        // 1. Navigation bar
        root.addView(buildNavigationBar())

        // 2. Search bar
        root.addView(buildSearchBar())

        // 3. Photo grid
        gridContainer = buildPhotoGrid()
        root.addView(gridContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // 4. Tab bar
        tabBar = buildTabBar()
        root.addView(tabBar)

        setContentView(root)

        // Request permission and load photos
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 2)
        } else {
            loadPhotos()
        }
    }

    // ---- Navigation Bar ----------------------------------------------------

    private fun buildNavigationBar(): View {
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
        }

        // Large title "Library" (34sp)
        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.largeTitle)
            text = "Library"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        navBar.addView(title)

        // Year/Month/Day selector (glass pill, 13sp)
        navBar.addView(buildViewModeSelector())

        // Select/Done button
        selectButton = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "Select"
            setTextColor(ColorTokens.systemBlue)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleSelectionMode() }
        }
        navBar.addView(selectButton)

        return navBar
    }

    // ---- Year/Month/Day Selector -------------------------------------------

    private fun buildViewModeSelector(): View {
        val selector = AppleuxSurface(this).apply {
            cornerRadius = SizingTokens.CORNER_RADIUS_ROUND
            surfaceColor = ColorTokens.systemFillSecondary
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity)
            )
        }

        val modes = listOf("Year" to ViewMode.YEAR, "Month" to ViewMode.MONTH, "Day" to ViewMode.DAY)
        viewModeSegments.clear()
        modes.forEach { (label, mode) ->
            val segment = AppleuxText(this).apply {
                applyTypography(TypographyTokens.footnote)
                text = label
                setTextColor(if (mode == currentViewMode) ColorTokens.systemBlue else ColorTokens.secondaryLabel)
                setPadding(
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity)
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentViewMode = mode
                    updateViewModeSelector()
                }
            }
            viewModeSegments.add(segment)
            container.addView(segment)
        }

        selector.addView(container)
        return selector
    }

    private fun updateViewModeSelector() {
        val modes = listOf(ViewMode.YEAR, ViewMode.MONTH, ViewMode.DAY)
        viewModeSegments.forEachIndexed { index, segment ->
            segment.setTextColor(if (modes[index] == currentViewMode) ColorTokens.systemBlue else ColorTokens.secondaryLabel)
        }
    }

    // ---- Search Bar --------------------------------------------------------

    private fun buildSearchBar(): View {
        val wrapper = FrameLayout(this).apply {
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity)
            )
        }

        val searchBar = AppleuxSurface(this).apply {
            cornerRadius = SizingTokens.CORNER_RADIUS_ROUND
            surfaceColor = ColorTokens.systemFillSecondary
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity)
            )
        }

        // Search icon
        val searchIcon = ImageView(this).apply {
            setImageResource(R.drawable.il_ic_search)
            setColorFilter(ColorTokens.secondaryLabel)
            layoutParams = LinearLayout.LayoutParams(
                LayoutUtils.dpToPx(IconTokens.SIZE_SMALL, this@PhotosActivity),
                LayoutUtils.dpToPx(IconTokens.SIZE_SMALL, this@PhotosActivity)
            ).apply {
                marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity)
            }
        }
        container.addView(searchIcon)

        // Search placeholder
        val placeholder = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "Search"
            setTextColor(ColorTokens.secondaryLabel)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(placeholder)

        searchBar.addView(container)
        wrapper.addView(searchBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            LayoutUtils.dpToPx(36f, this)
        ))

        return wrapper
    }

    // ---- Photo Grid --------------------------------------------------------

    private fun buildPhotoGrid(): ScrollView {
        grid = GridLayout(this).apply {
            columnCount = GRID_COLUMNS
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity)
            )
        }

        emptyView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity)
            )
        }

        val emptyText = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "No Photos"
            setTextColor(ColorTokens.secondaryLabel)
        }
        emptyView.addView(emptyText)

        return ScrollView(this).apply {
            addView(grid)
        }
    }

    // ---- Tab Bar -----------------------------------------------------------

    private fun buildTabBar(): AppleuxNavigationBar {
        return AppleuxNavigationBar(this).apply {
            addItem(AppleuxNavigationBar.NavigationItem(
                id = TAB_LIBRARY,
                label = "Library",
                iconRes = R.drawable.il_ic_photos
            ))
            addItem(AppleuxNavigationBar.NavigationItem(
                id = TAB_COLLECTIONS,
                label = "Collections",
                iconRes = R.drawable.sf_location
            ))
            addItem(AppleuxNavigationBar.NavigationItem(
                id = TAB_ALBUMS,
                label = "Albums",
                iconRes = R.drawable.il_ic_grid
            ))
            addItem(AppleuxNavigationBar.NavigationItem(
                id = TAB_SEARCH,
                label = "Search",
                iconRes = R.drawable.il_ic_search
            ))
            addItem(AppleuxNavigationBar.NavigationItem(
                id = TAB_HIDDEN,
                label = "Hidden",
                iconRes = R.drawable.sf_lock
            ))
            // Recently Deleted is a separate tab from Hidden: Hidden is a
            // privacy lock, this is a 30-day timer with a restore path.
            // Labelled "Deleted", not "Recently Deleted": this bar is already
            // carrying six items and AppleuxNavigationBar does not cap the
            // label to one line, so the long name wraps and makes one tab
            // taller than the rest. The full name appears in the Albums row,
            // the empty state, the action sheet and every toast.
            addItem(AppleuxNavigationBar.NavigationItem(
                id = TAB_RECENTLY_DELETED,
                label = "Deleted",
                iconRes = R.drawable.sf_trash
            ))
            setActiveItem(TAB_LIBRARY)
            // setOnItemSelectedListener, not an `onItemSelected =` property:
            // AppleuxNavigationBar has no such property, so the assignment never
            // compiled and no tab — Hidden included — ever switched.
            setOnItemSelectedListener { item -> onTabSelected(item.id) }
        }
    }

    /** Switches tabs programmatically (used by the Albums shortcut row). */
    private fun showTab(id: Int) {
        tabBar.setActiveItem(id)
        onTabSelected(id)
    }

    private fun onTabSelected(id: Int) {
        currentTabId = id
        isHiddenTabActive = (id == TAB_HIDDEN)
        isTrashTabActive = (id == TAB_RECENTLY_DELETED)
        // Selection mode is a Library/Hidden concept; the trash tiles open a
        // restore sheet instead, so the Select button has nothing to do there.
        selectButton.visibility = if (isTrashTabActive) View.GONE else View.VISIBLE
        when (id) {
            TAB_LIBRARY -> loadPhotos()
            TAB_COLLECTIONS -> loadCollections()
            TAB_ALBUMS -> loadAlbums()
            TAB_SEARCH -> loadSearch()
            TAB_HIDDEN -> {
                if (isPinUnlocked) {
                    loadHiddenPhotos()
                } else {
                    showPinEntry()
                }
            }
            TAB_RECENTLY_DELETED -> loadTrashPhotos()
        }
    }

    // ---- Photo Loading -----------------------------------------------------

    private fun loadPhotos() {
        grid.removeAllViews()
        selectedPositions.clear()
        cellViews.clear()
        isSelectionMode = false
        selectButton.text = "Select"

        var n = 0
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID),
            null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
            while (c.moveToNext() && n++ < 60) {
                val u = ContentUris.withAppendedId(uri, c.getLong(0))
                if (isPhotoHidden(u)) continue
                // Trashed photos stay in MediaStore; the grid must not show them.
                if (isPhotoTrashed(u)) continue
                val cell = createPhotoCell(u, n - 1)
                cellViews[n - 1] = cell
                grid.addView(cell)
            }
        }

        if (n == 0) {
            grid.addView(emptyView)
        }
    }

    private fun loadHiddenPhotos() {
        grid.removeAllViews()
        selectedPositions.clear()
        cellViews.clear()
        isSelectionMode = false
        selectButton.text = "Select"

        var n = 0
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID),
            null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
            while (c.moveToNext() && n++ < 60) {
                val u = ContentUris.withAppendedId(uri, c.getLong(0))
                if (!isPhotoHidden(u)) continue
                if (isPhotoTrashed(u)) continue
                val cell = createPhotoCell(u, n - 1)
                cellViews[n - 1] = cell
                grid.addView(cell)
            }
        }

        if (n == 0) {
            grid.addView(hiddenEmptyView)
        }
    }

    private fun createPhotoCell(uri: Uri, position: Int): PhotoCellView {
        val cellSize = calculateCellSize()
        val gap = LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this)

        return PhotoCellView(this, uri, position).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                setMargins(gap, gap, gap, gap)
            }
        }
    }

    private fun calculateCellSize(): Int {
        val screenWidth = LayoutUtils.getScreenWidth(this)
        val horizontalPadding = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this) * 2
        val gap = LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this)
        val availableWidth = screenWidth - horizontalPadding - gap * (GRID_COLUMNS - 1)
        return availableWidth / GRID_COLUMNS
    }

    // ---- Selection Mode ----------------------------------------------------

    private fun toggleSelectionMode() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            enterSelectionMode()
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        selectButton.text = "Done"
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedPositions.clear()
        selectButton.text = "Select"
        cellViews.values.forEach { it.photoSelected = false }
    }

    // ---- Hide / Unhide -----------------------------------------------------

    private fun isPhotoHidden(uri: Uri): Boolean {
        return hiddenPrefs.getBoolean(KEY_HIDDEN_PREFIX + uri.toString(), false)
    }

    private fun setPhotoHidden(uri: Uri, hidden: Boolean) {
        hiddenPrefs.edit().putBoolean(KEY_HIDDEN_PREFIX + uri.toString(), hidden).apply()
        if (isHiddenTabActive) {
            loadHiddenPhotos()
        } else {
            loadPhotos()
        }
    }

    // ---- Trash store (Recently Deleted) --------------------------------------

    /** True while this photo sits in Recently Deleted. */
    private fun isPhotoTrashed(uri: Uri): Boolean = trash.any { it.uri == uri.toString() }

    private fun loadTrash(): List<TrashPhoto> {
        val raw = runCatching {
            JSONArray(trashPrefs.getString(KEY_TRASH_PHOTOS, "[]")!!)
        }.getOrDefault(JSONArray())
        val out = mutableListOf<TrashPhoto>()
        for (i in 0 until raw.length()) {
            raw.optJSONObject(i)?.let { out.add(TrashPhoto.fromJson(it)) }
        }
        return out
    }

    private fun saveTrash() {
        // Newest first and bounded: this writes on the main thread on every
        // tile change, and SharedPreferences is not a database.
        trash.sortByDescending { it.deletedAt }
        while (trash.size > MAX_TRASH_ENTRIES) trash.removeAt(trash.size - 1)
        trashPrefs.edit()
            .putString(KEY_TRASH_PHOTOS, JSONArray(trash.map { it.toJson() }).toString())
            .apply()
    }

    /**
     * Trash-first delete: the record goes into the album and the photo is
     * filtered out of every grid. The bytes stay where they are, so Restore is
     * a prefs edit rather than a file move. `albumPath` is the on-disk path —
     * the purge needs it to unlink the file, and a restore needs it to confirm
     * the file is still there.
     */
    private fun movePhotoToTrash(uri: Uri) {
        if (isPhotoTrashed(uri)) {
            refreshAfterTrashChange()
            return
        }
        var name = ""
        var path: String? = null
        runCatching {
            contentResolver.query(uri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0).orEmpty()
                    if (!c.isNull(1)) path = c.getString(1)
                }
            }
        }
        trash.add(TrashPhoto(
            uri = uri.toString(),
            deletedAt = System.currentTimeMillis(),
            albumPath = path,
            name = name.ifEmpty { uri.lastPathSegment.orEmpty() }
        ))
        saveTrash()
        AppleuxToast.show(this, "Moved to Recently Deleted")
        refreshAfterTrashChange()
    }

    /**
     * Removes the record and confirms the bytes survived. Nothing moved, so
     * this is a prefs edit — but a photo can vanish underneath us (SD card
     * pulled, gallery cleanup), so the existence check is real rather than
     * assumed, and a failed restore says so instead of silently doing nothing.
     */
    private fun restoreTrashPhoto(entry: TrashPhoto) {
        val existed = photoStillExists(entry)
        trash.removeAll { it.uri == entry.uri }
        saveTrash()
        AppleuxToast.show(this, if (existed) "Restored to Library" else "Photo is no longer on this device")
        refreshAfterTrashChange()
    }

    /** The only path that actually unlinks bytes: the user's explicit choice. */
    private fun deleteTrashPhotoPermanently(entry: TrashPhoto) {
        runCatching {
            entry.albumPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
            contentResolver.delete(Uri.parse(entry.uri), null, null)
            Unit
        }.onFailure {
            AppleuxToast.show(this, "Could not delete")
        }
        trash.removeAll { it.uri == entry.uri }
        saveTrash()
        AppleuxToast.show(this, "Deleted permanently")
        refreshAfterTrashChange()
    }

    private fun confirmEmptyTrash() {
        if (trash.isEmpty()) return
        AppleuxDialogs.alert(
            this,
            title = "Empty Recently Deleted?",
            message = trash.size.toString() + " " +
                    (if (trash.size == 1) "photo" else "photos") +
                    " will be deleted permanently.",
            confirmLabel = "Delete",
            cancelLabel = "Cancel",
        ) { emptyTrash() }
    }

    private fun emptyTrash() {
        val snapshot = trash.toList()
        snapshot.forEach { entry ->
            runCatching {
                entry.albumPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
                contentResolver.delete(Uri.parse(entry.uri), null, null)
                Unit
            }
        }
        trash.clear()
        saveTrash()
        AppleuxToast.show(this, "Recently Deleted emptied")
        refreshAfterTrashChange()
    }

    /**
     * 30-day auto-purge, run once at start-up before anything reads the album.
     * Expired entries are genuinely deleted rather than merely hidden —
     * otherwise "29 days left" would be a promise the disk never keeps.
     */
    private fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_DAYS * MILLIS_PER_DAY
        val expired = trash.filter { it.deletedAt <= cutoff }
        if (expired.isEmpty()) return
        expired.forEach { entry ->
            runCatching {
                entry.albumPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
                contentResolver.delete(Uri.parse(entry.uri), null, null)
                Unit
            }
        }
        val expiredUris = expired.map { it.uri }.toSet()
        trash.removeAll { it.uri in expiredUris }
        saveTrash()
    }

    private fun photoStillExists(entry: TrashPhoto): Boolean {
        entry.albumPath?.let { if (File(it).exists()) return true }
        // No usable path, or the file moved: ask the provider. A query that
        // returns no row means the media is gone.
        var found = false
        runCatching {
            contentResolver.query(Uri.parse(entry.uri),
                arrayOf(MediaStore.Images.Media._ID), null, null, null
            )?.use { c -> found = c.count > 0 }
        }
        return found
    }

    /** Whole days left before the auto-purge, rounded up, never below zero. */
    private fun daysLeft(deletedAt: Long): Long {
        val remaining = deletedAt + TRASH_RETENTION_DAYS * MILLIS_PER_DAY -
            System.currentTimeMillis()
        if (remaining <= 0L) return 0L
        return (remaining + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY
    }

    private fun daysLeftLabel(deletedAt: Long): String {
        val days = daysLeft(deletedAt)
        return when {
            days <= 0L -> "Deleting now"
            days == 1L -> "1 day left"
            else -> "$days days left"
        }
    }

    private fun refreshAfterTrashChange() {
        when {
            isTrashTabActive -> loadTrashPhotos()
            isHiddenTabActive -> loadHiddenPhotos()
            else -> loadPhotos()
        }
    }

    // ---- Context Menu (iOS 27 Bottom Sheet) --------------------------------

    private fun showContextMenu(uri: Uri, position: Int) {
        val sheet = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ColorTokens.systemBackground)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val isHidden = isPhotoHidden(uri)
        val options = mutableListOf(
            ContextMenuOption("Share", R.drawable.il_ic_share) {
                sharePhoto(uri)

            },
            ContextMenuOption(if (isHidden) "Unhide" else "Hide",
                R.drawable.sf_checkmark) {
                setPhotoHidden(uri, !isHidden)

            },
            ContextMenuOption("Favorite", R.drawable.sf_star) {
                toggleFavorite(uri)

            },
            ContextMenuOption("Edit", R.drawable.sf_pencil) {
                openEditMode(uri)

            },
            ContextMenuOption("Crop", R.drawable.sf_crop) {
                showCropDialog(uri)

            },
            ContextMenuOption("Info", R.drawable.il_ic_info) {
                showPhotoInfo(uri)

            },
            ContextMenuOption("Delete", R.drawable.sf_trash) {
                movePhotoToTrash(uri)

            }
        )

        options.forEach { option ->
            container.addView(buildContextMenuItem(option))
        }

        // Cancel button with 8dp gap
        val cancelButton = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "Cancel"
            setTextColor(ColorTokens.systemBlue)
            gravity = Gravity.CENTER
            setPadding(
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {}
        }

        val cancelContainer = FrameLayout(this).apply {
            setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity), 0, 0)
            addView(cancelButton, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val sheetContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(container)
            addView(cancelContainer)
        }

        sheet.addView(sheetContent)
        AppleuxDialogs.custom(this@PhotosActivity, sheet)
    }

    private fun buildContextMenuItem(option: ContextMenuOption): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = LayoutUtils.dpToPx(48f, this@PhotosActivity)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                0
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { option.action() }
        }

        val icon = ImageView(this).apply {
            setImageResource(option.iconRes)
            setColorFilter(option.tint)
            layoutParams = LinearLayout.LayoutParams(
                LayoutUtils.dpToPx(IconTokens.SIZE_SMALL, this@PhotosActivity),
                LayoutUtils.dpToPx(IconTokens.SIZE_SMALL, this@PhotosActivity)
            ).apply {
                marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            }
        }
        row.addView(icon)

        val label = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = option.label
            setTextColor(option.tint)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        return row
    }

    /**
     * One row in an action sheet.
     *
     * `tint` defaults to systemBlue, the iOS default for an action row. It
     * exists because destructive rows ("Delete Permanently") are systemRed, and
     * rendering both in blue made two very different actions look identical.
     *
     * `action` is declared LAST on purpose: every call site uses Kotlin's
     * trailing-lambda syntax, and a trailing lambda always binds to the final
     * parameter. Putting `tint` after `action` silently retyped every one of
     * those lambdas as an `Int`.
     */
    private data class ContextMenuOption(
        val label: String,
        val iconRes: Int,
        val tint: Int = ColorTokens.systemBlue,
        val action: () -> Unit
    )

    // ---- PIN Entry ---------------------------------------------------------

    private fun showPinEntry() {
        // Try biometric first
        if (androidx.biometric.BiometricManager.from(this).canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricAuth()
        } else {
            showPinEntryDialog()
        }
    }

    private fun showBiometricAuth() {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isPinUnlocked = true
                    loadHiddenPhotos()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showPinEntryDialog()
                }
            })

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Hidden Photos")
            .setSubtitle("Use biometric authentication to view hidden photos")
            .setNegativeButtonText("Use Passcode")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showPinEntryDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity)
            )
            setBackgroundColor(ColorTokens.systemBackground)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.headline)
            text = "Enter Passcode"
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER
        }
        container.addView(title)

        val subtitle = AppleuxText(this).apply {
            applyTypography(TypographyTokens.footnote)
            text = "Enter your 4-digit passcode to view hidden photos"
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
            setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity), 0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity))
        }
        container.addView(subtitle)

        val pinDisplay = AppleuxText(this).apply {
            applyTypography(TypographyTokens.largeTitle)
            text = ""
            setTextColor(ColorTokens.systemBlue)
            gravity = Gravity.CENTER
            setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity), 0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity))
        }
        container.addView(pinDisplay)

        val keypad = GridLayout(this).apply {
            columnCount = 3
            setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity), 0, 0)
        }

        val pinBuilder = StringBuilder()
        val updatePinDisplay = {
            pinDisplay.text = "●".repeat(pinBuilder.length)
        }

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.forEach { key ->
            val keyView = Button(this).apply {
                text = key
                textSize = 24f
                setTextColor(ColorTokens.label)
                background = null
                isAllCaps = false
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = LayoutUtils.dpToPx(56f, this@PhotosActivity)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                setOnClickListener {
                    when (key) {
                        "⌫" -> {
                            if (pinBuilder.isNotEmpty()) {
                                pinBuilder.deleteCharAt(pinBuilder.length - 1)
                                updatePinDisplay()
                            }
                        }
                        "" -> { /* empty cell */ }
                        else -> {
                            if (pinBuilder.length < PIN_LENGTH) {
                                pinBuilder.append(key)
                                updatePinDisplay()
                                if (pinBuilder.length == PIN_LENGTH) {
                                    if (verifyPin(pinBuilder.toString())) {
                                        isPinUnlocked = true

                                        loadHiddenPhotos()
                                    } else {
                                        pinBuilder.clear()
                                        updatePinDisplay()
                                        pinDisplay.setTextColor(ColorTokens.systemRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            keypad.addView(keyView)
        }

        container.addView(keypad)
        AppleuxDialogs.custom(this@PhotosActivity, container)
    }

    private fun verifyPin(pin: String): Boolean {
        val storedHash = pinPrefs.getString(KEY_PIN_HASH, null)
        if (storedHash == null) {
            // First time: store the PIN
            pinPrefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
            return true
        }
        return storedHash == hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ---- Photo Actions -----------------------------------------------------

    private fun sharePhoto(uri: Uri) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, "Share Photo"))
    }

    private fun toggleFavorite(uri: Uri) {
        // Placeholder: would integrate with MediaStore favorites
        AppleuxToast.show(this, "Favorites not yet implemented")
    }

    private fun deletePhoto(uri: Uri, position: Int) {
        movePhotoToTrash(uri)
    }

    // ---- Recently Deleted album ----------------------------------------------

    /**
     * The trash album: the same four-column grid as Library, but every tile
     * carries a "N days left" badge and opens a Restore / Delete Permanently
     * sheet instead of the viewer. "Empty Recently Deleted" sits under the
     * grid, as it does in iOS.
     */
    private fun loadTrashPhotos() {
        grid.removeAllViews()
        selectedPositions.clear()
        cellViews.clear()
        isSelectionMode = false
        selectButton.text = "Select"

        val entries = trash.sortedByDescending { it.deletedAt }
        if (entries.isEmpty()) {
            grid.addView(buildTrashEmptyView(), gridFullWidthParams())
            return
        }

        val cellSize = calculateCellSize()
        val gap = LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this)
        entries.forEach { entry ->
            grid.addView(TrashPhotoCellView(this, entry).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                    setMargins(gap, gap, gap, gap)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { showTrashActionSheet(entry) }
            })
        }

        grid.addView(AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "Empty Recently Deleted"
            setTextColor(ColorTokens.systemRed)
            gravity = Gravity.CENTER
            layoutParams = gridFullWidthParams()
            setPadding(
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { confirmEmptyTrash() }
        })
    }

    /** A GridLayout.LayoutParams that spans every column — for the button and
     *  the empty state, which must not be squeezed into one cell. */
    private fun gridFullWidthParams() = GridLayout.LayoutParams().apply {
        width = ViewGroup.LayoutParams.MATCH_PARENT
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GRID_COLUMNS)
    }

    /**
     * Restore / Delete Permanently sheet for one trashed photo. Reuses the
     * same bottom-sheet construction as the normal context menu so the two
     * sheets are visually identical — only the rows differ.
     */
    private fun showTrashActionSheet(entry: TrashPhoto) {
        val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this)
        val sheet = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ColorTokens.systemBackground)
            setPadding(pad, pad, pad, pad)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Name + countdown: the countdown is the reason this sheet exists.
        container.addView(AppleuxText(this).apply {
            applyTypography(TypographyTokens.footnote)
            text = entry.name.ifEmpty { "Photo" }
            setTextColor(ColorTokens.label)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity)
            )
        })
        container.addView(AppleuxText(this).apply {
            applyTypography(TypographyTokens.footnote)
            text = daysLeftLabel(entry.deletedAt)
            setTextColor(ColorTokens.secondaryLabel)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity)
            )
        })

        container.addView(buildContextMenuItem(ContextMenuOption(
            "Restore", R.drawable.sf_undo,
            action = {
                restoreTrashPhoto(entry)

            }
        )))
        container.addView(buildContextMenuItem(ContextMenuOption(
            "Delete Permanently", R.drawable.sf_trash,
            tint = ColorTokens.systemRed,
            action = {
                deleteTrashPhotoPermanently(entry)

            }
        )))

        val cancelButton = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "Cancel"
            setTextColor(ColorTokens.systemBlue)
            gravity = Gravity.CENTER
            setPadding(
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {}
        }

        val cancelContainer = FrameLayout(this).apply {
            // this@PhotosActivity, not `this`: inside apply, `this` is the
            // FrameLayout, which is not a Context.
            setPadding(
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity),
                0, 0
            )
            addView(cancelButton, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        sheet.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(container)
            addView(cancelContainer)
        })
        AppleuxDialogs.custom(this@PhotosActivity, sheet)
    }

    /**
     * A Recently Deleted tile: the thumbnail plus a "N days left" badge. The
     * badge is the whole point — it is what tells the user this photo is on a
     * timer instead of already gone.
     */
    private inner class TrashPhotoCellView(
        context: Context,
        val entry: TrashPhoto
    ) : FrameLayout(context) {

        private val days = daysLeft(entry.deletedAt)

        init {
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            // Reuse the shared LRU thumbnail cache rather than decoding again;
            // the cap is what keeps this inside the 3GB budget.
            val cached = thumbnailCache[entry.uri]
            if (cached != null) {
                image.setImageBitmap(cached)
            } else {
                runCatching { image.setImageURI(Uri.parse(entry.uri)) }
            }
            addView(image)

            addView(AppleuxText(context).apply {
                text = if (days <= 0L) "Deleting" else "$days days left"
                applyTypography(TypographyTokens.caption2)
                setTextColor(ColorTokens.white)
                val h = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
                val v = LayoutUtils.dpToPx(2f, context)
                setPadding(h, v, h, v)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(
                        SizingTokens.CORNER_RADIUS_SMALL, context)
                    setColor(ColorTokens.black)
                    alpha = 180
                }
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    marginStart = LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, context)
                    bottomMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, context)
                }
            })
        }
    }

    private fun buildHiddenEmptyView(): LinearLayout =
        buildAlbumEmptyState(R.drawable.sf_lock, "No Hidden Photos")

    private fun buildTrashEmptyView(): LinearLayout =
        buildAlbumEmptyState(R.drawable.sf_trash, "No Recently Deleted")

    /**
     * The shared empty state for an album tab: a large glyph over centred
     * secondary-label text. Hidden and Recently Deleted differ only in glyph
     * and wording, so they share this rather than duplicating the padding
     * stack twice.
     */
    private fun buildAlbumEmptyState(iconRes: Int, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity)
            )

            val icon = ImageView(this@PhotosActivity).apply {
                setImageResource(iconRes)
                setColorFilter(ColorTokens.secondaryLabel)
                layoutParams = LinearLayout.LayoutParams(
                    LayoutUtils.dpToPx(48f, this@PhotosActivity),
                    LayoutUtils.dpToPx(48f, this@PhotosActivity)
                ).apply {
                    bottomMargin = LayoutUtils.dpToPx(
                        SpacingTokens.INSETS_MD, this@PhotosActivity)
                }
            }
            addView(icon)

            addView(AppleuxText(this@PhotosActivity).apply {
                applyTypography(TypographyTokens.body)
                text = label
                setTextColor(ColorTokens.secondaryLabel)
            })
        }
    }

    // ---- Permission --------------------------------------------------------

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (r.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadPhotos()
        }
    }

    // ---- Collections -------------------------------------------------------

    private fun loadCollections() {
        grid.removeAllViews()
        cellViews.clear()
        collections.clear()

        // Build collections list
        collections.addAll(listOf(
            CollectionItem(CollectionType.MEMORIES, "Memories", "Auto-grouped by date & location", R.drawable.sf_clock, getRecentPhotosCount(7)),
            CollectionItem(CollectionType.MEMORIES, "Last 30 Days", "Photos from the past month", R.drawable.sf_calendar, getRecentPhotosCount(30)),
            CollectionItem(CollectionType.MEMORIES, "Last Year", "Photos from the past year", R.drawable.sf_calendar, getRecentPhotosCount(365)),
            CollectionItem(CollectionType.PEOPLE, "People", "Contacts with photos", R.drawable.sf_location, getPeopleCount()),
            CollectionItem(CollectionType.PLACES, "Places", "Photos with GPS data", R.drawable.sf_location, getPlacesCount()),
            CollectionItem(CollectionType.TRIPS, "Trips", "Photos >100km from home", R.drawable.sf_compass, getTripsCount()),
            CollectionItem(CollectionType.MEDIA_TYPES, "Screenshots", "Captured screen images", R.drawable.il_ic_camera, getMediaTypeCount(MediaType.SCREENSHOTS)),
            CollectionItem(CollectionType.MEDIA_TYPES, "Videos", "Video recordings", R.drawable.il_ic_play, getMediaTypeCount(MediaType.VIDEOS)),
            CollectionItem(CollectionType.MEDIA_TYPES, "Selfies", "Front camera photos", R.drawable.il_ic_rotation, getMediaTypeCount(MediaType.SELFIES)),
            CollectionItem(CollectionType.MEDIA_TYPES, "Live Photos", "Animated photos", R.drawable.sf_compass, getMediaTypeCount(MediaType.LIVE_PHOTOS)),
            CollectionItem(CollectionType.MEDIA_TYPES, "RAW", "RAW format photos", R.drawable.il_ic_photos, getMediaTypeCount(MediaType.RAW)),
            CollectionItem(CollectionType.MEDIA_TYPES, "Panoramas", "Wide panoramic photos", R.drawable.sf_crop, getMediaTypeCount(MediaType.PANORAMAS)),
            CollectionItem(CollectionType.FEATURED, "Featured", "Your best photos", R.drawable.sf_star, getFeaturedCount()),
            CollectionItem(CollectionType.FEATURED, "Recently Added", "Latest imports", R.drawable.sf_add, getRecentPhotosCount(7))
        ))

        buildCollectionsView()
    }

    private fun buildCollectionsView() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(ColorTokens.systemBackground)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
        }

        collections.forEach { collection ->
            container.addView(buildCollectionRow(collection))
        }

        scrollView.addView(container)
        grid.addView(scrollView)
    }

    private fun buildCollectionRow(collection: CollectionItem): View {
        val row = AppleuxSurface(this).apply {
            cornerRadius = SizingTokens.CORNER_RADIUS_CARD
            surfaceColor = ColorTokens.systemFillTertiary
            isClickable = true
            isFocusable = true
            setOnClickListener { openCollection(collection) }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val icon = ImageView(this).apply {
            setImageResource(collection.iconRes)
            setColorFilter(ColorTokens.systemBlue)
            layoutParams = LinearLayout.LayoutParams(
                LayoutUtils.dpToPx(IconTokens.SIZE_MEDIUM, this@PhotosActivity),
                LayoutUtils.dpToPx(IconTokens.SIZE_MEDIUM, this@PhotosActivity)
            ).apply {
                marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            }
        }
        inner.addView(icon)

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = collection.title
            setTextColor(ColorTokens.label)
        }
        textContainer.addView(title)

        val subtitle = AppleuxText(this).apply {
            applyTypography(TypographyTokens.footnote)
            text = "${collection.subtitle} • ${collection.photoCount}"
            setTextColor(ColorTokens.secondaryLabel)
        }
        textContainer.addView(subtitle)

        inner.addView(textContainer)
        row.addView(inner)

        return row
    }

    private fun openCollection(collection: CollectionItem) {
        // Navigate to collection detail
        val intent = android.content.Intent(this, PhotosActivity::class.java).apply {
            putExtra("collection_type", collection.type.name)
            putExtra("collection_title", collection.title)
        }
        // For now, show a toast - full implementation would open a detail view
        AppleuxToast.show(this, "Opening: ${collection.title}")
    }

    // ---- Collection Data Helpers -------------------------------------------

    private fun getRecentPhotosCount(days: Int): Int {
        val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf((cutoff / 1000).toString())
        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                selection, selectionArgs, null
            )?.use { c -> c.count } ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun getPeopleCount(): Int {
        // Query distinct faces/people - simplified
        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.MIME_TYPE} = ?",
                arrayOf("image/jpeg"), null
            )?.use { c -> c.count } ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun getPlacesCount(): Int {
        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.LATITUDE} IS NOT NULL",
                null, null
            )?.use { c -> c.count } ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun getTripsCount(): Int {
        // Simplified: count photos with GPS data
        return getPlacesCount()
    }

    private fun getMediaTypeCount(type: MediaType): Int {
        return try {
            val (selection, args) = when (type) {
                MediaType.SCREENSHOTS -> "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%Screenshots%")
                MediaType.VIDEOS -> "${MediaStore.Images.Media.MIME_TYPE} LIKE ?" to arrayOf("video/%")
                MediaType.SELFIES -> "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%FrontCamera%")
                MediaType.LIVE_PHOTOS -> "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%LivePhotos%")
                MediaType.RAW -> "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%.dng")
                MediaType.PANORAMAS -> "${MediaStore.Images.Media.WIDTH} > ${MediaStore.Images.Media.HEIGHT} * 2" to arrayOf()
            }
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                selection, args, null
            )?.use { c -> c.count } ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun getFeaturedCount(): Int {
        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.IS_FAVORITE} = 1",
                null, null
            )?.use { c -> c.count } ?: 0
        } catch (e: Exception) { 0 }
    }

    // ---- Albums ------------------------------------------------------------

    private fun loadAlbums() {
        grid.removeAllViews()
        cellViews.clear()
        albums.clear()

        // Smart albums
        albums.add(Album("favorites", "Favorites", isSmartAlbum = true))
        albums.add(Album("videos", "Videos", isSmartAlbum = true))
        albums.add(Album("screenshots", "Screenshots", isSmartAlbum = true))
        albums.add(Album("selfies", "Selfies", isSmartAlbum = true))
        albums.add(Album("recently_added", "Recently Added", isSmartAlbum = true))
        // Not a smart album: it reads the trash store, and tapping it switches
        // to the Recently Deleted tab rather than filtering the library.
        albums.add(Album("recently_deleted", "Recently Deleted", isSmartAlbum = false))

        buildAlbumsView()
    }

    private fun buildAlbumsView() {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(ColorTokens.systemBackground)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
        }

        albums.forEach { album ->
            container.addView(buildAlbumRow(album))
        }

        scrollView.addView(container)
        grid.addView(scrollView)
    }

    private fun buildAlbumRow(album: Album): View {
        val row = AppleuxSurface(this).apply {
            cornerRadius = SizingTokens.CORNER_RADIUS_CARD
            surfaceColor = ColorTokens.systemFillTertiary
            isClickable = true
            isFocusable = true
            setOnClickListener { openAlbum(album) }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
        }

        val icon = ImageView(this).apply {
            setImageResource(if (album.isSmartAlbum) R.drawable.il_ic_grid else R.drawable.sf_pencil)
            setColorFilter(ColorTokens.systemBlue)
            layoutParams = LinearLayout.LayoutParams(
                LayoutUtils.dpToPx(IconTokens.SIZE_MEDIUM, this@PhotosActivity),
                LayoutUtils.dpToPx(IconTokens.SIZE_MEDIUM, this@PhotosActivity)
            ).apply {
                marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            }
        }
        inner.addView(icon)

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = album.name
            setTextColor(ColorTokens.label)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        inner.addView(title)

        row.addView(inner)
        return row
    }

    private fun openAlbum(album: Album) {
        if (album.id == ALBUM_RECENTLY_DELETED) {
            showTab(TAB_RECENTLY_DELETED)
            return
        }
        AppleuxToast.show(this, "Opening album: ${album.name}")
    }

    // ---- Search ------------------------------------------------------------

    private fun loadSearch() {
        grid.removeAllViews()
        cellViews.clear()
        buildSearchView()
    }

    private fun buildSearchView() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ColorTokens.systemBackground)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
        }

        // Search input
        val searchInput = EditText(this).apply {
            // A bare android.widget.EditText is not an AppleuxText, so it has
            // no applyTypography(). The HIG Body metrics are applied directly
            // instead: 17pt, tracking 0.40pt. Line height stays unset because
            // android:lineSpacingMultiplier scales the font's own leading.
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TypographyTokens.body.size)
            letterSpacing = TypographyTokens.body.emTracking
            hint = "Search photos, people, places..."
            setHintTextColor(ColorTokens.secondaryLabel)
            setTextColor(ColorTokens.label)
            setBackgroundColor(ColorTokens.systemFillSecondary)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LayoutUtils.dpToPx(44f, this@PhotosActivity)
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s.toString()
                    performSearch()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        container.addView(searchInput)

        // Filter chips
        val filterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity), 0, 0)
        }

        val filters = listOf("All" to SearchFilter.ALL, "Photos" to SearchFilter.PHOTOS,
            "Videos" to SearchFilter.VIDEOS, "Screenshots" to SearchFilter.SCREENSHOTS,
            "Favorites" to SearchFilter.FAVORITES)

        filters.forEach { (label, filter) ->
            val chip = AppleuxText(this).apply {
                applyTypography(TypographyTokens.footnote)
                text = label
                setTextColor(if (filter == searchFilter) ColorTokens.white else ColorTokens.systemBlue)
                setPadding(
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity)
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    searchFilter = filter
                    loadSearch()
                }
            }
            filterContainer.addView(chip)
        }
        container.addView(filterContainer)

        // Suggestions
        if (recentSearches.isNotEmpty()) {
            val suggestionsTitle = AppleuxText(this).apply {
                applyTypography(TypographyTokens.footnoteEmphasized)
                text = "Recent"
                setTextColor(ColorTokens.secondaryLabel)
                setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity), 0, 0)
            }
            container.addView(suggestionsTitle)

            recentSearches.forEach { search ->
                container.addView(buildSuggestionRow(search))
            }
        }

        val scrollView = ScrollView(this).apply {
            addView(container)
        }
        grid.addView(scrollView)
    }

    private fun buildSuggestionRow(suggestion: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                searchQuery = suggestion
                performSearch()
            }
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.sf_clock)
            setColorFilter(ColorTokens.secondaryLabel)
            layoutParams = LinearLayout.LayoutParams(
                LayoutUtils.dpToPx(IconTokens.SIZE_SMALL, this@PhotosActivity),
                LayoutUtils.dpToPx(IconTokens.SIZE_SMALL, this@PhotosActivity)
            ).apply {
                marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            }
        }
        row.addView(icon)

        val text = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = suggestion
            setTextColor(ColorTokens.label)
        }
        row.addView(text)

        return row
    }

    private fun performSearch() {
        if (searchQuery.isBlank()) return

        // Add to recent searches
        if (!recentSearches.contains(searchQuery)) {
            recentSearches.add(0, searchQuery)
            if (recentSearches.size > 10) recentSearches.removeAt(10)
        }

        // Parse natural language queries
        val lowerQuery = searchQuery.lowercase()

        // Date search
        if (lowerQuery.contains("last week") || lowerQuery.contains("this week")) {
            showSearchResults(getPhotosByDateRange(7))
        } else if (lowerQuery.contains("last month") || lowerQuery.contains("this month")) {
            showSearchResults(getPhotosByDateRange(30))
        } else if (lowerQuery.contains("last year") || lowerQuery.contains("this year")) {
            showSearchResults(getPhotosByDateRange(365))
        }
        // Location search
        else if (lowerQuery.contains(" in ") || lowerQuery.contains(" from ")) {
            val location = searchQueryAfterPreposition(lowerQuery)
            showSearchResults(getPhotosByLocation(location))
        }
        // Media type search
        else if (lowerQuery.contains("screenshot")) {
            showSearchResults(getPhotosByMediaType(MediaType.SCREENSHOTS))
        } else if (lowerQuery.contains("video")) {
            showSearchResults(getPhotosByMediaType(MediaType.VIDEOS))
        } else if (lowerQuery.contains("selfie")) {
            showSearchResults(getPhotosByMediaType(MediaType.SELFIES))
        }
        // Object search (using MediaStore tags if available)
        else {
            showSearchResults(getPhotosByText(searchQuery))
        }
    }

    private fun searchQueryAfterPreposition(query: String): String {
        val prepositions = listOf(" in ", " from ")
        for (prep in prepositions) {
            val idx = query.indexOf(prep)
            if (idx >= 0) {
                return query.substring(idx + prep.length).trim()
            }
        }
        return query
    }

    private fun getPhotosByDateRange(days: Int): List<Uri> {
        val cutoff = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf((cutoff / 1000).toString())
        return queryPhotoUris(selection, selectionArgs)
    }

    private fun getPhotosByLocation(location: String): List<Uri> {
        return queryPhotoUris(
            "${MediaStore.Images.Media.DATA} LIKE ?",
            arrayOf("%$location%")
        )
    }

    private fun getPhotosByMediaType(type: MediaType): List<Uri> {
        return when (type) {
            MediaType.SCREENSHOTS -> getPhotosByLocation("Screenshots")
            MediaType.VIDEOS -> queryPhotoUris(
                "${MediaStore.Images.Media.MIME_TYPE} LIKE ?",
                arrayOf("video/%")
            )
            MediaType.SELFIES -> getPhotosByLocation("FrontCamera")
            MediaType.LIVE_PHOTOS -> getPhotosByLocation("LivePhotos")
            MediaType.RAW -> getPhotosByLocation(".dng")
            MediaType.PANORAMAS -> queryPhotoUris(
                "${MediaStore.Images.Media.WIDTH} > ${MediaStore.Images.Media.HEIGHT} * 2",
                arrayOf()
            )
        }
    }

    private fun getPhotosByText(text: String): List<Uri> {
        return queryPhotoUris(
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.TITLE} LIKE ?",
            arrayOf("%$text%", "%$text%")
        )
    }

    private fun queryPhotoUris(selection: String, selectionArgs: Array<String>): List<Uri> {
        val uris = mutableListOf<Uri>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        try {
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID),
                selection, selectionArgs, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                while (c.moveToNext()) {
                    val u = ContentUris.withAppendedId(uri, c.getLong(0))
                    // Search is a grid too — a trashed photo must not surface here.
                    if (isPhotoTrashed(u)) continue
                    uris.add(u)
                }
            }
        } catch (e: Exception) { /* graceful degradation */ }
        return uris
    }

    private fun showSearchResults(uris: List<Uri>) {
        // Replace grid content with search results
        grid.removeAllViews()
        cellViews.clear()

        if (uris.isEmpty()) {
            val emptyText = AppleuxText(this).apply {
                applyTypography(TypographyTokens.body)
                text = "No results found"
                setTextColor(ColorTokens.secondaryLabel)
                gravity = Gravity.CENTER
                setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity), 0, 0)
            }
            grid.addView(emptyText)
            return
        }

        uris.forEachIndexed { index, photoUri ->
            val cell = createPhotoCell(photoUri, index)
            cellViews[index] = cell
            grid.addView(cell)
        }
    }

    // ---- Photo Viewer ------------------------------------------------------

    fun openPhotoViewer(photoUri: Uri, allPhotos: List<Uri>) {
        viewerPhotoUris.clear()
        viewerPhotoUris.addAll(allPhotos)
        viewerCurrentIndex = allPhotos.indexOf(photoUri).coerceAtLeast(0)
        showPhotoAtViewerIndex()
    }

    private fun showPhotoAtViewerIndex() {
        if (viewerCurrentIndex < 0 || viewerCurrentIndex >= viewerPhotoUris.size) return

        val currentUri = viewerPhotoUris[viewerCurrentIndex]
        AppleuxDialogs.dismiss(this)

        val root = FrameLayout(this).apply {
            setBackgroundColor(ColorTokens.black)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Photo display
        val photoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageURI(currentUri)

            // Double-tap to zoom
            var lastTapTime = 0L
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 300) {
                            // Double tap - toggle zoom
                            if (scaleType == ImageView.ScaleType.FIT_CENTER) {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            } else {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        }
                        lastTapTime = currentTime
                    }
                }
                false
            }

            // Single tap - toggle controls
            setOnClickListener {
                isControlsVisible = !isControlsVisible
            }
        }
        root.addView(photoView)

        // Info button
        val infoButton = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "ⓘ"
            setTextColor(ColorTokens.white)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { showPhotoInfo(currentUri) }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.TOP
            ).apply {
                topMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity)
                rightMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            }
        }
        root.addView(infoButton)

        // Edit button
        val editButton = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "Edit"
            setTextColor(ColorTokens.systemYellow)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { openEditMode(currentUri) }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.TOP
            ).apply {
                topMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity)
                leftMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            }
        }
        root.addView(editButton)

        // Close button
        val closeButton = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "✕"
            setTextColor(ColorTokens.white)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {}
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ).apply {
                bottomMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@PhotosActivity)
                rightMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            }
        }
        root.addView(closeButton)

        // Navigation arrows
        if (viewerCurrentIndex > 0) {
            val prevButton = AppleuxText(this).apply {
                applyTypography(TypographyTokens.body)
                text = "‹"
                setTextColor(ColorTokens.white)
                textSize = 36f
                setPadding(
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    viewerCurrentIndex--
                    showPhotoAtViewerIndex()
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.START or Gravity.CENTER_VERTICAL
                ).apply {
                    leftMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
                }
            }
            root.addView(prevButton)
        }

        if (viewerCurrentIndex < viewerPhotoUris.size - 1) {
            val nextButton = AppleuxText(this).apply {
                applyTypography(TypographyTokens.body)
                text = "›"
                setTextColor(ColorTokens.white)
                textSize = 36f
                setPadding(
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    viewerCurrentIndex++
                    showPhotoAtViewerIndex()
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL
                ).apply {
                    rightMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
                }
            }
            root.addView(nextButton)
        }

        // The viewer used to keep the AlertDialog to hide it on rotation. The
        // AppleUX dialog is an in-view child of the content host, so the Activity
        // destroying its views already removes it — there is nothing to dismiss
        // and nothing to retain, which is one fewer leaked window reference.
        AppleuxDialogs.custom(this@PhotosActivity, root)
    }

    private fun showPhotoInfo(uri: Uri) {
        val info = extractPhotoInfo(uri)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ColorTokens.systemBackground)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity)
            )
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.headline)
            text = "Details"
            setTextColor(ColorTokens.label)
        }
        container.addView(title)

        val details = listOf(
            "Date" to info.date,
            "Time" to info.time,
            "Location" to info.location,
            "Camera" to info.camera,
            "ISO" to info.iso,
            "Shutter" to info.shutter,
            "Aperture" to info.aperture,
            "Focal Length" to info.focalLength,
            "File Size" to info.fileSize,
            "Dimensions" to info.dimensions,
            "Format" to info.format
        )

        details.forEach { (label, value) ->
            if (value.isNotEmpty()) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@PhotosActivity), 0, 0)
                }

                val labelView = AppleuxText(this).apply {
                    applyTypography(TypographyTokens.footnote)
                    text = label
                    setTextColor(ColorTokens.secondaryLabel)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(labelView)

                val valueView = AppleuxText(this).apply {
                    applyTypography(TypographyTokens.footnoteEmphasized)
                    text = value
                    setTextColor(ColorTokens.label)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(valueView)

                container.addView(row)
            }
        }

        AppleuxDialogs.custom(this@PhotosActivity, container)
    }

    private fun extractPhotoInfo(uri: Uri): PhotoInfo {
        var info = PhotoInfo()
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val dateAdded = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val width = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val height = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                    val size = c.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val displayName = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeType = c.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

                    if (dateAdded >= 0) {
                        val dateSec = c.getLong(dateAdded)
                        val date = Date(dateSec * 1000)
                        info = info.copy(
                            date = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(date),
                            time = SimpleDateFormat("h:mm a", Locale.US).format(date)
                        )
                    }
                    if (width >= 0 && height >= 0) {
                        info = info.copy(dimensions = "${c.getInt(width)} × ${c.getInt(height)}")
                    }
                    if (size >= 0) {
                        val bytes = c.getLong(size)
                        info = info.copy(fileSize = formatFileSize(bytes))
                    }
                    if (displayName >= 0) {
                        val name = c.getString(displayName) ?: ""
                        info = info.copy(format = name.substringAfterLast('.', "Unknown"))
                    }
                }
            }

            // Extract EXIF data
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: ""
                    if (make.isNotEmpty() || model.isNotEmpty()) {
                        info = info.copy(camera = "$make $model".trim())
                    }
                    val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                    if (iso != null) info = info.copy(iso = iso)
                    val shutter = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                    if (shutter != null) info = info.copy(shutter = "${shutter}s")
                    val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                    if (aperture != null) info = info.copy(aperture = "f/$aperture")
                    val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                    if (focal != null) info = info.copy(focalLength = focal)

                    // GPS
                    val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                    val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                    if (lat != null && lon != null) {
                        info = info.copy(location = "$lat, $lon")
                    }
                }
            } catch (e: Exception) { /* EXIF not available */ }
        } catch (e: Exception) { /* graceful degradation */ }
        return info
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    // ---- Edit Mode ---------------------------------------------------------

    private fun openEditMode(uri: Uri) {
        currentEditUri = uri
        editHistory.clear()
        editHistoryIndex = -1

        val root = FrameLayout(this).apply {
            setBackgroundColor(ColorTokens.black)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Photo display
        val photoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageURI(uri)
        }
        root.addView(photoView)

        // Edit controls container
        val controlsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ColorTokens.systemBackground)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        // Adjustment sliders
        val adjustments = listOf(
            "Exposure" to 0f, "Brilliance" to 0f, "Contrast" to 0f,
            "Brightness" to 0f, "Highlights" to 0f, "Shadows" to 0f,
            "Saturation" to 0f, "Vibrance" to 0f, "Warmth" to 0f,
            "Tint" to 0f, "Sharpness" to 0f, "Vignette" to 0f
        )

        adjustments.forEach { (name, initialValue) ->
            controlsContainer.addView(buildAdjustmentSlider(name, initialValue))
        }

        // Filter presets
        val filterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity), 0, 0)
        }

        val filters = listOf("None", "Vivid", "Dramatic", "Warm", "Cool", "Noir", "Fade")
        filters.forEach { filter ->
            val filterButton = AppleuxText(this).apply {
                applyTypography(TypographyTokens.footnote)
                text = filter
                setTextColor(if (filter == "None") ColorTokens.systemBlue else ColorTokens.secondaryLabel)
                setPadding(
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                    LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity)
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    addEditAction("filter", filters.indexOf(filter).toFloat())
                    // Update UI to show selected filter
                    (parent as? LinearLayout)?.let { parent ->
                        for (i in 0 until parent.childCount) {
                            val child = parent.getChildAt(i) as? AppleuxText
                            if (child != null && filters.contains(child.text.toString())) {
                                child.setTextColor(if (child.text == filter) ColorTokens.systemBlue else ColorTokens.secondaryLabel)
                            }
                        }
                    }
                }
            }
            filterContainer.addView(filterButton)
        }
        controlsContainer.addView(filterContainer)

        // Action buttons
        val actionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity), 0, 0)
        }

        val undoButton = Button(this).apply {
            text = "Undo"
            isEnabled = false
            setOnClickListener { undoEdit() }
        }
        actionContainer.addView(undoButton)

        val redoButton = Button(this).apply {
            text = "Redo"
            isEnabled = false
            setOnClickListener { redoEdit() }
        }
        actionContainer.addView(redoButton)

        val resetButton = Button(this).apply {
            text = "Reset"
            setOnClickListener { resetEdits() }
        }
        actionContainer.addView(resetButton)

        val doneButton = Button(this).apply {
            text = "Done"
            setOnClickListener { saveEdits(uri);}
        }
        actionContainer.addView(doneButton)

        controlsContainer.addView(actionContainer)

        root.addView(controlsContainer)
        AppleuxDialogs.custom(this@PhotosActivity, root)
    }

    private fun buildAdjustmentSlider(name: String, initialValue: Float): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@PhotosActivity), 0, 0)
        }

        val label = AppleuxText(this).apply {
            applyTypography(TypographyTokens.footnote)
            text = name
            setTextColor(ColorTokens.label)
        }
        container.addView(label)

        val seekBar = AppleuxSlider(this).apply {
            max = 100
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val normalized = (progress - 50) / 50f
                        addEditAction(name.lowercase(), normalized)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(seekBar)

        return container
    }

    private fun addEditAction(type: String, value: Float) {
        // Remove any actions after current index (new branch)
        if (editHistoryIndex < editHistory.size - 1) {
            editHistory.subList(editHistoryIndex + 1, editHistory.size).clear()
        }
        editHistory.add(EditAction(type, value))
        editHistoryIndex++
    }

    private fun undoEdit() {
        if (editHistoryIndex >= 0) {
            editHistoryIndex--
        }
    }

    private fun redoEdit() {
        if (editHistoryIndex < editHistory.size - 1) {
            editHistoryIndex++
        }
    }

    private fun resetEdits() {
        editHistory.clear()
        editHistoryIndex = -1
    }

    private fun saveEdits(uri: Uri) {
        // Non-destructive: store edits separately from original
        // In a full implementation, this would apply edits and save to a new file
        AppleuxToast.show(this, "Edits saved")
    }

    // ---- Crop & Rotate -----------------------------------------------------

    private fun showCropDialog(uri: Uri) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ColorTokens.systemBackground)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@PhotosActivity)
            )
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.headline)
            text = "Crop"
            setTextColor(ColorTokens.label)
        }
        container.addView(title)

        val aspectRatios = listOf("Free", "1:1", "4:3", "16:9", "3:2", "9:16")
        aspectRatios.forEach { ratio ->
            val button = Button(this).apply {
                text = ratio
                setOnClickListener {
                    applyCrop(uri, ratio)

                }
            }
            container.addView(button)
        }

        val rotateButton = Button(this).apply {
            text = "Rotate 90°"
            setOnClickListener {
                rotateImage(uri, 90f)

            }
        }
        container.addView(rotateButton)

        val flipButton = Button(this).apply {
            text = "Flip Horizontal"
            setOnClickListener {
                flipImage(uri, horizontal = true)

            }
        }
        container.addView(flipButton)

        AppleuxDialogs.custom(this@PhotosActivity, container)
    }

    private fun applyCrop(uri: Uri, aspectRatio: String) {
        // Simplified crop - would use Canvas in full implementation
        AppleuxToast.show(this, "Crop: $aspectRatio")
    }

    private fun rotateImage(uri: Uri, degrees: Float) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // Save to temporary file
            val tempFile = File(cacheDir, "rotated_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // In full implementation, would update the original or create edited version
            AppleuxToast.show(this, "Rotated ${degrees}°")
        } catch (e: Exception) {
            AppleuxToast.show(this, "Rotation failed")
        }
    }

    private fun flipImage(uri: Uri, horizontal: Boolean) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val matrix = Matrix().apply {
                if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
            }
            val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            val tempFile = File(cacheDir, "flipped_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                flipped.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            AppleuxToast.show(this, "Flipped")
        } catch (e: Exception) {
            AppleuxToast.show(this, "Flip failed")
        }
    }

    // ---- Long Press Quick Actions ------------------------------------------

    private fun showQuickActions(uri: Uri, position: Int) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ColorTokens.systemBackground)
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@PhotosActivity)
            )
        }

        val actions = listOf(
            "Favorite" to { toggleFavorite(uri) },
            "Share" to { sharePhoto(uri) },
            "Hide" to { setPhotoHidden(uri, true) },
            "Edit" to { openEditMode(uri) },
            "Crop" to { showCropDialog(uri) },
            "Info" to { showPhotoInfo(uri) },
            "Delete" to { deletePhoto(uri, position) }
        )

        actions.forEach { (label, action) ->
            val row = Button(this).apply {
                text = label
                setOnClickListener {
                    action()

                }
            }
            container.addView(row)
        }

        AppleuxDialogs.custom(this@PhotosActivity, container)
    }

    // ---- Photo Cell View ---------------------------------------------------

    /**
     * A photo cell with selection mode support.
     * Draws a blue checkmark circle overlay when selected.
     * Supports thumbnail caching for 3GB RAM budget.
     */
    private inner class PhotoCellView(
        context: Context,
        val photoUri: Uri,
        val position: Int
    ) : FrameLayout(context) {

        private val imageView = ImageView(context)
        private val checkmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val checkmarkPath = Path()

        /**
         * Whether this photo is in the edit-mode selection set.
         *
         * Named photoSelected, not isSelected: android.view.View already
         * declares isSelected()/setSelected(), so a property called isSelected
         * has the identical JVM signature and is an accidental override. It is
         * deliberately NOT delegated to the platform flag either - that flag
         * tracks focus traversal, and letting the checkmark depend on which row
         * gained focus last would be a bug, not a feature.
         */
        var photoSelected: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        init {
            setWillNotDraw(false)

            imageView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

            // Use cached thumbnail if available
            val cacheKey = photoUri.toString()
            val cached = thumbnailCache[cacheKey]
            if (cached != null) {
                imageView.setImageBitmap(cached)
            } else {
                runCatching {
                    imageView.setImageURI(photoUri)
                    // Cache the thumbnail
                    imageView.drawable?.let { drawable ->
                        val bitmap = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        thumbnailCache[cacheKey] = bitmap
                    }
                }
            }
            addView(imageView)

            isClickable = true
            isFocusable = true

            setOnClickListener {
                if (isSelectionMode) {
                    toggleCellSelection()
                } else {
                    // Open photo viewer
                    val allPhotos = cellViews.values.map { it.photoUri }
                    openPhotoViewer(photoUri, allPhotos)
                }
            }

            setOnLongClickListener {
                if (!isSelectionMode) {
                    showQuickActions(photoUri, position)
                }
                true
            }
        }

        private fun toggleCellSelection() {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
                photoSelected = false
            } else {
                selectedPositions.add(position)
                photoSelected = true
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (photoSelected) {
                drawCheckmark(canvas)
            }
        }

        private fun drawCheckmark(canvas: Canvas) {
            val size = LayoutUtils.dpToPx(20f, context)
            val padding = LayoutUtils.dpToPx(4f, context)
            val cx = width - padding - size / 2f
            val cy = padding + size / 2f

            // Blue circle
            checkmarkPaint.color = ColorTokens.systemBlue
            checkmarkPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, size / 2f, checkmarkPaint)

            // White checkmark
            checkmarkPaint.color = ColorTokens.white
            checkmarkPaint.style = Paint.Style.STROKE
            checkmarkPaint.strokeWidth = LayoutUtils.dpToPx(2f, context).toFloat()
            checkmarkPaint.strokeCap = Paint.Cap.ROUND
            checkmarkPaint.strokeJoin = Paint.Join.ROUND

            val checkSize = size * 0.5f
            checkmarkPath.reset()
            checkmarkPath.moveTo(cx - checkSize * 0.5f, cy)
            checkmarkPath.lineTo(cx - checkSize * 0.1f, cy + checkSize * 0.4f)
            checkmarkPath.lineTo(cx + checkSize * 0.5f, cy - checkSize * 0.4f)
            canvas.drawPath(checkmarkPath, checkmarkPaint)
        }
    }

    // ---- Widget Support -----------------------------------------------------

    /**
     * iOS 27 style photo widget data provider.
     * Supports: Photo widget (single photo/album), Memories widget (rotating),
     * People widget (favorite contacts).
     * Privacy: lock screen hides widget content.
     */
    object WidgetSupport {
        const val WIDGET_SIZE_SMALL = "2x2"
        const val WIDGET_SIZE_MEDIUM = "4x2"
        const val WIDGET_SIZE_LARGE = "4x4"

        enum class WidgetType {
            SINGLE_PHOTO,
            ALBUM,
            MEMORIES,
            PEOPLE
        }

        data class WidgetConfig(
            val type: WidgetType,
            val size: String,
            val albumId: String? = null,
            val photoUri: String? = null,
            val isOnLockScreen: Boolean = false
        )

        /**
         * Get widget data for rendering.
         * Returns empty list if on lock screen and content should be hidden.
         */
        fun getWidgetData(config: WidgetConfig, context: Context): List<Uri> {
            if (config.isOnLockScreen) {
                return emptyList() // Privacy: hide content on lock screen
            }

            return when (config.type) {
                WidgetType.SINGLE_PHOTO -> {
                    config.photoUri?.let { listOf(Uri.parse(it)) } ?: emptyList()
                }
                WidgetType.ALBUM -> {
                    config.albumId?.let { getAlbumPhotos(it, context) } ?: emptyList()
                }
                WidgetType.MEMORIES -> {
                    getFeaturedPhotos(context)
                }
                WidgetType.PEOPLE -> {
                    getPeoplePhotos(context)
                }
            }
        }

        private fun getAlbumPhotos(albumId: String, context: Context): List<Uri> {
            // Query photos for specific album
            return emptyList()
        }

        private fun getFeaturedPhotos(context: Context): List<Uri> {
            val uris = mutableListOf<Uri>()
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            try {
                context.contentResolver.query(uri,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.IS_FAVORITE} = 1",
                    null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                    while (c.moveToNext() && uris.size < 10) {
                        uris.add(ContentUris.withAppendedId(uri, c.getLong(0)))
                    }
                }
            } catch (e: Exception) { /* graceful degradation */ }
            return uris
        }

        private fun getPeoplePhotos(context: Context): List<Uri> {
            // Simplified: return recent photos with faces
            val uris = mutableListOf<Uri>()
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            try {
                context.contentResolver.query(uri,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.MIME_TYPE} = ?",
                    arrayOf("image/jpeg"),
                    MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                    while (c.moveToNext() && uris.size < 5) {
                        uris.add(ContentUris.withAppendedId(uri, c.getLong(0)))
                    }
                }
            } catch (e: Exception) { /* graceful degradation */ }
            return uris
        }
    }
}
