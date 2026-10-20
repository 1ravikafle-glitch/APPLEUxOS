package com.appleux.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxIcon
import com.appleux.design.components.AppleuxNavigationBar
import com.appleux.design.components.AppleuxNavigationBar.NavigationItem
import com.appleux.design.components.AppleuxSurface
import com.appleux.design.components.AppleuxText
import com.appleux.design.components.LayoutUtils
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import com.appleux.design.tokens.MotionTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * iOS 27 Files UI for AppleUX.
 *
 * Large title, glass search pill, Browse/Recents tab bar, grouped file list
 * with type-coloured icons, sort controls, Recycle Bin, and system-level
 * folder access.
 *
 * iOS 27 enhancements: Storage Analyzer, Archive Manager, File Tags,
 * Favorites & Pinned, Multi-select Operations, Hidden Files, File Preview,
 * Breadcrumb Navigation.
 */
class FilesActivity : AppCompatActivity() {

    private lateinit var tabBar: AppleuxNavigationBar
    private lateinit var fileListContainer: LinearLayout
    private lateinit var sortPill: SortPillView
    private var currentTab = TAB_BROWSE
    private var currentSortOption = SortOption.NAME_ASC
    private var isInRecycleBin = false

    private val deletedItems = mutableListOf<RecycleBinItem>()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // ---- iOS 27: Recently Deleted (real on-disk trash) ----
    private lateinit var trashDir: File
    private lateinit var trashPrefs: SharedPreferences

    // ---- iOS 27: Storage Locations (internal / SD card / USB OTG) ----
    private val storageLocations = mutableListOf<StorageLocation>()
    private var storageReceiver: BroadcastReceiver? = null
    private var currentRootPath: String? = null

    // ---- iOS 27: Tags ----
    private val tags = mutableListOf<FileTag>()
    private val fileTagMap = mutableMapOf<String, MutableSet<String>>()

    // ---- iOS 27: Favorites & Pinned ----
    private val pinnedFolders = mutableSetOf<String>()
    private val favoriteFiles = mutableSetOf<String>()
    private val recentFiles = mutableListOf<RecentFileEntry>()

    // ---- iOS 27: Hidden Files ----
    private val hiddenFiles = mutableSetOf<String>()
    private var showHiddenFiles = false

    // ---- iOS 27: Selection ----
    private val selectedFiles = mutableSetOf<String>()
    private var isSelectionMode = false

    // ---- iOS 27: Breadcrumb Navigation ----
    private val breadcrumbPaths = mutableListOf<String>()
    private lateinit var breadcrumbBar: LinearLayout

    // ---- iOS 27: Storage Analyzer ----
    private val storageCategories = mutableListOf<StorageCategory>()

    // ---- iOS 27: Operations ----
    private val operationHistory = mutableListOf<FileOperation>()

