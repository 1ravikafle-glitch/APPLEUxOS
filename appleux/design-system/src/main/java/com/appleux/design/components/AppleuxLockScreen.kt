package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.LockScreenTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The iOS 27 Lock Screen.
 *
 * Built against Apple's own guide screenshot — see [LockScreenTokens] for the
 * file and every measured ratio. Top to bottom: full-bleed wallpaper, a small
 * centred date, a very large Lightweight translucent clock spanning ~83% of
 * the screen width with its centre in the upper third, the caller's
 * notifications, and two circular glass controls (flashlight bottom-left,
 * camera bottom-right) inset into the bottom corners.
 *
 * ## SECURITY — read before wiring this up
 *
 * This view **cannot unlock anything and does not pretend to.**
 *
 *  - It performs no authentication, holds no credential and touches no
 *    `KeyguardManager`, `BiometricPrompt` or `DevicePolicyManager` API.
 *  - [setOnUnlockRequestListener] is a *request*, not a success callback. The
 *    listener is told "the user swiped up"; what happens next is entirely the
 *    caller's business. The view deliberately has no "unlocked" state to flip
 *    and never hides, fades or dismisses itself.
 *  - Nothing here may ever call `dismissKeyguard()`, report a successful
 *    credential check, or set `FLAG_DISMISS_KEYGUARD` on the caller's behalf.
 *    A shared design-system view that could do that would turn every surface
 *    in the ROM into a lock-screen bypass.
 *  - For the same reason there is no painted "Swipe up to unlock" caption: the
 *    gesture target is the wallpaper area, and a label promising an unlock we
 *    cannot perform is a lie.
 *
 * The flashlight and camera controls are equally inert — they invoke
 * callbacks and nothing else. No `CameraManager`, no `setTorchMode()`, no
 * camera intent. The caller owns the platform calls and their permissions.
 */
