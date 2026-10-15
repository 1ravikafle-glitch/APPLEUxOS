package com.appleux.design.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.SpacingTokens

/**
 * The notification shade.
 *
 * PROVENANCE — background translucency and card geometry follow Apple's own
 * iOS 27 guide screenshots in
 * `design-reference/ios27/official-guide/images/` (Control Center sheet
 * `7511c37e-*.png` for the frosted fill, lock-screen card `9f9c87b1-*.png`
 * for the card radius and insets).
 */
class AppleuxNotificationShade @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var isExpanded: Boolean = false

    private val recyclerView: RecyclerView

    private var notificationAdapter: NotificationAdapter? = null

    private val clearAllButton: AppleuxText = AppleuxText(context).apply {
        text = "Clear All"
        textSize = 14f
        setTextStyle(AppleuxText.TextStyle.MEDIUM)
        setTextColor(ColorTokens.systemBlue)
        gravity = Gravity.CENTER
        setPadding(0, SpacingTokens.PADDING_MEDIUM, 0, SpacingTokens.PADDING_MEDIUM)
        visibility = GONE
    }

    init {
        // The frost comes from FLAG_BLUR_BEHIND on the overlay window
        // (AppleuxSystemUIService); a RenderEffect here would only blur this
        // view's own fill and never what is behind it. The fill therefore has
        // to stay TRANSLUCENT — the previous opaque
        // ColorTokens.systemBackground painted straight over the blurred
        // wallpaper and left the window flag as dead weight. Same fill and
        // alpha source as AppleuxControlCenter.
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            // Anchored at the top, so the sheet is rounded at its BOTTOM edge.
            // (The Control Center rises from the bottom, hence rounded at top.)
            val r = LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP *
                resources.displayMetrics.density
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            setColor(
                if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK
                else LiquidGlassTokens.REGULAR_BG_LIGHT
            )
            this.alpha = AppleuxGlass.scrimAlphaForLevel(readGlassLevel())
        }
        clipToOutline = true

        // Header / list / Clear All must STACK VERTICALLY. As siblings of a
        // FrameLayout they overlapped and layoutParams.weight was inert
        // (weight only resolves inside a LinearLayout), which put the date
        // straight through the first notification.
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        addView(column)

        // The header paints no background of its own: on iOS it sits directly
        // on the frosted shade, so an opaque fill here would defeat the blur.
        column.addView(
            AppleuxNotificationHeader(context),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        column.addView(recyclerView)

        // Clear All is a footer, not a FrameLayout overlay, so it can never
        // sit on top of the last row.
        clearAllButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        column.addView(clearAllButton)
    }

    fun setNotifications(
        notifications: List<NotificationData>,
        onAction: ((NotificationData, NotificationAction) -> Unit)? = null,
        onTap: ((NotificationData) -> Unit)? = null
    ) {
        notificationAdapter = NotificationAdapter(notifications, onAction, onTap)
        recyclerView.adapter = notificationAdapter
        clearAllButton.visibility = if (notifications.isEmpty()) GONE else VISIBLE
    }

    fun setOnClearAllListener(listener: (() -> Unit)?) {
        clearAllButton.setOnClickListener { listener?.invoke() }
    }

    fun setOnDismissListener(listener: ((NotificationData) -> Unit)?) {
        dismissListener = listener
        if (listener != null && !swipeHelperAttached) {
            swipeHelperAttached = true
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            ) {
                override fun onMove(
                    v: RecyclerView,
                    h: RecyclerView.ViewHolder,
                    t: RecyclerView.ViewHolder
                ): Boolean = false

                /**
                 * Ongoing / FLAG_NO_CLEAR notifications are not swipeable, so
                 * the helper must refuse them outright. Letting the swipe run
                 * and ignoring it in [onSwiped] leaves the row stranded
                 * off-screen with nothing backing it up.
                 */
                override fun getMovementFlags(
                    r: RecyclerView,
                    h: RecyclerView.ViewHolder
                ): Int {
                    val adapter = r.adapter as? NotificationAdapter
                    val clearable = adapter?.isClearable(h.bindingAdapterPosition) ?: false
                    return if (clearable) {
                        makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                    } else {
                        makeMovementFlags(0, 0)
                    }
                }

                override fun onSwiped(h: RecyclerView.ViewHolder, dir: Int) {
                    val pos = h.bindingAdapterPosition
                    val item =
                        (recyclerView.adapter as? NotificationAdapter)?.itemAt(pos)
                    if (item != null) dismissListener?.invoke(item)
                }
            }).attachToRecyclerView(recyclerView)
        }
    }

    private var dismissListener: ((NotificationData) -> Unit)? = null
    private var swipeHelperAttached = false

    fun expand() {
        if (isExpanded) return
        isExpanded = true
        // Snap on-screen: slide animations depend on measured height, which is
        // zero for never-laid-out GONE views (a whole class of invisible-shade
        // bugs). Motion returns once show/hide is rock solid.
        visibility = View.VISIBLE
        translationY = 0f
        invalidate()
    }

    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        visibility = View.GONE
        translationY = 0f
    }

    fun updateDragProgress(progress: Float) {
        val targetY = -height * (1 - progress)
        y = targetY
        invalidate()
    }

    /** Shared glass intensity (iOS 27 transparency slider), same as Control Center. */
    private fun readGlassLevel(): Int = try {
        AppleuxRuntimeConfig.readInt(
            context.contentResolver,
            AppleuxRuntimeConfig.KEY_GLASS_INTENSITY,
            AppleuxGlass.DEFAULT_LEVEL
        ).coerceIn(AppleuxGlass.MIN_LEVEL, AppleuxGlass.MAX_LEVEL)
    } catch (e: Exception) {
        AppleuxGlass.DEFAULT_LEVEL
    }

    data class NotificationAction(
        val title: String,
        val intent: android.app.PendingIntent
    )

    data class NotificationData(
        val id: String,
        val title: String,
        val subtitle: String,
        val timestamp: String,
        val iconRes: Int? = null,
        /** Posting app icon, already tinted. Null renders no badge. */
        val icon: android.graphics.drawable.Drawable? = null,
        val isClearable: Boolean = true,
        val key: String? = null,
        val packageName: String? = null,
        val actions: List<NotificationAction> = emptyList(),
        val contentIntent: android.app.PendingIntent? = null
    )

    /**
     * Per-row grouping decision.
     *
     * @param label app name for the head of a run, null for a continuation.
     * @param stacked true when the preceding row is from the same package.
     */
    data class Grouping(val label: String?, val stacked: Boolean)

    class NotificationAdapter(
        private val items: List<NotificationData>,
        private val onAction: ((NotificationData, NotificationAction) -> Unit)? = null,
        private val onTap: ((NotificationData) -> Unit)? = null
    ) : RecyclerView.Adapter<NotificationViewHolder>() {

        /**
         * iOS groups consecutive notifications from one app into a stack under
         * a single app label. Precomputed per position so the flat list, and
         * therefore every adapter position, stays index-aligned with the
         * swipe helper.
         */
        private val grouping: List<Grouping> = buildGrouping(items)

        fun itemAt(position: Int): NotificationData? = items.getOrNull(position)

        fun isClearable(position: Int): Boolean =
            items.getOrNull(position)?.isClearable ?: false

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder =
            NotificationViewHolder(AppleuxNotificationItem(parent.context))

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, grouping[position], onAction) { data -> onTap?.invoke(data) }
        }

        override fun getItemCount() = items.size

        private companion object {
            fun buildGrouping(items: List<NotificationData>): List<Grouping> {
                val out = ArrayList<Grouping>(items.size)
                var previousPackage: String? = null
                for (item in items) {
                    val pkg = item.packageName
                    out.add(
                        when {
                            pkg.isNullOrEmpty() -> Grouping(null, false)
                            pkg == previousPackage -> Grouping(null, true)
                            else -> Grouping(appLabel(pkg), false)
                        }
                    )
                    previousPackage = pkg
                }
                return out
            }

            /** "com.example.Messenger" -> "Messenger". */
            fun appLabel(pkg: String): String {
                val last = pkg.substringAfterLast('.', pkg)
                if (last.isEmpty()) return pkg
                return last.replaceFirstChar { it.uppercaseChar() }
            }
        }
    }

    class NotificationViewHolder(val view: AppleuxNotificationItem) : RecyclerView.ViewHolder(view) {
        fun bind(
            data: NotificationData,
            grouping: Grouping,
            onAction: ((NotificationData, NotificationAction) -> Unit)?,
            onTap: ((NotificationData) -> Unit)? = null
        ) {
            view.setGroupDecoration(grouping.label, grouping.stacked)
            view.setTitle(data.title)
            view.setSubtitle(data.subtitle)
            view.setTimestamp(data.timestamp)
            data.iconRes?.let { view.setIcon(it) }
            view.setIconDrawable(data.icon)
            view.setActions(data.actions) { action -> onAction?.invoke(data, action) }
            view.setOnClickListener { onTap?.invoke(data) }
        }
    }
}