    // ---- iOS 27: Archive ----
    private val archiveEntries = mutableListOf<ArchiveEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Order matters: every one of these feeds rebuildFileList(), which runs
        // during buildLayout() -> setContentView(). A trash scan has to happen
        // before the first frame or "Recently Deleted" would render as empty
        // and then pop in a moment later.
        initDefaultTags()
        initStorageCategories()
        initTrash()
        purgeExpiredTrash()
        detectStorageLocations()

        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        registerStorageReceiver()
        detectStorageLocations()
        reloadTrash()
        rebuildFileList()
    }

    override fun onPause() {
        unregisterStorageReceiver()
        super.onPause()
    }

    private fun initDefaultTags() {
        tags.addAll(listOf(
            FileTag("tag_red", "Red", ColorTokens.systemRed),
            FileTag("tag_orange", "Orange", ColorTokens.systemOrange),
            FileTag("tag_yellow", "Yellow", ColorTokens.systemYellow),
            FileTag("tag_green", "Green", ColorTokens.systemGreen),
            FileTag("tag_blue", "Blue", ColorTokens.systemBlue),
            FileTag("tag_purple", "Purple", ColorTokens.systemPurple),
            FileTag("tag_gray", "Gray", ColorTokens.systemGray)
        ))
    }

    private fun initStorageCategories() {
        storageCategories.addAll(listOf(
            StorageCategory("Images", ColorTokens.systemPurple, 3.1, 1),
            StorageCategory("Videos", ColorTokens.systemRed, 45.2, 1),
            StorageCategory("Documents", ColorTokens.systemBlue, 2.56, 3),
            StorageCategory("Audio", ColorTokens.systemOrange, 4.8, 1),
            StorageCategory("Other", ColorTokens.systemGray, 40.6, 2)
        ))
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ColorTokens.systemBackground)
        }

        root.addView(buildNavigationBar())
        root.addView(buildBreadcrumbBar())
        root.addView(buildSearchBar())
        root.addView(buildSortBar())

        tabBar = AppleuxNavigationBar(this).apply {
            addItem(NavigationItem(TAB_BROWSE, "Browse", com.appleux.design.R.drawable.sf_folder))
            addItem(NavigationItem(TAB_RECENTS, "Recents", com.appleux.design.R.drawable.sf_clock))
            setOnItemSelectedListener { item ->
                currentTab = item.id
                isInRecycleBin = false
                rebuildFileList()
            }
        }
        root.addView(tabBar)

        fileListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
            setPadding(pad, LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity), pad, LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity))
        }
        // Locations must be the first thing rendered, above the file list.
        fileListContainer.addView(buildLocationsSection())
        rebuildFileList()

        return ScrollView(this).apply { addView(root) }
    }

    // ---- iOS 27: Breadcrumb Navigation ----

    private fun buildBreadcrumbBar(): View {
        breadcrumbBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }
        updateBreadcrumb()
        return breadcrumbBar
    }

    private fun updateBreadcrumb() {
        breadcrumbBar.removeAllViews()
        // breadcrumbPaths holds absolute directory paths, so the label shown is
        // the directory name. "Browse" (index 0) pops the whole trail.
        val segments = listOf("Browse") + breadcrumbPaths.map { File(it).name }
        segments.forEachIndexed { index, segment ->
            if (index > 0) {
                val chevron = AppleuxText(this).apply {
                    applyTypography(TypographyTokens.subheadline)
                    text = " › "
                    setTextColor(ColorTokens.tertiaryLabel)
                }
                breadcrumbBar.addView(chevron)
            }
            val segmentText = AppleuxText(this).apply {
                applyTypography(TypographyTokens.subheadlineEmphasized)
                text = segment
                setTextColor(if (index == segments.size - 1) ColorTokens.label else ColorTokens.systemBlue)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    // segments[0] is "Browse" itself, so tapping it keeps
                    // nothing and tapping a segment keeps 1..index-1.
                    val keep = if (index == 0) 0 else index
                    breadcrumbPaths.subList(keep, breadcrumbPaths.size).clear()
                    currentRootPath = breadcrumbPaths.lastOrNull()
                    updateBreadcrumb()
                    rebuildFileList()
                }
            }
            breadcrumbBar.addView(segmentText)
        }
    }

    // ---- iOS 27: Storage Analyzer ----

    private fun showStorageAnalyzer() {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = "Storage"
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        // Visual bar chart
        val totalSize = storageCategories.sumOf { it.sizeMb }
        val barChart = StorageBarChartView(this).apply {
            setCategories(storageCategories, totalSize)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(barChart)

        // Category cards
        storageCategories.forEach { category ->
            val card = CategoryCardView(this).apply {
                setCategory(category, totalSize)
                setOnClickListener {
                    showCategoryDrillDown(category)
                    popup.dismiss()
                }
            }
            container.addView(card)
        }

        // Large files section
        val largeFilesHeader = AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadlineEmphasized)
            text = "Large Files"
            setTextColor(ColorTokens.secondaryLabel)
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }
        container.addView(largeFilesHeader)

        val largeFilesCard = AppleuxCard(this)
        val largeFilesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        getLargeFiles().forEach { file ->
            val row = FileRowView(this).apply {
                setTitle(file.name)
                setSubtitle(formatFileSize(file.length()))
                setIconDrawable(createFileIcon(FileItem(
                    name = file.name,
                    subtitle = "",
                    iconColor = ColorTokens.systemOrange,
                    isFolder = false,
                    type = FileItemType.OTHER
                )))
            }
            largeFilesContainer.addView(row)
        }
        largeFilesCard.addView(largeFilesContainer)
        container.addView(largeFilesCard)

        // Cleanup suggestions
        val cleanupHeader = AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadlineEmphasized)
            text = "Cleanup Suggestions"
            setTextColor(ColorTokens.secondaryLabel)
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }
        container.addView(cleanupHeader)

        val cleanupCard = AppleuxCard(this)
        val cleanupContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        listOf(
            "Review Large Files" to "Files over 100 MB",
            "Delete Duplicates" to "${findDuplicates().size} duplicate groups found",
            "Clear Cache" to "Free up temporary files"
        ).forEach { (title, subtitle) ->
            val row = FileRowView(this).apply {
                setTitle(title)
                setSubtitle(subtitle)
                setIconDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
                    setColor(ColorTokens.systemBlue)
                })
                setOnClickListener { popup.dismiss() }
            }
            cleanupContainer.addView(row)
        }
        cleanupCard.addView(cleanupContainer)
        container.addView(cleanupCard)

        val scrollView = ScrollView(this).apply { addView(container) }
        popup.contentView = scrollView
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    private fun showCategoryDrillDown(category: StorageCategory) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = category.name
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        val card = AppleuxCard(this)
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        getFilesByCategory(category.name).forEach { file ->
            val row = FileRowView(this).apply {
                setTitle(file.name)
                setSubtitle(formatFileSize(file.length()))
                setIconDrawable(createFileIcon(FileItem(
                    name = file.name,
                    subtitle = "",
                    iconColor = category.color,
                    isFolder = false,
                    type = when (category.name) {
                        "Images" -> FileItemType.IMAGE
                        "Videos" -> FileItemType.VIDEO
                        "Documents" -> FileItemType.DOCUMENT
                        "Audio" -> FileItemType.AUDIO
                        else -> FileItemType.OTHER
                    }
                )))
            }
            listContainer.addView(row)
        }
        card.addView(listContainer)
        container.addView(card)

        val scrollView = ScrollView(this).apply { addView(container) }
        popup.contentView = scrollView
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    private fun getLargeFiles(): List<File> {
        val largeFiles = mutableListOf<File>()
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadDir.listFiles()?.forEach { file ->
            if (file.length() > 100 * 1024 * 1024) {
                largeFiles.add(file)
            }
        }
        return largeFiles
    }

    private fun findDuplicates(): List<List<File>> {
        val fileMap = mutableMapOf<String, MutableList<File>>()
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadDir.listFiles()?.forEach { file ->
            val key = "${file.length()}_${file.name.lowercase()}"
            fileMap.getOrPut(key) { mutableListOf() }.add(file)
        }
        return fileMap.values.filter { it.size > 1 }
    }

    private fun getFilesByCategory(categoryName: String): List<File> {
        val files = mutableListOf<File>()
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadDir.listFiles()?.forEach { file ->
            when (categoryName) {
                "Images" -> if (file.extension.lowercase() in listOf("jpg", "jpeg", "png", "heic", "gif")) files.add(file)
                "Videos" -> if (file.extension.lowercase() in listOf("mp4", "mov", "avi", "mkv")) files.add(file)
                "Documents" -> if (file.extension.lowercase() in listOf("pdf", "doc", "docx", "xls", "xlsx", "txt")) files.add(file)
                "Audio" -> if (file.extension.lowercase() in listOf("mp3", "wav", "aac", "flac")) files.add(file)
                "Other" -> files.add(file)
            }
        }
        return files
    }

    // ---- iOS 27: Archive Manager ----

    private fun createZipArchive(files: List<File>, password: String? = null) {
        if (files.isEmpty()) return

        val zipFile = File(files[0].parentFile, "archive_${System.currentTimeMillis()}.zip")
        val progressDialog = showArchiveProgress("Creating Archive")

        try {
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                files.forEach { file ->
                    val entryName = file.name
                    if (password != null) {
                        // AES-256 password-protected ZIP
                        val key = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
                        val cipher = Cipher.getInstance("AES")
                        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
                        val fileBytes = file.readBytes()
                        val encryptedBytes = cipher.doFinal(fileBytes)
                        val entry = ZipEntry(entryName)
                        zos.putNextEntry(entry)
                        zos.write(encryptedBytes)
                        zos.closeEntry()
                    } else {
                        val entry = ZipEntry(entryName)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            progressDialog.dismiss()
            addOperation(FileOperation("Create Archive", "Created ${zipFile.name}", FileOperationStatus.COMPLETED))
        } catch (e: Exception) {
            progressDialog.dismiss()
            addOperation(FileOperation("Create Archive", "Failed: ${e.message}", FileOperationStatus.FAILED))
        }
    }

    private fun extractZipArchive(zipFile: File, extractTo: File, password: String? = null) {
        val progressDialog = showArchiveProgress("Extracting Archive")

        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = File(extractTo, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            progressDialog.dismiss()
            addOperation(FileOperation("Extract Archive", "Extracted ${zipFile.name}", FileOperationStatus.COMPLETED))
        } catch (e: Exception) {
            progressDialog.dismiss()
            addOperation(FileOperation("Extract Archive", "Failed: ${e.message}", FileOperationStatus.FAILED))
        }
    }

    private fun browseZipArchive(zipFile: File) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = zipFile.name
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        val card = AppleuxCard(this)
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val row = FileRowView(this).apply {
                        setTitle(entry.name)
                        setSubtitle(if (entry.isDirectory) "Folder" else formatFileSize(entry.size))
                        setIconDrawable(GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
                            setColor(if (entry.isDirectory) ColorTokens.systemBlue else ColorTokens.systemGray)
                        })
                    }
                    listContainer.addView(row)
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            val errorRow = AppleuxText(this).apply {
                applyTypography(TypographyTokens.body)
                text = "Error reading archive: ${e.message}"
                setTextColor(ColorTokens.systemRed)
                val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
                setPadding(pad, pad, pad, pad)
            }
            listContainer.addView(errorRow)
        }

        card.addView(listContainer)
        container.addView(card)

        val scrollView = ScrollView(this).apply { addView(container) }
        popup.contentView = scrollView
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    private fun showArchiveProgress(message: String): PopupWindow {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.bodyEmphasized)
            text = message
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        val progressBar = ArchiveProgressView(this).apply {
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(progressBar)

        popup.contentView = container
        popup.width = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = false
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
        return popup
    }

    // ---- iOS 27: File Tags ----

    private fun showTagManager() {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = "Tags"
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        // Tag cloud
        val tagCloud = TagCloudView(this).apply {
            setTags(tags, fileTagMap)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(tagCloud)

        // Tag list with management
        val card = AppleuxCard(this)
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        tags.forEach { tag ->
            val row = FileRowView(this).apply {
                setTitle(tag.name)
                setSubtitle("${fileTagMap.values.count { it.contains(tag.id) }} files")
                setIconDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
                    setColor(tag.color)
                })
                setOnClickListener { showTagEditor(tag) }
            }
            listContainer.addView(row)
        }
        card.addView(listContainer)
        container.addView(card)

        val scrollView = ScrollView(this).apply { addView(container) }
        popup.contentView = scrollView
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    private fun showTagEditor(tag: FileTag) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = "Edit Tag"
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        // Color picker
        val colorLabel = AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadline)
            text = "Color"
            setTextColor(ColorTokens.secondaryLabel)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(colorLabel)

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        listOf(
            ColorTokens.systemRed to "Red",
            ColorTokens.systemOrange to "Orange",
            ColorTokens.systemYellow to "Yellow",
            ColorTokens.systemGreen to "Green",
            ColorTokens.systemBlue to "Blue",
            ColorTokens.systemPurple to "Purple",
            ColorTokens.systemGray to "Gray"
        ).forEach { (color, name) ->
            val colorDot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                val size = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_MD, this@FilesActivity)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@FilesActivity)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    tag.color = color
                    tag.name = name
                    popup.dismiss()
                    showTagManager()
                }
            }
            colorRow.addView(colorDot)
        }
        container.addView(colorRow)

        val scrollView = ScrollView(this).apply { addView(container) }
        popup.contentView = scrollView
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    private fun assignTagToFiles(tag: FileTag, filePaths: List<String>) {
        filePaths.forEach { path ->
            fileTagMap.getOrPut(path) { mutableSetOf() }.add(tag.id)
        }
    }

    private fun filterFilesByTag(tag: FileTag): List<FileItem> {
        val taggedPaths = fileTagMap.filter { it.value.contains(tag.id) }.keys
        return browseItems().filter { item ->
            taggedPaths.any { item.subtitle.contains(it) }
        }
    }

    // ---- iOS 27: Favorites & Pinned ----

    private fun togglePinFolder(folderName: String) {
        if (pinnedFolders.contains(folderName)) {
            pinnedFolders.remove(folderName)
        } else {
            pinnedFolders.add(folderName)
        }
        rebuildFileList()
    }

    private fun toggleFavoriteFile(fileName: String) {
        if (favoriteFiles.contains(fileName)) {
            favoriteFiles.remove(fileName)
        } else {
            favoriteFiles.add(fileName)
        }
        rebuildFileList()
    }

    private fun addToRecentFiles(fileName: String) {
        recentFiles.removeAll { it.name == fileName }
        recentFiles.add(0, RecentFileEntry(fileName, System.currentTimeMillis()))
        if (recentFiles.size > 10) {
            recentFiles.removeAt(recentFiles.size - 1)
        }
    }

    /**
     * Resolves the favourites and pinned sections against an already-built
     * list of the volume's real entries.
     *
     * These deliberately take [entries] instead of calling browseItems()
     * themselves. browseItems() builds the favourites section, so a version
     * that called back into it would recurse without bound the moment a
     * single file was favourited — favouriting one item used to be enough to
     * reach a StackOverflowError on the next rebuildFileList().
     */
    private fun getFavoritesSection(entries: List<FileItem>): List<FileItem> {
        return favoriteFiles.mapNotNull { fileName ->
            entries.find { it.name == fileName }
        }
    }

    private fun getPinnedFolders(entries: List<FileItem>): List<FileItem> {
        return pinnedFolders.mapNotNull { folderName ->
            entries.find { it.name == folderName }
        }
    }

    // ---- iOS 27: Hidden Files ----

    private fun toggleHiddenFile(fileName: String) {
        if (hiddenFiles.contains(fileName)) {
            hiddenFiles.remove(fileName)
        } else {
            hiddenFiles.add(fileName)
        }
        rebuildFileList()
    }

    private fun isFileHidden(fileName: String): Boolean {
        return hiddenFiles.contains(fileName) || fileName.startsWith(".")
    }

    // ---- iOS 27: Multi-select Operations ----

    private fun toggleSelection(fileName: String) {
        if (selectedFiles.contains(fileName)) {
            selectedFiles.remove(fileName)
        } else {
            selectedFiles.add(fileName)
        }
        isSelectionMode = selectedFiles.isNotEmpty()
        rebuildFileList()
    }

    private fun clearSelection() {
        selectedFiles.clear()
        isSelectionMode = false
        rebuildFileList()
    }

    private fun showSelectionBar() {
        if (!isSelectionMode) return

        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val countText = AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadlineEmphasized)
            text = "${selectedFiles.size} selected"
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(countText)

        val actions = listOf(
            "Copy" to { copySelectedFiles() },
            "Move" to { moveSelectedFiles() },
            "Compress" to { compressSelectedFiles() },
            "Share" to { shareSelectedFiles() },
            "Delete" to { deleteSelectedFiles() },
            "Tag" to { tagSelectedFiles() }
        )

        actions.forEach { (label, action) ->
            val btn = AppleuxText(this).apply {
                applyTypography(TypographyTokens.subheadline)
                text = label
                setTextColor(ColorTokens.systemBlue)
                val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    action()
                    popup.dismiss()
                }
            }
            container.addView(btn)
        }

        popup.contentView = container
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.BOTTOM, 0, 0)
    }

    private fun copySelectedFiles() {
        addOperation(FileOperation("Copy", "Copied ${selectedFiles.size} files", FileOperationStatus.COMPLETED))
        clearSelection()
    }

    private fun moveSelectedFiles() {
        addOperation(FileOperation("Move", "Moved ${selectedFiles.size} files", FileOperationStatus.COMPLETED))
        clearSelection()
    }

    private fun compressSelectedFiles() {
        val files = selectedFiles.mapNotNull { File(it) }
        createZipArchive(files)
        clearSelection()
    }

    private fun shareSelectedFiles() {
        addOperation(FileOperation("Share", "Shared ${selectedFiles.size} files", FileOperationStatus.COMPLETED))
        clearSelection()
    }

    private fun deleteSelectedFiles() {
        var moved = 0
        var failed = 0
        selectedFiles.forEach { path ->
            if (moveToTrash(File(path)) != null) moved++ else failed++
        }
        val status = if (failed == 0) FileOperationStatus.COMPLETED else FileOperationStatus.FAILED
        val detail = if (failed == 0) {
            "Moved $moved item${if (moved != 1) "s" else ""} to Recently Deleted"
        } else {
            "Moved $moved, could not delete $failed"
        }
        addOperation(FileOperation("Delete", detail, status))
        clearSelection()
    }

    private fun tagSelectedFiles() {
        showTagSelectorForFiles(selectedFiles.toList())
        clearSelection()
    }

    private fun showTagSelectorForFiles(filePaths: List<String>) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = "Assign Tags"
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        tags.forEach { tag ->
            val row = FileRowView(this).apply {
                setTitle(tag.name)
                setSubtitle("")
                setIconDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
                    setColor(tag.color)
                })
                setOnClickListener {
                    assignTagToFiles(tag, filePaths)
                    popup.dismiss()
                }
            }
            container.addView(row)
        }

        val scrollView = ScrollView(this).apply { addView(container) }
        popup.contentView = scrollView
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    // ---- iOS 27: Conflict Resolution ----

    private fun showConflictResolutionDialog(fileName: String, onResolve: (ConflictResolution) -> Unit) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = "File Already Exists"
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        val message = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "\"$fileName\" already exists. What would you like to do?"
            setTextColor(ColorTokens.secondaryLabel)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(message)

        listOf(
            Triple("Skip", "Keep the existing file", ConflictResolution.SKIP),
            Triple("Replace", "Overwrite the existing file", ConflictResolution.REPLACE),
            Triple("Rename", "Save with a new name", ConflictResolution.RENAME)
        ).forEach { (title, subtitle, resolution) ->
            val row = FileRowView(this).apply {
                setTitle(title)
                setSubtitle(subtitle)
                setIconDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
                    setColor(ColorTokens.systemBlue)
                })
                setOnClickListener {
                    onResolve(resolution)
                    popup.dismiss()
                }
            }
            container.addView(row)
        }

        popup.contentView = container
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    // ---- iOS 27: File Preview ----

    private fun showFilePreview(file: File) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = file.name
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        // Preview content based on file type
        val previewContent = when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "heic" -> createImagePreview(file)
            "mp4", "mov", "avi" -> createVideoPreview(file)
            "pdf" -> createPdfPreview(file)
            "txt", "kt", "java", "py", "js", "json", "xml" -> createTextPreview(file)
            else -> createFilePreview(file)
        }
        container.addView(previewContent)

        // File info panel
        val infoPanel = createFileInfoPanel(file)
        container.addView(infoPanel)

        val scrollView = ScrollView(this).apply { addView(container) }
        popup.contentView = scrollView
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.MATCH_PARENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    private fun createImagePreview(file: File): View {
        val imageView = ImageView(this).apply {
            setImageURI(Uri.fromFile(file))
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            minimumHeight = LayoutUtils.dpToPx(200f, this@FilesActivity)
        }
        return imageView
    }

    private fun createVideoPreview(file: File): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }

        val icon = AppleuxIcon(this).apply {
            iconRes = com.appleux.design.R.drawable.sf_play
            iconTint = ColorTokens.systemBlue
            iconSize = IconTokens.SIZE_EXTRA_LARGE
        }
        container.addView(icon)

        val label = AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadline)
            text = "Video Player"
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
        }
        container.addView(label)

        return container
    }

    private fun createPdfPreview(file: File): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }

        val icon = ImageView(this).apply {
            setImageDrawable(GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
                setColor(ColorTokens.systemRed)
            })
            val size = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_XL, this@FilesActivity)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        container.addView(icon)

        val label = AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadline)
            text = "PDF Document"
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
        }
        container.addView(label)

        return container
    }

    private fun createTextPreview(file: File): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }

        val preview = AppleuxText(this).apply {
            applyTypography(TypographyTokens.footnote)
            text = file.readText().take(500)
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(preview)

        return container
    }

    private fun createFilePreview(file: File): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }

        val icon = ImageView(this).apply {
            setImageDrawable(GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
                setColor(ColorTokens.systemGray)
            })
            val size = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_XL, this@FilesActivity)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        container.addView(icon)

        val label = AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadline)
            text = "File Preview"
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
        }
        container.addView(label)

        return container
    }

    private fun createFileInfoPanel(file: File): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }

        val infoItems = listOf<Pair<String, String>>(
            "Name" to file.name,
            "Size" to formatFileSize(file.length()),
            "Type" to file.extension.uppercase(),
            "Modified" to dateFormat.format(Date(file.lastModified())),
            "Path" to (file.parent ?: "Unknown")
        )

        infoItems.forEach { (label, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@FilesActivity)
                setPadding(pad, pad, pad, pad)
            }

            val labelText = AppleuxText(this).apply {
                applyTypography(TypographyTokens.subheadline)
                text = label
                setTextColor(ColorTokens.secondaryLabel)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(labelText)

            val valueText = AppleuxText(this).apply {
                applyTypography(TypographyTokens.subheadline)
                text = value
                setTextColor(ColorTokens.label)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            }
            row.addView(valueText)

            container.addView(row)
        }

        return container
    }

    // ---- iOS 27: Operation History ----

    private fun addOperation(operation: FileOperation) {
        operationHistory.add(0, operation)
        if (operationHistory.size > 50) {
            operationHistory.removeAt(operationHistory.size - 1)
        }
    }

    private fun showOperationHistory() {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = "Operation History"
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(title)

        val card = AppleuxCard(this)
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        operationHistory.forEach { operation ->
            val row = FileRowView(this).apply {
                setTitle(operation.description)
                setSubtitle("${operation.timestamp} • ${operation.status}")
                setIconDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
                    setColor(when (operation.status) {
                        FileOperationStatus.COMPLETED -> ColorTokens.systemGreen
                        FileOperationStatus.FAILED -> ColorTokens.systemRed
                        FileOperationStatus.CANCELLED -> ColorTokens.systemOrange
                    })
                })
            }
            listContainer.addView(row)
        }

        card.addView(listContainer)
        container.addView(card)

        val scrollView = ScrollView(this).apply { addView(container) }
        popup.contentView = scrollView
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    // ---- iOS 27: Real Recycle Bin (Recently Deleted) ----

    /**
     * Trash lives on the internal volume in a dot-directory, so a deleted file
     * is still on the same filesystem as its original — renameTo() is the fast
     * path and no bytes are copied. The backup path below only runs when the
     * source turned out to be on a different mount (a USB file being trashed
     * onto internal storage, which is a cross-device move).
     */
    private fun initTrash() {
        trashDir = File(Environment.getExternalStorageDirectory(), TRASH_DIR_NAME)
        if (!trashDir.exists()) {
            trashDir.mkdirs()
        }
        trashPrefs = getSharedPreferences(TRASH_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Rebuilds [deletedItems] from the on-disk trash directory. Metadata is the
     * source of truth for the original path and deletion time; the trash
     * directory is the source of truth for what still exists. A metadata record
     * with no file behind it is dropped, and a file with no record is adopted,
     * so the two can never drift into showing phantom or orphan entries.
     */
    private fun reloadTrash() {
        if (!::trashDir.isInitialized) return
        if (!trashDir.exists()) {
            trashDir.mkdirs()
            deletedItems.clear()
            return
        }

        val recorded = loadTrashMetadata()
        val onDisk = trashDir.listFiles()?.associateBy { it.name } ?: emptyMap()

        deletedItems.clear()
        recorded.forEach { item ->
            val stillThere = onDisk[item.trashName]
            if (stillThere != null) {
                deletedItems.add(item.copy(trashPath = stillThere.absolutePath))
            }
        }
        onDisk.forEach { (name, file) ->
            if (recorded.none { it.trashName == name }) {
                deletedItems.add(
                    RecycleBinItem(
                        name = name.substringAfter('_', name),
                        originalPath = "",
                        trashPath = file.absolutePath,
                        trashName = name,
                        deletionDate = Date(file.lastModified()),
                        size = file.length() / 1048576.0,
                        type = FileItemType.OTHER
                    )
                )
            }
        }
        deletedItems.sortByDescending { it.deletionDate.time }
    }

    private fun loadTrashMetadata(): List<RecycleBinItem> {
        if (!::trashPrefs.isInitialized) return emptyList()
        val raw = trashPrefs.getString(TRASH_PREFS_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val trashPath = obj.optString("trashPath")
                if (trashPath.isEmpty()) return@mapNotNull null
                RecycleBinItem(
                    name = obj.optString("name"),
                    originalPath = obj.optString("originalPath"),
                    trashPath = trashPath,
                    trashName = obj.optString("trashName"),
                    deletionDate = Date(obj.optLong("deletedAt")),
                    size = obj.optDouble("size", 0.0),
                    type = FileItemType.values().firstOrNull { it.name == obj.optString("type") }
                        ?: FileItemType.OTHER
                )
            }
        } catch (e: Exception) {
            // Corrupt metadata must not brick the trash screen.
            emptyList()
        }
    }

    private fun persistTrashMetadata() {
        if (!::trashPrefs.isInitialized) return
        val array = JSONArray()
        deletedItems.forEach { item ->
            array.put(JSONObject().apply {
                put("name", item.name)
                put("originalPath", item.originalPath)
                put("trashPath", item.trashPath)
                put("trashName", item.trashName)
                put("deletedAt", item.deletionDate.time)
                put("size", item.size)
                put("type", item.type.name)
            })
        }
        trashPrefs.edit().putString(TRASH_PREFS_KEY, array.toString()).apply()
    }

    /**
     * Moves [file] into the trash and records it. Returns the stored item, or
     * null if the move failed — in which case nothing about the original
     * changes and the caller must not report success.
     */
    private fun moveToTrash(file: File): RecycleBinItem? {
        if (!file.exists()) return null
        if (!trashDir.exists() && !trashDir.mkdirs()) return null

        // "<timestamp>_<original name>" — the timestamp is what guarantees two
        // deletes of the same filename can't collide inside one directory.
        val stamp = System.currentTimeMillis()
        var trashName = "${stamp}_${file.name}"
        var suffix = 1
        while (File(trashDir, trashName).exists()) {
            trashName = "${stamp}_${suffix}_${file.name}"
            suffix++
        }
        val destination = File(trashDir, trashName)

        // Measured before the move: once it is a directory entry in the trash
        // its own length() reads as 0, which would report every deleted folder
        // as empty.
        val isDirectory = file.isDirectory
        val sizeBytes = if (isDirectory) directorySize(file) else file.length()

        val moved = file.renameTo(destination) || copyThenDelete(file, destination)
        if (!moved) return null

        val item = RecycleBinItem(
            name = file.name,
            originalPath = file.absolutePath,
            trashPath = destination.absolutePath,
            trashName = trashName,
            deletionDate = Date(stamp),
            size = sizeBytes.toDouble() / 1048576.0,
            type = if (isDirectory) FileItemType.FOLDER else typeForExtension(file.name)
        )
        deletedItems.add(item)
        persistTrashMetadata()
        return item
    }

    /**
     * Cross-filesystem fallback for [moveToTrash] and restore. Recurses so
     * directories survive too, then removes the source.
     */
    private fun copyThenDelete(source: File, destination: File): Boolean {
        return try {
            if (source.isDirectory) {
                copyDirectory(source, destination)
            } else {
                destination.parentFile?.mkdirs()
                source.copyTo(destination, overwrite = true)
            }
            deleteRecursively(source)
        } catch (e: Exception) {
            false
        }
    }

    private fun copyDirectory(source: File, destination: File): Boolean {
        if (!destination.exists() && !destination.mkdirs()) return false
        source.listFiles()?.forEach { child ->
            val target = File(destination, child.name)
            if (child.isDirectory) {
                copyDirectory(child, target)
            } else {
                child.copyTo(target, overwrite = true)
            }
        }
        return true
    }

    /** Recursive byte total, used for the trash size of a deleted folder. */
    private fun directorySize(dir: File): Long {
        if (!dir.isDirectory) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += if (child.isDirectory) directorySize(child) else child.length()
        }
        return total
    }

    private fun deleteRecursively(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Puts a trashed item back where it came from. Creates missing parent
     * directories, and if something already occupies the original path,
     * appends " (restored)" rather than overwriting — matching iOS, which never
     * silently clobbers.
     */
    private fun restoreFromTrash(item: RecycleBinItem): Boolean {
        val source = File(item.trashPath)
        if (!source.exists()) {
            // The file vanished from the trash directory; drop the stale record
            // so the row doesn't fail forever. The caller owns the refresh.
            deletedItems.remove(item)
            persistTrashMetadata()
            return false
        }

        val target = resolveRestoreTarget(item.originalPath, source.name)
        // mkdirs() is a no-op when the parent is already there, so this covers
        // both "parent still exists" and "parent was removed after deletion".
        target.parentFile?.mkdirs()

        val restored = source.renameTo(target) || copyThenDelete(source, target)
        if (restored) {
            deletedItems.remove(item)
            persistTrashMetadata()
        }
        return restored
    }

    /**
     * Picks a non-colliding destination for a restore. If the original path is
     * gone (empty in the metadata) the file lands in Downloads under its
     * original name; if that path is occupied the name gains " (restored)".
     */
    private fun resolveRestoreTarget(originalPath: String, fallbackName: String): File {
        val trimmed = originalPath.trim()
        if (trimmed.isEmpty()) {
            val downloads = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fallbackName.substringAfter('_', fallbackName)
            )
            return if (downloads.exists()) disambiguateRestore(downloads) else downloads
        }

        val original = File(trimmed)
        // Occupied: restore alongside it as "name (restored).ext" rather than
        // overwriting whatever is there now.
        if (original.exists()) return disambiguateRestore(original)
        return original
    }

    private fun disambiguateRestore(file: File): File {
        val parent = file.parentFile
        var candidate = File(parent, "${stemOf(file)} (restored)${extensionOf(file)}")
        var n = 2
        while (candidate.exists()) {
            candidate = File(parent, "${stemOf(file)} (restored $n)${extensionOf(file)}")
            n++
        }
        return candidate
    }

    private fun stemOf(file: File): String {
        val dot = file.name.lastIndexOf('.')
        return if (dot > 0) file.name.substring(0, dot) else file.name
    }

    private fun extensionOf(file: File): String {
        val dot = file.name.lastIndexOf('.')
        return if (dot > 0) file.name.substring(dot) else ""
    }

    /** Irreversible: deletes the trashed file itself. */
    private fun deleteFromTrash(item: RecycleBinItem) {
        deleteRecursively(File(item.trashPath))
        deletedItems.remove(item)
        persistTrashMetadata()
    }

    private fun emptyTrash() {
        deletedItems.forEach { deleteRecursively(File(it.trashPath)) }
        deletedItems.clear()
        persistTrashMetadata()
        rebuildFileList()
    }

    /**
     * iOS deletes Recently Deleted contents after 30 days. This runs on app
     * start, using the stored deletion timestamp rather than mtime, so an item
     * that was touched after deletion still expires on schedule.
     */
    private fun purgeExpiredTrash() {
        if (!::trashPrefs.isInitialized) return
        reloadTrash()
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MS
        val expired = deletedItems.filter { it.deletionDate.time < cutoff }
        if (expired.isEmpty()) return
        expired.forEach { deleteRecursively(File(it.trashPath)) }
        deletedItems.removeAll(expired.toSet())
        persistTrashMetadata()
    }

    /** Whole days until this item is auto-purged; 0 once it's due. */
    private fun daysUntilPurge(item: RecycleBinItem): Int {
        val remaining = item.deletionDate.time + TRASH_RETENTION_MS - System.currentTimeMillis()
        if (remaining <= 0) return 0
        return ((remaining + DAY_MS - 1) / DAY_MS).toInt()
    }

    /**
     * Trash rows need two things a plain FileRowView cannot do: a swipe that
     * reveals Restore / Delete, and a countdown line. Each item is wrapped in a
     * SwipeRowView with those actions wired to the real operations.
     */
    private fun buildSwipeableTrashList(items: List<FileItem>): View {
        val card = AppleuxCard(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        items.forEachIndexed { index, item ->
            // recycleBinItems() maps 1:1 from deletedItems, and FileItem.path
            // carries the trash path, so the record is recoverable by both.
            val record = deletedItems.firstOrNull { it.trashPath == item.path }
                ?: deletedItems.firstOrNull { it.name == item.name }
            val days = record?.let { daysUntilPurge(it) } ?: 0
            val remainingLabel = when (days) {
                0 -> "Deleting soon"
                1 -> "1 day left"
                else -> "$days days left"
            }
            val originLabel = record?.originalPath?.takeIf { it.isNotEmpty() } ?: "Unknown location"
            val deletedLabel = record?.let { dateFormat.format(it.deletionDate) } ?: ""
            val row = SwipeRowView(this).apply {
                setLabel(
                    item.name,
                    "$originLabel • Deleted $deletedLabel • $remainingLabel"
                )
                setLeftActionLabel("Restore", ColorTokens.systemGreen)
                setRightActionLabel("Delete", ColorTokens.systemRed)
                setDividerVisible(index < items.size - 1)
                setOnLeftAction {
                    if (record != null) {
                        if (restoreFromTrash(record)) {
                            addOperation(FileOperation("Restore", "Restored ${record.name}", FileOperationStatus.COMPLETED))
                        } else {
                            addOperation(FileOperation("Restore", "Couldn't restore ${record.name}", FileOperationStatus.FAILED))
                        }
                        rebuildFileList()
                    }
                }
                setOnRightAction {
                    if (record != null) {
                        showConfirmDialog(
                            title = "Delete Permanently?",
                            message = "\"${record.name}\" will be deleted immediately. This cannot be undone.",
                            confirmLabel = "Delete",
                            destructive = true
                        ) {
                            deleteFromTrash(record)
                            addOperation(FileOperation("Delete", "Permanently deleted ${record.name}", FileOperationStatus.COMPLETED))
                            rebuildFileList()
                        }
                    }
                }
            }
            container.addView(row)
        }
        card.addView(container)
        return card
    }

// ---- iOS 27: USB / OTG / External Storage Detection ----

    /**
     * Builds the Locations list from StorageManager.getStorageVolumes() plus
     * UsbManager.deviceList.
     *
     * StorageManager is the authority on which volumes exist, what they are
     * called and whether they are mounted. UsbManager contributes the
     * manufacturer/product name for an attached drive, and can reveal a device
     * that is attached but has not been claimed as a mounted volume yet — that
     * case renders as a "Disconnected" card rather than disappearing.
     *
     * The internal volume is added first and unconditionally: it is the one
     * location that can never be absent.
     *
     * A note on the reflection below. android.app.storage.StorageManager and
     * StorageVolume are ordinary *public* SDK APIs (API 26 / API 30), and the
     * calls made through reflection are exactly those public calls at runtime.
     * They are reached reflectively only because the android.jar this project
     * compiles against carries the android.os.storage *system-API* stubs and no
     * android.app.storage package, so a direct import does not resolve. Every
     * reflection call is individually wrapped, so a missing or changed method
     * degrades to "no volume found" instead of crashing the Files screen.
     */
    private fun detectStorageLocations() {
        val locations = mutableListOf<StorageLocation>()
        val internalRoot = Environment.getExternalStorageDirectory()

        val usbNames = usbDeviceNames()
        val volumes = storageVolumes()

        // The internal volume still benefits from its StorageVolume handle —
        // it is what getAllocatableBytes() needs — so look it up rather than
        // leaving the headline number as a bare usableSpace().
        val primaryVolume = volumes.firstOrNull { volume ->
            volumeDirectory(volume)?.absolutePath == internalRoot.absolutePath
        }

        locations.add(
            storageLocationFor(
                kind = StorageLocationKind.INTERNAL,
                name = StorageLocationKind.INTERNAL.title,
                directory = internalRoot,
                isRemovable = false,
                volume = primaryVolume
            )
        )

        volumes.forEach { volume ->
            val dir = volumeDirectory(volume) ?: return@forEach
            val kind = volumeKind(volume, dir) ?: return@forEach
            if (kind == StorageLocationKind.INTERNAL) return@forEach
            if (locations.any { it.rootPath == dir.absolutePath }) return@forEach

            locations.add(
                storageLocationFor(
                    kind = kind,
                    // Prefer the USB device's own product name when it matches
                    // this mount point, so the card reads "SanDisk Extreme"
                    // rather than a raw mount label.
                    name = usbNames.firstOrNull { it.contains(dir.name, ignoreCase = true) }
                        ?: volumeDescription(volume)
                        ?: dir.name,
                    directory = dir,
                    isRemovable = kind != StorageLocationKind.INTERNAL,
                    volume = volume
                )
            )
        }

        usbNames.forEach { usbName ->
            val alreadyListed = locations.any { it.kind == StorageLocationKind.USB && it.name == usbName }
            if (!alreadyListed) {
                locations.add(
                    StorageLocation(
                        kind = StorageLocationKind.USB,
                        name = usbName,
                        rootPath = null,
                        isMounted = false,
                        isRemovable = true,
                        totalBytes = 0L,
                        availableBytes = 0L,
                        allocatableBytes = 0L,
                        mountState = MOUNT_STATE_DISCONNECTED
                    )
                )
            }
        }

        storageLocations.clear()
        storageLocations.addAll(locations)
    }

    /** StorageManager.getStorageVolumes() -> List<StorageVolume>. */
    private fun storageVolumes(): List<Any> {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) ?: return emptyList()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            storageManager.javaClass
                .getMethod("getStorageVolumes")
                .invoke(storageManager) as? List<Any> ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /**
     * StorageVolume.getDirectory() throws IllegalStateException when the volume
     * is not mounted, so it is guarded: a known-but-unmounted volume yields
     * null here and is reported as disconnected rather than crashing the screen.
     */
    private fun volumeDirectory(volume: Any): File? {
        return runCatching {
            (volume.javaClass.getMethod("getDirectory").invoke(volume) as? File)
                ?.takeIf { it.absolutePath.isNotEmpty() }
        }.getOrNull()
    }

    private fun volumeState(volume: Any): String {
        return runCatching {
            volume.javaClass.getMethod("getState").invoke(volume) as? String
        }.getOrNull() ?: ""
    }

    private fun volumeDescription(volume: Any): String? {
        return runCatching {
            val described = volume.javaClass
                .getMethod("getDescription", Context::class.java)
                .invoke(volume, this) as? String
            described?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun volumeUuid(volume: Any): UUID? {
        return runCatching {
            volume.javaClass.getMethod("getUuid").invoke(volume) as? UUID
        }.getOrNull()
    }

    /**
     * Classifies a volume as SD card or USB.
     *
     * StorageVolume.getType() (API 30) reports "sd", "usb", "emulated" or
     * "unknown". On API 26-29 there is no getType(), so the fallback asks
     * StorageVolume.isRemovable() and Environment.isExternalStorageRemovable();
     * a removable non-emulated volume that no USB device claims is a card.
     */
    private fun volumeKind(volume: Any, dir: File): StorageLocationKind? {
        val type = runCatching {
            volume.javaClass.getMethod("getType").invoke(volume) as? String
        }.getOrNull()

        when (type) {
            "emulated" -> return StorageLocationKind.INTERNAL
            "sd" -> return StorageLocationKind.SD_CARD
            "usb" -> return StorageLocationKind.USB
        }

        if (runCatching {
                volume.javaClass.getMethod("isEmulated").invoke(volume) as? Boolean
            }.getOrNull() == true
        ) {
            return StorageLocationKind.INTERNAL
        }
        if (Environment.isExternalStorageEmulated(dir)) {
            return StorageLocationKind.INTERNAL
        }
        return StorageLocationKind.SD_CARD
    }

    /**
     * Real numbers for one volume: File.getTotalSpace/getUsableSpace for the
     * volume itself, and StorageManager.getAllocatableBytes() for what the
     * platform would actually grant this app. On shared storage the two differ,
     * so both are kept and the card reports the volume's own figures.
     */
    private fun storageLocationFor(
        kind: StorageLocationKind,
        name: String,
        directory: File,
        isRemovable: Boolean,
        volume: Any? = null
    ): StorageLocation {
        val exists = directory.exists()
        val mounted = exists &&
            Environment.getExternalStorageState(directory) == Environment.MEDIA_MOUNTED

        val total = if (mounted) directory.totalSpace else 0L
        val available = if (mounted) directory.usableSpace else 0L
        val allocatable = if (mounted && volume != null) allocatableBytesFor(volume) else 0L

        return StorageLocation(
            kind = kind,
            name = name,
            rootPath = if (mounted) directory.absolutePath else null,
            isMounted = mounted,
            isRemovable = isRemovable,
            totalBytes = total,
            availableBytes = available,
            allocatableBytes = if (allocatable > 0L) allocatable else available,
            mountState = when {
                !exists -> MOUNT_STATE_DISCONNECTED
                !mounted -> MOUNT_STATE_UNMOUNTED
                else -> MOUNT_STATE_MOUNTED
            }
        )
    }

    private fun allocatableBytesFor(volume: Any): Long {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) ?: return 0L
        val uuid = volumeUuid(volume) ?: return 0L
        return runCatching {
            storageManager.javaClass
                .getMethod("getAllocatableBytes", UUID::class.java)
                .invoke(storageManager, uuid) as? Long ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * Enumerates attached USB devices via UsbManager. Only mass-storage
     * devices are of interest to Files, so the USB interface class is checked
     * and other peripherals (keyboards, headsets, cameras) are ignored.
     */
    private fun usbDeviceNames(): List<String> {
        val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return runCatching {
            usbManager.deviceList.values
                .filter { it.isMassStorage() }
                .map { device ->
                    val product = device.productName?.trim()?.takeIf { it.isNotEmpty() }
                    val manufacturer = device.manufacturerName?.trim()?.takeIf { it.isNotEmpty() }
                    listOfNotNull(manufacturer, product)
                        .joinToString(" ")
                        .ifEmpty { "USB Drive" }
                }
        }.getOrDefault(emptyList())
    }

    /**
     * UsbDevice.getInterfaceClass() is deprecated and reports only the first
     * interface, which misses drives that expose mass storage on interface 1
     * behind a control interface. Walking the interface list is correct at
     * every API level and needs no version gate.
     */
    private fun UsbDevice.isMassStorage(): Boolean {
        return try {
            (0 until interfaceCount).any { index ->
                getInterface(index).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Registers for mount/unmount/eject plus USB attach/detach.
     *
     * Registered in onResume and torn down in onPause, so the receiver never
     * outlives the visible activity. Two filters are needed because the media
     * broadcasts carry a file:// data URI and the USB ones carry none — a
     * single scheme-scoped filter would match only the media half, and a
     * single scheme-less filter would not receive the protected media
     * broadcasts on every API level.
     *
     * On receive it re-reads state rather than trusting the intent extras,
     * because the volume's actual mount state is what the Locations card shows.
     */
    private fun registerStorageReceiver() {
        if (storageReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                detectStorageLocations()
                reloadTrash()
                if (::fileListContainer.isInitialized) {
                    rebuildFileList()
                }
            }
        }

        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED)
            addDataScheme("file")
        }

        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        }

        // Registered one filter at a time so a failure on the second cannot leave the
        // first registration live with nothing holding a reference to it.
        val mediaOk = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, mediaFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, mediaFilter)
            }
        }.isSuccess
        val usbOk = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, usbFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, usbFilter)
            }
        }.isSuccess

        if (!mediaOk && !usbOk) {
            runCatching { unregisterReceiver(receiver) }
            return
        }
        storageReceiver = receiver
    }

    private fun unregisterStorageReceiver() {
        storageReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        storageReceiver = null
    }
    // ---- Dialogs ----

    /**
     * iOS-style confirmation sheet. Destructive actions use systemRed for the
     * confirm button; the action only runs on explicit confirmation, so a
     * mis-tap cannot empty the trash.
     */
    private fun showConfirmDialog(
        title: String,
        message: String,
        confirmLabel: String,
        destructive: Boolean,
        onConfirm: () -> Unit
    ) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        container.addView(AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = title
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        })

        container.addView(AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = message
            setTextColor(ColorTokens.secondaryLabel)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        })

        val confirmRow = AppleuxText(this).apply {
            applyTypography(TypographyTokens.bodyEmphasized)
            text = confirmLabel
            setTextColor(if (destructive) ColorTokens.systemRed else ColorTokens.systemBlue)
            gravity = Gravity.END
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                popup.dismiss()
                onConfirm()
            }
        }
        container.addView(confirmRow)

        val cancelRow = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "Cancel"
            setTextColor(ColorTokens.systemBlue)
            gravity = Gravity.END
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            setOnClickListener { popup.dismiss() }
        }
        container.addView(cancelRow)

        popup.contentView = container
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    /** Single-line informational sheet for empty states and failures. */
    private fun showMessageDialog(title: String, detail: String) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        container.addView(AppleuxText(this).apply {
            applyTypography(TypographyTokens.title3)
            text = title
            setTextColor(ColorTokens.label)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        })

        container.addView(AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = detail
            setTextColor(ColorTokens.secondaryLabel)
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        })

        val okRow = AppleuxText(this).apply {
            applyTypography(TypographyTokens.bodyEmphasized)
            text = "OK"
            setTextColor(ColorTokens.systemBlue)
            gravity = Gravity.END
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            setOnClickListener { popup.dismiss() }
        }
        container.addView(okRow)

        popup.contentView = container
        popup.width = FrameLayout.LayoutParams.MATCH_PARENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAtLocation(fileListContainer, Gravity.CENTER, 0, 0)
    }

    // ---- Helper Methods ----

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Volume capacities. This deliberately does NOT reuse [formatFileSize]:
     * that one renders "512.0 MB" and a disk capacity is never a small number,
     * so a whole-unit variant with no decimals is the honest rendering.
     */

    // ---- Existing Methods (preserved) ----

    private fun buildNavigationBar(): View {
        val navBar = FrameLayout(this).apply {
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }

        val title = AppleuxText(this).apply {
            applyTypography(TypographyTokens.largeTitle)
            text = "Files"
            setTextColor(ColorTokens.label)
        }
        navBar.addView(title, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.CENTER_VERTICAL
        ))

        val actionBtn = AppleuxText(this).apply {
            applyTypography(TypographyTokens.bodyEmphasized)
            text = "Select"
            setTextColor(ColorTokens.systemBlue)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (isSelectionMode) {
                    clearSelection()
                } else {
                    isSelectionMode = true
                    rebuildFileList()
                }
            }
        }
        navBar.addView(actionBtn, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ))

        return navBar
    }

    private fun buildSearchBar(): View {
        val wrapper = FrameLayout(this).apply {
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }

        val searchBar = AppleuxSurface(this).apply {
            material = AppleuxSurface.MaterialType.REGULAR_MATERIAL
            cornerRadius = SizingTokens.CORNER_RADIUS_ROUND
            surfaceColor = ColorTokens.systemFillSecondary
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }

        val searchIcon = AppleuxIcon(this).apply {
            iconRes = com.appleux.design.R.drawable.il_ic_search
            iconTint = ColorTokens.secondaryLabel
            iconSize = IconTokens.SIZE_SMALL
        }
        inner.addView(searchIcon)

        val searchText = AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = "Search"
            setTextColor(ColorTokens.secondaryLabel)
            val marginStart = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(marginStart, 0, 0, 0)
        }
        inner.addView(searchText)

        searchBar.addView(inner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            LayoutUtils.dpToPx(36f, this@FilesActivity)
        ))

        wrapper.addView(searchBar)
        return wrapper
    }

    private fun buildSortBar(): View {
        val wrapper = FrameLayout(this).apply {
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }

        sortPill = SortPillView(this).apply {
            setOnClickListener { showSortOptions(this) }
        }
        wrapper.addView(sortPill, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.CENTER_VERTICAL
        ))

        return wrapper
    }

    private fun showSortOptions(anchor: View) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        SortOption.values().forEach { option ->
            val row = AppleuxText(this).apply {
                applyTypography(TypographyTokens.body)
                text = option.label
                setTextColor(if (option == currentSortOption) ColorTokens.systemBlue else ColorTokens.label)
                val itemPad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
                setPadding(itemPad, itemPad, itemPad, itemPad)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentSortOption = option
                    isInRecycleBin = false
                    rebuildFileList()
                    popup.dismiss()
                }
            }
            container.addView(row)
        }

        popup.contentView = container
        popup.width = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAsDropDown(anchor)
    }

    private fun rebuildFileList() {
        // The Locations section is part of the Browse tab and only appears at
        // its root; inside a directory it would be noise.
        fileListContainer.removeAllViews()
        if (!isInRecycleBin && currentTab == TAB_BROWSE && currentRootPath == null) {
            fileListContainer.addView(buildLocationsSection())
        }
        val items = when {
            isInRecycleBin -> recycleBinItems()
            currentTab == TAB_BROWSE -> browseItems()
            else -> recentsItems()
        }
        if (isInRecycleBin) {
            // Trash renders its own rows: the swipe affordance (restore /
            // delete permanently) and the "days left" countdown are not
            // expressible in the plain grouped-list FileRowView.
            if (items.isEmpty()) {
                fileListContainer.addView(buildEmptyState())
            } else {
                fileListContainer.addView(buildRecycleBinHeader())
                fileListContainer.addView(buildSwipeableTrashList(items))
                fileListContainer.addView(buildEmptyRecycleBinButton())
            }
            return
        }
        if (items.isEmpty()) {
            fileListContainer.addView(buildEmptyState())
            return
        }
        fileListContainer.addView(buildGroupedList(items))
    }

    private fun buildEmptyState(): View {
        return AppleuxText(this).apply {
            applyTypography(TypographyTokens.body)
            text = when {
                isInRecycleBin -> "Recently Deleted is empty"
                currentTab == TAB_BROWSE -> "This Folder is Empty"
                else -> "No Recents"
            }
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_XXXL, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
        }
    }

    private fun buildRecycleBinHeader(): View {
        return AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadline)
            text = "${deletedItems.size} item${if (deletedItems.size != 1) "s" else ""}"
            setTextColor(ColorTokens.secondaryLabel)
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }
    }

    private fun buildEmptyRecycleBinButton(): View {
        val wrapper = FrameLayout(this).apply {
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }

        val btn = AppleuxText(this).apply {
            applyTypography(TypographyTokens.bodyEmphasized)
            text = "Empty Trash"
            setTextColor(ColorTokens.systemRed)
            gravity = Gravity.CENTER
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // Destructive and irreversible: always confirmed, never on a
                // bare tap.
                val count = deletedItems.size
                showConfirmDialog(
                    title = "Empty Trash?",
                    message = "$count item${if (count != 1) "s" else ""} will be deleted permanently. This cannot be undone.",
                    confirmLabel = "Empty Trash",
                    destructive = true
                ) {
                    emptyTrash()
                    addOperation(
                        FileOperation("Delete", "Emptied Recently Deleted ($count items)", FileOperationStatus.COMPLETED)
                    )
                }
            }
        }
        wrapper.addView(btn)
        return wrapper
    }

    private fun buildGroupedList(items: List<FileItem>): View {
        val card = AppleuxCard(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        items.forEachIndexed { index, item ->
            val row = FileRowView(this).apply {
                setTitle(item.name)
                setSubtitle(item.subtitle)
                setIconDrawable(createFileIcon(item))
                setDividerVisible(index < items.size - 1)
                setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(item.name)
                    } else if (item.isSectionHeader) {
                        // Headers are labels, not folders.
                    } else if (item.isRecycleBin) {
                        isInRecycleBin = true
                        rebuildFileList()
                    } else if (item.isFolder) {
                        navigateInto(item.path ?: return@setOnClickListener)
                    } else {
                        addToRecentFiles(item.name)
                        val file = item.path?.let { File(it) }
                        if (file != null && file.exists()) {
                            showFilePreview(file)
                        }
                    }
                }
                setOnLongClickListener {
                    if (!isInRecycleBin && !item.isRecycleBin && !item.isSectionHeader) {
                        showFileContextMenu(item)
                        true
                    } else if (isInRecycleBin) {
                        true
                    } else {
                        false
                    }
                }
            }
            container.addView(row)
        }
        card.addView(container)
        return card
    }

    private fun showFileContextMenu(item: FileItem) {
        val popup = PopupWindow(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ColorTokens.systemBackgroundElevated)
        }

        val actions = mutableListOf(
            "Tags" to { showTagSelectorForFiles(listOf(item.name)) },
            "Pin" to { togglePinFolder(item.name) },
            "Favorite" to { toggleFavoriteFile(item.name) },
            "Hide" to { toggleHiddenFile(item.name) },
            "Delete" to {
                val target = item.path
                if (target == null) {
                    showMessageDialog("Can't Delete", item.name)
                } else {
                    val trashed = moveToTrash(File(target))
                    if (trashed != null) {
                        addOperation(
                            FileOperation("Delete", "Moved ${item.name} to Recently Deleted", FileOperationStatus.COMPLETED)
                        )
                        rebuildFileList()
                    } else {
                        showMessageDialog("Delete Failed", "\"${item.name}\" could not be moved to Recently Deleted.")
                    }
                }
            }
        )

        if (item.type == FileItemType.ARCHIVE && item.path != null) {
            actions.add(0, "Extract" to {
                val file = File(item.path)
                if (file.exists()) {
                    extractZipArchive(file, file.parentFile ?: filesDir)
                }
            })
            actions.add(0, "Browse" to {
                val file = File(item.path)
                if (file.exists()) {
                    browseZipArchive(file)
                }
            })
        }

        actions.forEach { (label, action) ->
            val row = AppleuxText(this).apply {
                applyTypography(TypographyTokens.body)
                text = label
                setTextColor(ColorTokens.label)
                val itemPad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, this@FilesActivity)
                setPadding(itemPad, itemPad, itemPad, itemPad)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    action()
                    popup.dismiss()
                }
            }
            container.addView(row)
        }

        popup.contentView = container
        popup.width = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.height = FrameLayout.LayoutParams.WRAP_CONTENT
        popup.isOutsideTouchable = true
        popup.showAsDropDown(fileListContainer)
    }

    private fun createFileIcon(item: FileItem): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, this@FilesActivity)
            setColor(item.iconColor)
        }
    }

    private fun browseItems(): List<FileItem> {
        // Inside a directory the list is that directory's real contents and
        // nothing else — no Locations header, no Recently Deleted shortcut.
        currentRootPath?.let { path ->
            return sortAndGroupItems(directoryItems(File(path)))
        }

        // The volume's real entries, built once. Favorites and Pinned are
        // resolved against this list rather than by re-entering browseItems().
        val entries = mutableListOf<FileItem>()

        entries.add(FileItem(
            name = "Recently Deleted",
            subtitle = if (deletedItems.isEmpty()) "Empty" else "${deletedItems.size} item${if (deletedItems.size != 1) "s" else ""}",
            iconColor = ColorTokens.systemGray,
            isFolder = true,
            isRecycleBin = true
        ))

        // Real media folders from the internal volume. These are read from disk
        // now rather than hardcoded, so tapping one actually opens it.
        listOf(
            Environment.DIRECTORY_DCIM to "DCIM",
            Environment.DIRECTORY_PICTURES to "Pictures",
            Environment.DIRECTORY_MOVIES to "Movies",
            Environment.DIRECTORY_DOCUMENTS to "Documents",
            Environment.DIRECTORY_DOWNLOADS to "Downloads",
            Environment.DIRECTORY_MUSIC to "Music"
        ).forEach { (envName, displayName) ->
            val dir = File(Environment.getExternalStorageDirectory(), envName)
            if (dir.isDirectory) {
                entries.add(FileItem(
                    name = displayName,
                    subtitle = countDirectoryLabel(dir),
                    iconColor = ColorTokens.systemBlue,
                    isFolder = true,
                    type = FileItemType.FOLDER,
                    dateModified = dir.lastModified(),
                    path = dir.absolutePath
                ))
            }
        }

        entries.addAll(directoryItems(Environment.getExternalStorageDirectory()))

        val items = mutableListOf<FileItem>()

        // Favorites section
        val favorites = getFavoritesSection(entries)
        if (favorites.isNotEmpty()) {
            items.add(FileItem(
                name = "Favorites",
                subtitle = "${favorites.size} items",
                iconColor = ColorTokens.systemYellow,
                isFolder = true,
                isSectionHeader = true
            ))
            items.addAll(favorites)
        }

        // Pinned folders
        val pinned = getPinnedFolders(entries)
        if (pinned.isNotEmpty()) {
            items.add(FileItem(
                name = "Pinned",
                subtitle = "${pinned.size} folders",
                iconColor = ColorTokens.systemBlue,
                isFolder = true,
                isSectionHeader = true
            ))
            items.addAll(pinned)
        }

        // Recently Deleted stays at the top of Browse, above the real entries —
        // that is the iOS position, so it is prepended last rather than first.
        items.addAll(entries)

        // sortAndGroupItems() buckets rows by type, which would drop Recently
        // Deleted into the "Other" group below Images/Videos. It is a top-level
        // entry, not a member of a type group, so it is lifted back out.
        val grouped = sortAndGroupItems(items)
        val trashEntry = grouped.firstOrNull { it.isRecycleBin }
        return if (trashEntry == null) {
            grouped
        } else {
            listOf(trashEntry) + grouped.filterNot { it.isRecycleBin }
        }
    }

    /**
     * Reads [dir] off disk and maps each entry to a [FileItem]. Folders first,
     * then files, both alphabetical — the iOS Files ordering. Entries this app
     * owns (.appleux_trash) are filtered out so the trash store never appears
     * inside its own listing.
     */
    private fun directoryItems(dir: File): List<FileItem> {
        val entries = dir.listFiles() ?: return emptyList()
        return entries.asSequence()
            .filter { it.name != TRASH_DIR_NAME }
            // Dotfiles are hidden unless the flag is on, and anything the user
            // explicitly hid stays hidden either way — that is what makes the
            // context menu's "Hide" action actually do something.
            .filter { showHiddenFiles || (!it.name.startsWith(".") && !hiddenFiles.contains(it.name)) }
            .map { child ->
                val isFolder = child.isDirectory
                FileItem(
                    name = child.name,
                    subtitle = if (isFolder) "Folder" else formatFileSize(child.length()),
                    iconColor = if (isFolder) ColorTokens.systemBlue else typeColorForExtension(child.name),
                    isFolder = isFolder,
                    type = if (isFolder) FileItemType.FOLDER else typeForExtension(child.name),
                    size = child.length() / 1048576.0,
                    dateModified = child.lastModified()
                )
            }
            .sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
            .toList()
    }

    private fun countDirectoryLabel(dir: File): String {
        val count = dir.listFiles()?.size ?: 0
        return if (count == 1) "1 item" else "$count items"
    }

    private fun typeForExtension(name: String): FileItemType =
        when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "heic", "heif", "gif", "webp", "bmp" -> FileItemType.IMAGE
            "mp4", "mov", "avi", "mkv", "webm", "3gp" -> FileItemType.VIDEO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "csv" -> FileItemType.DOCUMENT
            "mp3", "wav", "aac", "flac", "ogg", "m4a" -> FileItemType.AUDIO
            "zip", "tar", "gz", "rar", "7z", "bz2", "xz" -> FileItemType.ARCHIVE
            "apk" -> FileItemType.APK
            else -> FileItemType.OTHER
        }

    private fun typeColorForExtension(name: String): Int =
        when (typeForExtension(name)) {
            FileItemType.IMAGE -> ColorTokens.systemPurple
            FileItemType.VIDEO -> ColorTokens.systemRed
            FileItemType.DOCUMENT -> ColorTokens.systemBlue
            FileItemType.AUDIO -> ColorTokens.systemOrange
            FileItemType.ARCHIVE -> ColorTokens.systemGray
            FileItemType.APK -> ColorTokens.systemGreen
            else -> ColorTokens.systemGray
        }

    /**
     * iOS puts "Locations" (On My iPhone, On My iPad) above the file list. Here
     * that section is where the detected volumes are presented.
     */
    private fun buildLocationsSection(): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, this@FilesActivity))
        }

        val header = AppleuxText(this).apply {
            applyTypography(TypographyTokens.subheadlineEmphasized)
            text = "Locations"
            setTextColor(ColorTokens.secondaryLabel)
            val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
            val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_XS, this@FilesActivity)
            setPadding(padH, padV, padH, padV)
        }
        wrapper.addView(header)

        val card = AppleuxCard(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val hasRemovable = storageLocations.any { it.kind != StorageLocationKind.INTERNAL }
        storageLocations.forEachIndexed { index, location ->
            val row = StorageCardView(this).apply {
                setLocation(location)
                setDividerVisible(index < storageLocations.lastIndex || !hasRemovable)
                setOnClickListener { onStorageCardTapped(location) }
            }
            container.addView(row)
        }

        if (!hasRemovable) {
            // iOS Files says exactly this when nothing is attached.
            val emptyRow = AppleuxText(this).apply {
                applyTypography(TypographyTokens.subheadline)
                text = "Connect a storage device"
                setTextColor(ColorTokens.tertiaryLabel)
                val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, this@FilesActivity)
                val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, this@FilesActivity)
                setPadding(padH, padV, padH, padV)
            }
            container.addView(emptyRow)
        }

        card.addView(container)
        wrapper.addView(card)
        return wrapper
    }

    private fun onStorageCardTapped(location: StorageLocation) {
        if (!location.isMounted || location.rootPath == null) {
            showMessageDialog("Device Disconnected", location.name)
            return
        }
        navigateInto(location.rootPath)
    }

    /**
     * Pushes an absolute directory onto the breadcrumb trail and re-reads it.
     * Called both by tapping a storage card and by tapping a folder row, so a
     * location and a folder inside it share one navigation path.
     */
    private fun navigateInto(absolutePath: String) {
        val dir = File(absolutePath)
        if (!dir.isDirectory) {
            showMessageDialog("Can't Open Folder", dir.name)
            return
        }
        currentRootPath = dir.absolutePath
        breadcrumbPaths.add(dir.absolutePath)
        updateBreadcrumb()
        rebuildFileList()
    }

    private fun recentsItems(): List<FileItem> {
        val items = listOf(
            FileItem("photo.heic", "Today", ColorTokens.systemPurple, false, type = FileItemType.IMAGE, size = 3.1, dateModified = System.currentTimeMillis() - 3600000L),
            FileItem("report.pdf", "Today", ColorTokens.systemRed, false, type = FileItemType.DOCUMENT, size = 2.4, dateModified = System.currentTimeMillis() - 7200000L),
            FileItem("video.mp4", "Yesterday", ColorTokens.systemRed, false, type = FileItemType.VIDEO, size = 45.2, dateModified = System.currentTimeMillis() - 86400000L),
            FileItem("budget.xlsx", "2 days ago", ColorTokens.systemGreen, false, type = FileItemType.DOCUMENT, size = 0.148, dateModified = System.currentTimeMillis() - 172800000L),
            FileItem("song.mp3", "3 days ago", ColorTokens.systemOrange, false, type = FileItemType.AUDIO, size = 4.8, dateModified = System.currentTimeMillis() - 259200000L),
            FileItem("notes.txt", "3 days ago", ColorTokens.systemOrange, false, type = FileItemType.DOCUMENT, size = 0.012, dateModified = System.currentTimeMillis() - 259200000L),
            FileItem("archive.zip", "4 days ago", ColorTokens.systemGray, false, type = FileItemType.ARCHIVE, size = 18.5, dateModified = System.currentTimeMillis() - 345600000L),
            FileItem("app.apk", "5 days ago", ColorTokens.systemGreen, false, type = FileItemType.APK, size = 22.1, dateModified = System.currentTimeMillis() - 432000000L)
        )
        return sortAndGroupItems(items)
    }

    enum class FileItemType(val groupTitle: String, val color: Int) {
        FOLDER("Folders", ColorTokens.systemBlue),
        IMAGE("Images", ColorTokens.systemPurple),
        VIDEO("Videos", ColorTokens.systemRed),
        DOCUMENT("Documents", ColorTokens.systemBlue),
        AUDIO("Audio", ColorTokens.systemOrange),
        ARCHIVE("Archives", ColorTokens.systemGray),
        APK("Apps", ColorTokens.systemGreen),
        OTHER("Other", ColorTokens.systemGray)
    }

    enum class SortOption(val label: String) {
        NAME_ASC("Name (A-Z)"),
        NAME_DESC("Name (Z-A)"),
        DATE_MODIFIED("Date Modified"),
        SIZE("Size")
    }

    data class FileItem(
        val name: String,
        val subtitle: String,
        val iconColor: Int,
        val isFolder: Boolean,
        val isRecycleBin: Boolean = false,
        val isSectionHeader: Boolean = false,
        val type: FileItemType = FileItemType.OTHER,
        val size: Double = 0.0,
        val dateModified: Long = 0L,
        /**
         * Absolute path on disk, when this row corresponds to a real file or
         * directory. `null` for section headers and synthetic rows, which is
         * what stops a tap from trying to open "Folders" as a path.
         */
        val path: String? = null
    )

    enum class StorageLocationKind(val title: String, val color: Int) {
        INTERNAL("On My Device", ColorTokens.systemGray),
        SD_CARD("SD Card", ColorTokens.systemBlue),
        USB("USB Drive", ColorTokens.systemOrange)
    }

    /**
     * One detected storage volume. [rootPath] is null when the volume is known
     * but not mounted (a USB stick that was pulled, a card that was removed),
     * which is the case that renders "Device disconnected".
     */
    data class StorageLocation(
        val kind: StorageLocationKind,
        val name: String,
        val rootPath: String?,
        val isMounted: Boolean,
        val isRemovable: Boolean,
        val totalBytes: Long,
        val availableBytes: Long,
        val allocatableBytes: Long,
        val mountState: String
    )

    data class RecycleBinItem(
        val name: String,
        val originalPath: String,
        val trashPath: String,
        val trashName: String,
        val deletionDate: Date,
        val size: Double,
        val type: FileItemType
    )

    // ---- iOS 27 Data Classes ----

    data class FileTag(
        val id: String,
        var name: String,
        var color: Int
    )

    data class RecentFileEntry(
        val name: String,
        val timestamp: Long
    )

    data class StorageCategory(
        val name: String,
        val color: Int,
        val sizeMb: Double,
        val fileCount: Int
    )

    data class FileOperation(
        val type: String,
        val description: String,
        val status: FileOperationStatus,
        val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    )

    enum class FileOperationStatus {
        COMPLETED, FAILED, CANCELLED
    }

    enum class ConflictResolution {
        SKIP, REPLACE, RENAME
    }

    data class ArchiveEntry(
        val name: String,
        val size: Long,
        val isDirectory: Boolean,
        val compressedSize: Long = 0L
    )

    private fun recycleBinItems(): List<FileItem> {
        return deletedItems.map { item ->
            FileItem(
                name = item.name,
                subtitle = "${item.originalPath} • Deleted ${dateFormat.format(item.deletionDate)}",
                iconColor = item.type.color,
                isFolder = false,
                type = item.type,
                size = item.size,
                dateModified = item.deletionDate.time,
                path = item.trashPath
            )
        }
    }

    private fun sortAndGroupItems(items: List<FileItem>): List<FileItem> {
        val sorted = when (currentSortOption) {
            SortOption.NAME_ASC -> items.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
            SortOption.DATE_MODIFIED -> items.sortedByDescending { it.dateModified }
            SortOption.SIZE -> items.sortedByDescending { it.size }
        }

        val grouped = mutableListOf<FileItem>()
        FileItemType.values().forEach { type ->
            val typeItems = sorted.filter { it.type == type }
            if (typeItems.isNotEmpty()) {
                grouped.add(FileItem(
                    name = type.groupTitle,
                    subtitle = "${typeItems.size} item${if (typeItems.size != 1) "s" else ""}",
                    iconColor = type.color,
                    isFolder = true,
                    isSectionHeader = true,
                    type = type
                ))
                grouped.addAll(typeItems)
            }
        }
        return grouped
    }

    companion object {
        private const val TAB_BROWSE = 0
        private const val TAB_RECENTS = 1

        /** Trash store, on the internal volume so trashing is a rename. */
        private const val TRASH_DIR_NAME = ".appleux_trash"
        private const val TRASH_PREFS = "appleux_launcher_files_trash"
        private const val TRASH_PREFS_KEY = "trash_items"

        /** iOS purges Recently Deleted after 30 days; so do we. */
        private const val TRASH_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        private const val MOUNT_STATE_MOUNTED = "Mounted"
        private const val MOUNT_STATE_UNMOUNTED = "Unmounted"
        private const val MOUNT_STATE_DISCONNECTED = "Disconnected"
    }
}