class AppleuxLockScreen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ---- Public API --------------------------------------------------------

    /** Invoked when the user swipes up. A request, never a success report. */
    fun interface OnUnlockRequestListener {
        fun onUnlockRequested()
    }

    /**
     * One row in the stack below the clock. A plain data holder on purpose:
     * the lock screen does not query `NotificationManager` and holds no
     * `StatusBarNotification` handles. The caller filters, groups and
     * redacts; the view only draws what it is given.
     */
    data class Notification(
        val title: String,
        val subtitle: String? = null,
        @DrawableRes val iconRes: Int = 0,
        val onClick: (() -> Unit)? = null,
        val onLongClick: (() -> Unit)? = null
    )

    // ---- State -------------------------------------------------------------

    private var onFlashlight: (() -> Unit)? = null
    private var onCamera: (() -> Unit)? = null
    private var onUnlockRequest: OnUnlockRequestListener? = null
    private var onUnlockUnavailable: (() -> Unit)? = null
    private val notifications = mutableListOf<Notification>()

    // ---- Views -------------------------------------------------------------

    private val wallpaper = WallpaperView(context)
    private val column = LinearLayout(context)
    private val topSpacer = View(context)
    private val dateText = AppleuxText(context)
    private val clockText = AppleuxText(context)
    private val notificationStack = LinearLayout(context)
    private val buttonRow = FrameLayout(context)
    private val flashlightButton = GlassButton(context)
    private val cameraButton = GlassButton(context)

    private val density = resources.displayMetrics.density
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    /** No seconds, no AM/PM — exactly as in Apple's guide screenshot ("9:41"). */
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())

    /** "Wed Apr 1": no comma, matching Apple's guide screenshot. */
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault())

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var trackingSwipe = false
    private var appliedTopInset = -1
    private var appliedBottomInset = -1

    init {
        // Wallpaper is the bottom layer and everything above it is translucent
        // over it. The previous version called setBackgroundColor(systemBackground),
        // which is exactly what flattened the screen to an opaque grey panel.
        addView(wallpaper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(buttonRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        column.orientation = LinearLayout.VERTICAL

        // Weighted spacer pushes the date+clock block so its centre lands at
        // CLOCK_CENTER_Y_FRACTION of the screen. Using a weight rather than a
        // fixed dp is what keeps it in the upper third on both a 393pt phone
        // and the POCO M5's taller, narrower panel.
        topSpacer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, LockScreenTokens.CLOCK_CENTER_Y_FRACTION
        )
        column.addView(topSpacer)

        dateText.apply {
            applyTypography(TypographyTokens.title3)
            gravity = Gravity.CENTER
            setTextColor(whiteAt(LockScreenTokens.DATE_FILL_ALPHA))
        }
        column.addView(dateText, lpWrap())

        clockText.apply {
            // Lightweight, not bold. That is the entire character of this
            // screen: thin white strokes with the wallpaper reading through
            // them. It needs a real Light face, which AppleuxText now provides.
            setTextStyle(AppleuxText.TextStyle.LIGHT)
            setTextColor(whiteAt(LockScreenTokens.CLOCK_FILL_ALPHA))
            gravity = Gravity.CENTER
            includeFontPadding = false
            setLineSpacing(0f, CLOCK_LINE_SPACING)
        }
        column.addView(clockText, lpWrap())

        column.addView(space(LockScreenTokens.NOTIFICATION_TOP_GAP_DP))

        notificationStack.orientation = LinearLayout.VERTICAL
        notificationStack.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f - LockScreenTokens.CLOCK_CENTER_Y_FRACTION
        )
        column.addView(notificationStack)

        // Make the row consume taps in the gap between the two buttons, so a tap there
        // is not mistaken for the start of an unlock swipe.
        buttonRow.isClickable = true
        buttonRow.setOnClickListener { }
        flashlightButton.setOnClickListener { onFlashlight?.invoke() }
        cameraButton.setOnClickListener { onCamera?.invoke() }
        buttonRow.addView(flashlightButton, buttonParams(Gravity.START))
        buttonRow.addView(cameraButton, buttonParams(Gravity.END))

        renderClock()
    }

    // ---- Wallpaper ---------------------------------------------------------

    /** Sets the full-bleed wallpaper, centre-cropped to fill the view. */
    fun setWallpaper(drawable: Drawable?) {
        wallpaper.source = drawable
        requestLayout()
        invalidate()
    }

    /** Convenience for callers holding a wallpaper resource id. */
    fun setWallpaper(@DrawableRes resId: Int) {
        setWallpaper(ContextCompat.getDrawable(context, resId))
    }

    // ---- Callbacks ---------------------------------------------------------

    /**
     * Caller supplies the real torch control. This view performs no
     * `CameraManager` call and requests no permission.
     */
    fun setOnFlashlightClickListener(listener: (() -> Unit)?) {
        onFlashlight = listener
        flashlightButton.isEnabled = listener != null
    }

    /**
     * Caller supplies the real camera launch. This view starts no activity and
     * requests no permission.
     */
    fun setOnCameraClickListener(listener: (() -> Unit)?) {
        onCamera = listener
        cameraButton.isEnabled = listener != null
    }

    /**
     * The user swiped up. The listener decides whether — and how — to
     * authenticate, and is responsible for the outcome. See the class-level
     * security note before calling this.
     */
    fun setOnUnlockRequestListener(listener: OnUnlockRequestListener?) {
        onUnlockRequest = listener
    }

    /** Notified when a swipe-up arrives with no listener attached. */
    fun setOnUnlockUnavailableListener(listener: (() -> Unit)?) {
        onUnlockUnavailable = listener
    }

    // ---- Notifications -----------------------------------------------------

    /** Replaces the stack below the clock. An empty list clears it. */
    fun setNotifications(items: List<Notification>) {
        notifications.clear()
        notifications.addAll(items)
        renderNotifications()
    }

    val notificationCount: Int get() = notifications.size

    fun clearNotifications() {
        notifications.clear()
        notificationStack.removeAllViews()
    }

    private fun renderNotifications() {
        notificationStack.removeAllViews()
        if (notifications.isEmpty()) {
            notificationStack.setPadding(0, 0, 0, 0)
            return
        }
        val sideInset = LayoutUtils.dpToPx(
            LockScreenTokens.BUTTON_INSET_DP - LockScreenTokens.BUTTON_DIAMETER_DP / 2f,
            context
        )
        // Clear the button row so the last notification is never hidden behind
        // the two glass controls.
        notificationStack.setPadding(
            sideInset,
            0,
            sideInset,
            LayoutUtils.dpToPx(LockScreenTokens.BUTTON_DIAMETER_DP, context)
        )
        for (item in notifications) {
            notificationStack.addView(
                NotificationCard(context).apply { bind(item) },
                lpWrap()
            )
        }
    }

    // ---- Clock -------------------------------------------------------------

    /**
     * Repaints date + clock, then reschedules for the next minute boundary.
     *
     * The previous implementation ran a 1 s `CountDownTimer` for the whole
     * life of the view to display a value that changes once a minute: 60
     * main-thread wakeups per minute on a device budgeted for 3 GB. This
     * sleeps until the value actually changes and recomputes the delay after
     * every tick, so it stays correct across suspend, clock changes and DST.
     */
    private fun renderClock() {
        val now = LocalTime.now()
        clockText.text = now.format(timeFormatter)
        dateText.text = LocalDate.now().format(dateFormatter)

        // Sleep until the wall clock crosses the next minute boundary. Done
        // with millisecond arithmetic rather than LocalTime.plusMinutes so it
        // stays correct if the user changes the time zone or the clock while
        // this view is alive.
        val nowMillis = System.currentTimeMillis()
        val delay = (MILLIS_PER_MINUTE - (nowMillis % MILLIS_PER_MINUTE))
            .coerceIn(MIN_CLOCK_TICK_MS, MAX_CLOCK_TICK_MS)

        val runnable = Runnable { renderClock() }
        clockRunnable?.let { handler.removeCallbacks(it) }
        clockRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    /**
     * Scales the clock so the rendered string occupies
     * [LockScreenTokens.CLOCK_WIDTH_FRACTION] of the available width, capped so
     * it can never exceed [LockScreenTokens.CLOCK_MAX_HEIGHT_FRACTION] of the
     * view height.
     *
     * Auto-fitting rather than fixing a point size is what makes this correct
     * across the device range: "10:08" is far narrower than "12:47", and a
     * fixed size would leave one of them short of the reference.
     */
    private fun autoSizeClock() {
        val text = clockText.text?.toString().orEmpty()
        if (text.isEmpty() || width == 0) return

        val targetPx = width * LockScreenTokens.CLOCK_WIDTH_FRACTION
        clockPaint.typeface = clockText.typeface
        clockPaint.textSize = clockText.textSize

        val measured = clockPaint.measureText(text)
        if (measured <= 0f) return

        var size = clockText.textSize * (targetPx / measured)

        val bounds = Rect()
        clockPaint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.height() > 0f) {
            val maxByHeight = height * LockScreenTokens.CLOCK_MAX_HEIGHT_FRACTION
            size = minOf(size, clockText.textSize * (maxByHeight / bounds.height()))
        }

        // TextView.setTextSize always calls requestLayout(), so assigning an
        // unchanged value would re-enter measure -> autoSizeClock -> requestLayout
        // forever. The epsilon is below a quarter pixel, which is far under any
        // visible difference.
        if (kotlin.math.abs(size - clockText.textSize) > 0.25f) {
            clockText.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
    }

    // ---- Insets ------------------------------------------------------------

    private fun applyInsets() {
        // rootWindowInsets (API 23) rather than View.getInsets (API 30): it is
        // the one available from onApplyWindowInsets on every supported level.
        val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootWindowInsets?.getInsets(WindowInsets.Type.systemBars())
        } else {
            null
        }
        val top = bars?.top ?: 0
        val bottom = bars?.bottom ?: 0

        // applyInsets() runs from onLayout and requests layout, so an unguarded
        // version would loop: layout -> applyInsets -> requestLayout -> layout.
        // Bail when nothing actually changed.
        if (top == appliedTopInset && bottom == appliedBottomInset) return
        appliedTopInset = top
        appliedBottomInset = bottom

        topSpacer.layoutParams = (topSpacer.layoutParams as LinearLayout.LayoutParams).apply {
            height = top + LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
        }

        val button = LayoutUtils.dpToPx(LockScreenTokens.BUTTON_DIAMETER_DP, context)
        val bottomInset = bottom +
            LayoutUtils.dpToPx(LockScreenTokens.BUTTON_ROW_BOTTOM_CLEARANCE_DP, context)

        buttonRow.layoutParams = (buttonRow.layoutParams as LayoutParams).apply {
            height = button + bottomInset
        }
        val inset = LayoutUtils.dpToPx(LockScreenTokens.BUTTON_INSET_DP, context)
        flashlightButton.layoutParams = LayoutParams(
            button, button, Gravity.START or Gravity.BOTTOM
        ).apply {
            leftMargin = inset
            bottomMargin = bottomInset
        }
        cameraButton.layoutParams = LayoutParams(
            button, button, Gravity.END or Gravity.BOTTOM
        ).apply {
            rightMargin = inset
            bottomMargin = bottomInset
        }
        requestLayout()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val result = super.onApplyWindowInsets(insets)
        applyInsets()
        return result
    }

    // ---- Touch -------------------------------------------------------------

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = trackingSwipe

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.rawY
                trackingSwipe = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Consumed as soon as the drag is unambiguously upward, so the
                // gesture cannot fall through to a launcher or shade below.
                if (trackingSwipe && downY - event.rawY > touchSlop * UNLOCK_SWIPE_SLOP_MULTIPLE) {
                    trackingSwipe = false
                    performUnlockRequest()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackingSwipe = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun performUnlockRequest() {
        val listener = onUnlockRequest
        if (listener != null) listener.onUnlockRequested() else onUnlockUnavailable?.invoke()
        // Deliberately nothing else: this view has no unlocked state and does
        // not hide itself. See the class-level security note.
    }

    // ---- Lifecycle ---------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Re-read on every attach: the process may have been frozen for a long
        // time, so whatever tick last ran is stale by definition.
        renderClock()
    }

    override fun onDetachedFromWindow() {
        clockRunnable?.let { handler.removeCallbacks(it) }
        clockRunnable = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        autoSizeClock()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) applyInsets()
    }

    /**
     * The lock screen is always white-on-wallpaper; iOS has no light variant
     * here and neither does this. A caller drawing its own status bar over a
     * pale wallpaper should ask [isLightIconsNeeded] rather than read a token.
     */
    fun isLightIconsNeeded(wallpaperAverageRgb: Int): Boolean =
        Color.luminance(wallpaperAverageRgb.toLong()) < 0.5

    // ---- Internals ---------------------------------------------------------

    private fun lpWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun space(dp: Float) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LayoutUtils.dpToPx(dp, context)
        )
    }

    private fun buttonParams(gravity: Int) = LayoutParams(
        LayoutUtils.dpToPx(LockScreenTokens.BUTTON_DIAMETER_DP, context),
        LayoutUtils.dpToPx(LockScreenTokens.BUTTON_DIAMETER_DP, context),
        gravity
    )

    private fun whiteAt(alpha: Int): Int =
        (Color.WHITE and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    // ---- Wallpaper layer ---------------------------------------------------

    /** Draws the wallpaper centre-cropped so any aspect ratio fills the view. */
    private class WallpaperView(context: Context) : View(context) {
        var source: Drawable? = null
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            val d = source ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val sw = d.intrinsicWidth.toFloat()
            val sh = d.intrinsicHeight.toFloat()
            if (sw <= 0f || sh <= 0f) return

            // cover: scale so the smaller axis fills, then crop the overflow.
            val scale = maxOf(w / sw, h / sh)
            val dw = sw * scale
            val dh = sh * scale
            val save = canvas.save()
            canvas.translate((w - dw) / 2f, (h - dh) / 2f)
            d.setBounds(0, 0, dw.toInt(), dh.toInt())
            d.draw(canvas)
            canvas.restoreToCount(save)
        }
    }

    // ---- The circular glass control ---------------------------------------

    /**
     * One of the two bottom-corner buttons: Liquid Glass *Clear* over the
     * wallpaper, with the kit's rim ([LiquidGlassTokens]).
     *
     * A real View rather than a hand-rolled Canvas button so focus, ripple,
     * content description and touch targets come from the platform, which is
     * what an accessibility audit checks.
     */
    private inner class GlassButton(context: Context) : FrameLayout(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val iconView = ImageView(context)

        init {
            isClickable = true
            isFocusable = true
            addView(
                iconView,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            )
        }

        fun setIcon(@DrawableRes resId: Int) {
            iconView.setImageDrawable(ContextCompat.getDrawable(context, resId))
            // The SF drawables ship as black vectors; over a wallpaper the
            // glyph must be white, so tint rather than recolour fifty assets.
            iconView.setColorFilter(Color.WHITE)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f

            // Clear glass: the kit's near-black base at low alpha, so the
            // wallpaper reads through while the glyph keeps its contrast.
            fill.color = alphaOver(LiquidGlassTokens.CLEAR_BASE, CLEAR_FILL_ALPHA)
            canvas.drawCircle(cx, cy, r, fill)

            rim.color = alphaOver(LiquidGlassTokens.RIM_DARK, RIM_ALPHA)
            rim.strokeWidth = (LiquidGlassTokens.RIM_SPREAD_DP * 2f * density).coerceAtLeast(1f)
            canvas.drawCircle(cx, cy, r - rim.strokeWidth / 2f, rim)

            if (isPressed) {
                fill.color = PRESSED_WASH
                canvas.drawCircle(cx, cy, r, fill)
            }
        }
    }

    // ---- Notification card -------------------------------------------------

    /**
     * A single glass row under the clock. Shares the fill and rim language of
     * the buttons so the stack reads as one material, not three.
     */
    private inner class NotificationCard(context: Context) : FrameLayout(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val shape = Path()
        private val bounds = RectF()

        private val icon = ImageView(context)
        private val title = AppleuxText(context)
        private val subtitle = AppleuxText(context)

        private val padH = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
        private val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
        private val iconSize = LayoutUtils.dpToPx(LockScreenTokens.NOTIFICATION_ICON_DP, context)
        private val cornerRadius =
            LayoutUtils.dpToPxFloat(LockScreenTokens.NOTIFICATION_CORNER_RADIUS_DP, context)

        init {
            val text = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            icon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            icon.setColorFilter(Color.WHITE)
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            icon.visibility = GONE

            title.apply {
                applyTypography(TypographyTokens.subheadlineEmphasized)
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            subtitle.apply {
                applyTypography(TypographyTokens.subheadline)
                setTextColor(NOTIFICATION_SUBTITLE_COLOR)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                visibility = GONE
            }
            text.addView(title)
            text.addView(subtitle)

            addView(icon, LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = padH
            })
            addView(text, LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
                marginEnd = padH
            })

            setPadding(0, padV, 0, padV)
        }

        fun bind(item: Notification) {
            title.text = item.title
            val sub = item.subtitle
            subtitle.visibility = if (sub.isNullOrEmpty()) GONE else VISIBLE
            subtitle.text = sub.orEmpty()

            if (item.iconRes != 0) {
                icon.visibility = VISIBLE
                icon.setImageDrawable(ContextCompat.getDrawable(context, item.iconRes))
            } else {
                icon.visibility = GONE
            }

            val interactive = item.onClick != null || item.onLongClick != null
            isClickable = interactive
            setOnClickListener(if (item.onClick != null) View.OnClickListener { item.onClick() } else null)
            setOnLongClickListener(if (item.onLongClick != null) {
                View.OnLongClickListener { item.onLongClick(); true }
            } else {
                null
            })

            contentDescription = if (sub.isNullOrEmpty()) {
                item.title
            } else {
                "${item.title}. $sub"
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            shape.reset()
            shape.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)

            fill.color = alphaOver(LiquidGlassTokens.CLEAR_BASE, CARD_FILL_ALPHA)
            canvas.drawPath(shape, fill)

            rim.color = alphaOver(LiquidGlassTokens.RIM_DARK, RIM_ALPHA)
            rim.strokeWidth = (LiquidGlassTokens.RIM_SPREAD_DP * 2f * density).coerceAtLeast(1f)
            canvas.drawPath(shape, rim)
        }
    }

    private companion object {
        /**
         * AppleUX-chosen: how far past the touch slop a drag must travel before
         * it counts as an unlock request. 4x slop is the platform convention
         * for separating a drag from a tap.
         */
        const val UNLOCK_SWIPE_SLOP_MULTIPLE = 4f

        /** AppleUX-chosen fill alphas for Clear glass over wallpaper. */
        const val CLEAR_FILL_ALPHA = 0x40
        const val CARD_FILL_ALPHA = 0x59
        const val RIM_ALPHA = 0x80
        const val PRESSED_WASH = 0x26FFFFFF
        const val NOTIFICATION_SUBTITLE_COLOR = 0xB3FFFFFF.toInt()

        /** Clock line spacing: AppleUX-chosen, tightened so the glyphs group. */
        const val CLOCK_LINE_SPACING = 0.9f

        /**
         * Tick bounds. The 250ms floor absorbs a clock that jumps; the 60s
         * ceiling is a backstop against a clock set far in the future.
         */
        const val MIN_CLOCK_TICK_MS = 250L
        const val MAX_CLOCK_TICK_MS = 60_000L
        const val MILLIS_PER_MINUTE = 60_000L

        fun alphaOver(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }
}