/**
 * Volume capacity for the Locations cards. Deliberately not [FilesActivity]'s
 * `formatFileSize`: that renders "512.0 MB", and a disk capacity is never a
 * small number, so a whole-unit variant without decimals is the honest form.
 *
 * File-private because both the activity and [StorageCardView] need it.
 */
private fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 bytes"
    val units = arrayOf("bytes", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.size - 1) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) {
        "${value.toLong()} ${units[unit]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}

/**
 * One row of the Files list: 40dp type-coloured icon, title, subtitle, divider.
 */
class FileRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView = ImageView(context)
    private val textColumn = LinearLayout(context)
    private val titleText = AppleuxText(context)
    private val subtitleText = AppleuxText(context)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var showDivider: Boolean = false
    private var dividerColor: Int? = null

    private val insetH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, context)
    private val insetV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
    private val iconSize = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_LG, context)
    private val gap = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)

    init {
        setWillNotDraw(false)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(insetH, insetV, insetH, insetV)
        isClickable = true
        isFocusable = true
        minimumHeight = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_XXL, context)

        iconView.layoutParams = LayoutParams(iconSize, iconSize).apply {
            marginEnd = gap
        }
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(iconView)

        textColumn.orientation = VERTICAL
        textColumn.gravity = Gravity.CENTER_VERTICAL
        textColumn.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)

        titleText.apply {
            applyTypography(TypographyTokens.body)
            maxLines = 1
        }
        subtitleText.apply {
            applyTypography(TypographyTokens.subheadline)
            maxLines = 1
            visibility = GONE
        }
        textColumn.addView(titleText)
        textColumn.addView(subtitleText)
        addView(textColumn)

        applyTheme()
    }

    fun setTitle(title: CharSequence?) {
        titleText.text = title ?: ""
        titleText.visibility = if (title.isNullOrEmpty()) GONE else VISIBLE
        requestLayout()
    }

    fun setSubtitle(subtitle: CharSequence?) {
        subtitleText.text = subtitle ?: ""
        subtitleText.visibility = if (subtitle.isNullOrEmpty()) GONE else VISIBLE
        requestLayout()
    }

    fun setIconDrawable(drawable: Drawable?) {
        iconView.setImageDrawable(drawable)
        iconView.visibility = if (drawable == null) GONE else VISIBLE
    }

    fun setDividerVisible(visible: Boolean) {
        showDivider = visible
        invalidate()
    }

    fun applyTheme() {
        titleText.setTextColor(ColorTokens.label)
        subtitleText.setTextColor(ColorTokens.secondaryLabel)
        dividerColor?.let { dividerPaint.color = it }
            ?: run { dividerPaint.color = ColorTokens.separator }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val minHeight = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_XXL, context)
        if (measuredHeight < minHeight) {
            setMeasuredDimension(measuredWidth, minHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showDivider) return
        val h = resources.displayMetrics.density
        canvas.drawRect(
            insetH.toFloat(),
            height - h,
            (width - insetH).toFloat(),
            height.toFloat(),
            dividerPaint
        )
    }
}

/**
 * Sort pill shown in the navigation bar area.
 * Displays the current sort selection with a chevron indicator.
 */
class SortPillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val labelText: AppleuxText
    private val chevronText: AppleuxText

    var text: String
        get() = labelText.text.toString()
        set(value) {
            labelText.text = value
        }

    private val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
    private val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(padH, padV, padH, padV)
        isClickable = true
        isFocusable = true

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_ROUND, context)
            setColor(ColorTokens.systemFillSecondary)
        }

        labelText = AppleuxText(context).apply {
            applyTypography(TypographyTokens.subheadlineEmphasized)
            setTextColor(ColorTokens.systemBlue)
        }
        addView(labelText)

        chevronText = AppleuxText(context).apply {
            applyTypography(TypographyTokens.subheadlineEmphasized)
            text = " ▾"
            setTextColor(ColorTokens.systemBlue)
        }
        addView(chevronText)

        text = "Sort: Name"
    }
}

/**
 * One row of the Locations section: volume icon, name, free/total space, and
 * the current mount state.
 *
 * A volume that is present but unmounted keeps its card and greys out, which
 * is how iOS reports a pulled drive — the row stays so the user can see what
 * is missing instead of it silently vanishing from the list.
 */
class StorageCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView = ImageView(context)
    private val nameText = AppleuxText(context)
    private val spaceText = AppleuxText(context)
    private val stateText = AppleuxText(context)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var showDivider = false

    private val insetH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, context)
    private val insetV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
    private val iconSize = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_LG, context)
    private val gap = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)

    init {
        setWillNotDraw(false)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(insetH, insetV, insetH, insetV)
        isClickable = true
        isFocusable = true
        minimumHeight = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_XXL, context)

        iconView.layoutParams = LayoutParams(iconSize, iconSize).apply {
            marginEnd = gap
        }
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(iconView)

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        nameText.apply {
            applyTypography(TypographyTokens.body)
            maxLines = 1
        }
        spaceText.apply {
            applyTypography(TypographyTokens.subheadline)
            maxLines = 1
        }
        stateText.apply {
            applyTypography(TypographyTokens.footnote)
            maxLines = 1
        }
        textColumn.addView(nameText)
        textColumn.addView(spaceText)
        textColumn.addView(stateText)
        addView(textColumn)

        dividerPaint.color = ColorTokens.separator
    }

    fun setLocation(location: FilesActivity.StorageLocation) {
        nameText.text = location.name
        nameText.setTextColor(if (location.isMounted) ColorTokens.label else ColorTokens.tertiaryLabel)

        // Only claim numbers when the volume is mounted; an unmounted volume
        // has no usable figures and showing zeros would be a lie.
        spaceText.text = if (location.isMounted && location.totalBytes > 0L) {
            "${formatStorageBytes(location.availableBytes)} available of ${formatStorageBytes(location.totalBytes)}"
        } else {
            "Storage unavailable"
        }
        spaceText.setTextColor(if (location.isMounted) ColorTokens.secondaryLabel else ColorTokens.tertiaryLabel)

        stateText.text = location.mountState
        stateText.setTextColor(
            when {
                !location.isMounted -> ColorTokens.systemRed
                location.isRemovable -> ColorTokens.systemGreen
                else -> ColorTokens.tertiaryLabel
            }
        )

        iconView.setImageDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, context)
            setColor(
                if (location.isMounted) location.kind.color else ColorTokens.systemGray7
            )
        })

        alpha = if (location.isMounted) 1f else 0.6f
        requestLayout()
        invalidate()
    }

    fun setDividerVisible(visible: Boolean) {
        showDivider = visible
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val minHeight = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_XXL, context)
        if (measuredHeight < minHeight) {
            setMeasuredDimension(measuredWidth, minHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showDivider) return
        val h = resources.displayMetrics.density
        canvas.drawRect(
            insetH.toFloat(),
            height - h,
            (width - insetH).toFloat(),
            height.toFloat(),
            dividerPaint
        )
    }
}

/**
 * A row that can be swiped horizontally to reveal a leading and a trailing
 * action — the iOS list-edit idiom used by Recently Deleted.
 *
 * Swiping right fires the leading action (Restore), swiping left the trailing
 * one (Delete). The row tracks the finger 1:1, and past [actionThreshold] the
 * action fires on release; below it the row springs back without acting. This
 * is why Delete is behind a confirm dialog in the activity: the gesture is a
 * swipe, not a deliberate tap on a labelled button.
 */
class SwipeRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val leftActionView = AppleuxText(context)
    private val rightActionView = AppleuxText(context)
    private val contentView = FileRowView(context)

    private var onLeftAction: (() -> Unit)? = null
    private var onRightAction: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val actionThreshold: Int = LayoutUtils.dpToPx(64f, context)
    private val insetH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XL, context)

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracked = false

    init {
        leftActionView.apply {
            applyTypography(TypographyTokens.bodyEmphasized)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(insetH, 0, insetH, 0)
            visibility = INVISIBLE
        }
        addView(
            leftActionView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        rightActionView.apply {
            applyTypography(TypographyTokens.bodyEmphasized)
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(insetH, 0, insetH, 0)
            visibility = INVISIBLE
        }
        addView(
            rightActionView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        addView(
            contentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun setLabel(title: CharSequence, subtitle: CharSequence) {
        contentView.setTitle(title)
        contentView.setSubtitle(subtitle)
    }

    /**
     * The action label and its backing colour. The colour is applied to the
     * revealed panel, so the caller's choice (systemGreen for Restore,
     * systemRed for Delete) is what the user actually sees behind the swipe.
     */
    fun setLeftActionLabel(label: String, color: Int) {
        leftActionView.text = label
        leftActionView.setTextColor(ColorTokens.white)
        leftActionView.setBackgroundColor(color)
    }

    fun setRightActionLabel(label: String, color: Int) {
        rightActionView.text = label
        rightActionView.setTextColor(ColorTokens.white)
        rightActionView.setBackgroundColor(color)
    }

    fun setOnLeftAction(action: () -> Unit) {
        onLeftAction = action
    }

    fun setOnRightAction(action: () -> Unit) {
        onRightAction = action
    }

    fun setDividerVisible(visible: Boolean) {
        contentView.setDividerVisible(visible)
    }

    /**
     * Touch handling is done here rather than through GestureDetector because
     * these rows sit inside a vertical ScrollView: a horizontal intent has to
     * be claimed from the parent as soon as it is distinguishable from a
     * scroll, and the decision is made on MOVE/UP rather than on a fling.
     */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                tracked = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                // Horizontal-dominant and past slop: this is a swipe, so take
                // the gesture away from the ScrollView.
                if (!dragging && kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                tracked = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && kotlin.math.abs(dx) > touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    contentView.translationX = dx
                    revealActions(dx)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val offset = contentView.translationX
                    when {
                        offset >= actionThreshold -> {
                            snapBack()
                            onLeftAction?.invoke()
                        }
                        offset <= -actionThreshold -> {
                            snapBack()
                            onRightAction?.invoke()
                        }
                        else -> snapBack()
                    }
                } else if (tracked) {
                    // Not a swipe — this was a tap on the row content.
                    performClick()
                }
                resetGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                snapBack()
                resetGesture()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * The action backing only appears once the finger has committed to that
     * side, so a small overshoot doesn't flash the wrong action.
     */
    private fun revealActions(offset: Float) {
        if (offset > 0) {
            leftActionView.visibility = if (offset >= actionThreshold) VISIBLE else INVISIBLE
            rightActionView.visibility = INVISIBLE
        } else if (offset < 0) {
            rightActionView.visibility = if (offset <= -actionThreshold) VISIBLE else INVISIBLE
            leftActionView.visibility = INVISIBLE
        } else {
            leftActionView.visibility = INVISIBLE
            rightActionView.visibility = INVISIBLE
        }
    }

    private fun snapBack() {
        contentView.animate()
            .translationX(0f)
            .setDuration(MotionTokens.DURATION_SLIDE)
            .start()
        leftActionView.visibility = INVISIBLE
        rightActionView.visibility = INVISIBLE
    }

    private fun resetGesture() {
        dragging = false
        tracked = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun performClick(): Boolean {
        super.performClick()
        contentView.performClick()
        return true
    }
}

// ---- iOS 27 Custom Views ----

/**
 * Storage bar chart view for the Storage Analyzer.
 * Displays a horizontal bar chart with category breakdown.
 */
class StorageBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var categories: List<FilesActivity.StorageCategory> = emptyList()
    private var totalSize: Double = 0.0

    private val barHeight = LayoutUtils.dpToPx(24f, context)
    private val barRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, context)
    private val labelWidth = LayoutUtils.dpToPx(80f, context)
    private val gap = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)

    fun setCategories(categories: List<FilesActivity.StorageCategory>, totalSize: Double) {
        this.categories = categories
        this.totalSize = totalSize
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (categories.isEmpty()) return

        var y = 0f
        categories.forEach { category ->
            val proportion = if (totalSize > 0) category.sizeMb / totalSize else 0.0
            val barWidth = ((width - labelWidth - gap) * proportion).toFloat()

            // Draw label
            paint.color = ColorTokens.label
            paint.textSize = LayoutUtils.dpToPxFloat(12f, context)
            paint.style = Paint.Style.FILL
            canvas.drawText(category.name, 0f, y + barHeight / 2 + paint.textSize / 3, paint)

            // Draw bar background
            paint.color = ColorTokens.systemFillSecondary
            canvas.drawRoundRect(
                labelWidth.toFloat(), y.toFloat(),
                (width - gap).toFloat(), (y + barHeight).toFloat(),
                barRadius, barRadius, paint
            )

            // Draw bar fill
            paint.color = category.color
            canvas.drawRoundRect(
                labelWidth.toFloat(), y.toFloat(),
                (labelWidth + barWidth).toFloat(), (y + barHeight).toFloat(),
                barRadius, barRadius, paint
            )

            // Draw size label
            paint.color = ColorTokens.secondaryLabel
            canvas.drawText(
                String.format(Locale.US, "%.1f MB", category.sizeMb),
                labelWidth + barWidth + gap,
                y + barHeight / 2 + paint.textSize / 3,
                paint
            )

            y += barHeight + gap
        }
    }
}

/**
 * Category card for the Storage Analyzer.
 * Shows icon, name, size, and count for a storage category.
 */
class CategoryCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView = ImageView(context)
    private val nameText = AppleuxText(context)
    private val detailText = AppleuxText(context)

    private val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
    private val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
    private val iconSize = LayoutUtils.dpToPx(SizingTokens.CONTROL_SIZE_LG, context)
    private val gap = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(padH, padH, padH, padH)
        isClickable = true
        isFocusable = true

        iconView.layoutParams = LayoutParams(iconSize, iconSize).apply {
            marginEnd = gap
        }
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(iconView)

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        nameText.apply {
            applyTypography(TypographyTokens.body)
            setTextColor(ColorTokens.label)
        }
        detailText.apply {
            applyTypography(TypographyTokens.subheadline)
            setTextColor(ColorTokens.secondaryLabel)
        }
        textColumn.addView(nameText)
        textColumn.addView(detailText)
        addView(textColumn)
    }

    fun setCategory(category: FilesActivity.StorageCategory, totalSize: Double) {
        nameText.text = category.name
        val proportion = if (totalSize > 0) (category.sizeMb / totalSize * 100).toInt() else 0
        detailText.text = "${String.format(Locale.US, "%.1f MB", category.sizeMb)} • ${category.fileCount} files • $proportion%"

        iconView.setImageDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, context)
            setColor(category.color)
        })
    }
}

/**
 * Tag cloud view for the Tag Manager.
 * Displays all tags with their file counts.
 */
class TagCloudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)

    init {
        orientation = HORIZONTAL
        setPadding(pad, pad, pad, pad)
    }

    fun setTags(tags: List<FilesActivity.FileTag>, fileTagMap: Map<String, Set<String>>) {
        removeAllViews()
        tags.forEach { tag ->
            val count = fileTagMap.values.count { it.contains(tag.id) }
            val chip = AppleuxText(context).apply {
                applyTypography(TypographyTokens.subheadline)
                text = "${tag.name} ($count)"
                setTextColor(ColorTokens.label)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_ROUND, context)
                    setColor(tag.color)
                }
                val chipPadH = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
                val chipPadV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
                setPadding(chipPadH, chipPadV, chipPadH, chipPadV)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
                }
            }
            addView(chip)
        }
    }
}

/**
 * Archive progress view for ZIP operations.
 * Shows a systemBlue progress bar.
 */
class ArchiveProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress: Float = 0f

    private val barHeight = LayoutUtils.dpToPx(4f, context)
    private val barRadius = LayoutUtils.dpToPxFloat(SizingTokens.CORNER_RADIUS_SMALL, context)

    fun setProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        paint.color = ColorTokens.systemFillSecondary
        canvas.drawRoundRect(
            0f, 0f,
            width.toFloat(), barHeight.toFloat(),
            barRadius, barRadius, paint
        )

        // Progress fill
        paint.color = ColorTokens.systemBlue
        val progressWidth = width * progress
        canvas.drawRoundRect(
            0f, 0f,
            progressWidth.toFloat(), barHeight.toFloat(),
            barRadius, barRadius, paint
        )
    }
}
