package com.appleux.launcher.apps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.*
import android.hardware.camera2.params.RggbChannelVector
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.view.*
import android.widget.GridLayout
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.appleux.sounds.AppleuxSoundPlayer
import com.appleux.design.components.AppleuxGlass
import com.appleux.design.components.AppleuxHaptics
import com.appleux.design.components.AppleuxIconRegistry
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.design.theme.AppleuxTheme
import com.appleux.design.tokens.*
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import com.appleux.design.components.AppleuxToast

/**
 * iOS 27 Camera UI for AppleUX.
 *
 * Design: fullscreen camera preview with floating glass controls.
 * Mode selector (PHOTO, VIDEO, PORTRAIT, PANO) above shutter.
 * Shutter button: 72dp white ring + fill, centered bottom.
 * Thumbnail: bottom-left, 44dp rounded square.
 * Flash toggle: top-left, 44dp glass circle.
 * Camera flip: top-right, 44dp glass circle.
 * Bottom safe area: 34dp above home indicator.
 */
class CameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraActivity"
        private const val CAMERA_PERMISSION_REQUEST = 100

        // iOS camera bottom safe area — not in the standard spacing token set.
        private const val BOTTOM_SAFE_AREA_DP = 34f

        private val MODES = listOf("PHOTO", "VIDEO", "PORTRAIT", "PANO")
    }

    // Camera2 API
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    /**
     * The repeating capture request. Pro mode re-applies ISO / shutter /
     * focus / exposure / white-balance to this SAME builder and pushes it back
     * into the live session, rather than reopening the camera per slider move.
     * It was referenced by updateCaptureParams() and by the session callback
     * but never declared, so the whole pro path could not resolve.
     */
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private val cameraOpenCloseLock = Semaphore(1)
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // UI
    private lateinit var previewView: SurfaceView
    private lateinit var shutterButton: FrameLayout
    private lateinit var shutterRing: View
    private lateinit var shutterFill: View
    private lateinit var flashButton: ImageButton
    private lateinit var flipButton: ImageButton
    private lateinit var thumbnailView: ImageView
    private lateinit var modeSelector: HorizontalScrollView
    private lateinit var modeContainer: LinearLayout
    private val modeButtons = mutableListOf<TextView>()

    // Pro mode controls
    private lateinit var proModeButton: TextView
    private lateinit var proControlsPanel: FrameLayout
    private lateinit var isoSlider: SeekBar
    private lateinit var shutterSlider: SeekBar
    private lateinit var focusSlider: SeekBar
    private lateinit var exposureSlider: SeekBar
    private lateinit var temperatureSlider: SeekBar
    private lateinit var isoValueLabel: TextView
    private lateinit var shutterValueLabel: TextView
    private lateinit var focusValueLabel: TextView
    private lateinit var exposureValueLabel: TextView
    private lateinit var temperatureValueLabel: TextView
    private var isProMode = false
    private var activeProControl: String? = null

    // State
    private var isFlashOn = false
    private var isFrontCamera = false
    private var currentMode = "PHOTO"
    private var lastCapturedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUI()

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        } else {
            setupCamera()
        }
    }

    // -------------------------------------------------------------------------
    // UI Construction
    // -------------------------------------------------------------------------

    private fun buildUI() {
        val density = resources.displayMetrics.density

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ColorTokens.black)
        }

        // Camera preview — fullscreen, edge-to-edge
        previewView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(previewView)

        // Top controls
        root.addView(createFlashButton())
        root.addView(createFlipButton())

        // Mode selector
        root.addView(createModeSelector())

        // Bottom controls
        root.addView(createThumbnailButton())
        root.addView(createShutterButton())

        // Pro mode toggle + controls panel
        root.addView(createProModeButton())
        root.addView(createProControlsPanel())

        setContentView(root)
        applyChrome()
    }

    private fun applyChrome() {
        AppleuxTheme.applyToActivity(this)
        AppleuxRuntimeConfig.reload(this)
        AppleuxTheme.isDarkMode = AppleuxRuntimeConfig.isDarkModeEnabled
        AppleuxTheme.applySystemBars(this)
    }

    // -------------------------------------------------------------------------
    // Flash toggle (top-left, 44dp glass circle)
    // -------------------------------------------------------------------------

    private fun createFlashButton(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val margin = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(margin, (SpacingTokens.INSETS_XXXL4 * density).toInt(), 0, 0)
            }
        }

        flashButton = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (SizingTokens.CONTROL_SIZE_XL * density).toInt(),
                (SizingTokens.CONTROL_SIZE_XL * density).toInt()
            )
            background = createGlassCircleBackground(SizingTokens.CONTROL_SIZE_XL)
            setImageDrawable(getIcon("system_flashlight"))
            setColorFilter(ColorTokens.label)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (SpacingTokens.INSETS_MD * density).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = "Flash"
            AppleuxGlass.applyBlur(this, AppleuxGlass.DEFAULT_LEVEL)
            setOnClickListener { toggleFlash() }
        }

        container.addView(flashButton)
        return container
    }

    // -------------------------------------------------------------------------
    // Camera flip (top-right, 44dp glass circle)
    // -------------------------------------------------------------------------

    private fun createFlipButton(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                val margin = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(0, (SpacingTokens.INSETS_XXXL4 * density).toInt(), margin, 0)
            }
        }

        flipButton = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (SizingTokens.CONTROL_SIZE_XL * density).toInt(),
                (SizingTokens.CONTROL_SIZE_XL * density).toInt()
            )
            background = createGlassCircleBackground(SizingTokens.CONTROL_SIZE_XL)
            setImageDrawable(getIcon("system_camera"))
            setColorFilter(ColorTokens.label)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (SpacingTokens.INSETS_MD * density).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = "Flip Camera"
            AppleuxGlass.applyBlur(this, AppleuxGlass.DEFAULT_LEVEL)
            setOnClickListener { flipCamera() }
        }

        container.addView(flipButton)
        return container
    }

    // -------------------------------------------------------------------------
    // Mode selector (horizontal scroll, 13sp semibold)
    // -------------------------------------------------------------------------

    private fun createModeSelector(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                val bottomMargin = (BOTTOM_SAFE_AREA_DP + SizingTokens.CONTROL_SIZE_XXL +
                    SpacingTokens.INSETS_XXL) * density
                setMargins(0, 0, 0, bottomMargin.toInt())
            }
        }

        modeSelector = HorizontalScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        modeContainer = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        MODES.forEach { mode ->
            val button = createModeButton(mode)
            modeButtons.add(button)
            modeContainer.addView(button)
        }

        modeSelector.addView(modeContainer)
        container.addView(modeSelector)
        return container
    }

    private fun createModeButton(mode: String): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val hMargin = (SpacingTokens.INSETS_MD * density).toInt()
                setMargins(hMargin, 0, hMargin, 0)
            }
            text = mode
            textSize = TypographyTokens.footnoteEmphasized.size
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (mode == currentMode) ColorTokens.white else ColorTokens.white48)
            val vPad = (SpacingTokens.INSETS_MD * density).toInt()
            setPadding(0, vPad, 0, vPad)
            contentDescription = mode
            setOnClickListener { selectMode(mode) }
        }
    }

    private fun selectMode(mode: String) {
        currentMode = mode
        modeButtons.forEachIndexed { index, button ->
            val selected = MODES[index] == mode
            button.setTextColor(if (selected) ColorTokens.white else ColorTokens.white48)
        }
        AppleuxHaptics.getInstance(this).selectionChanged()
    }

    // -------------------------------------------------------------------------
    // Shutter button (72dp circle, white ring + fill, centered bottom)
    // -------------------------------------------------------------------------

    private fun createShutterButton(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                val bottomMargin = (BOTTOM_SAFE_AREA_DP * density).toInt()
                setMargins(0, 0, 0, bottomMargin)
            }
        }

        shutterButton = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (72f * density).toInt(),
                (72f * density).toInt()
            )
            contentDescription = "Shutter"
        }

        // Outer ring — white stroke
        shutterRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = createShutterRingBackground()
        }

        // Inner fill — white solid
        shutterFill = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (60f * density).toInt(),
                (60f * density).toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
            background = createShutterFillBackground()
        }

        shutterButton.addView(shutterRing)
        shutterButton.addView(shutterFill)

        shutterButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    animateShutterPress(true)
                    AppleuxHaptics.getInstance(this).lightImpact()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    animateShutterPress(false)
                    if (event.action == MotionEvent.ACTION_UP) {
                        // The shutter sound belongs to the capture, not the
                        // press: on ACTION_DOWN the photo has not happened yet.
                        AppleuxSoundPlayer.shutter(this)
                        capturePhoto()
                    }
                    true
                }
                else -> false
            }
        }

        container.addView(shutterButton)
        return container
    }

    private fun animateShutterPress(pressed: Boolean) {
        val density = resources.displayMetrics.density
        val targetSize = if (pressed) (52f * density).toInt() else (60f * density).toInt()
        val layoutParams = shutterFill.layoutParams as FrameLayout.LayoutParams
        layoutParams.width = targetSize
        layoutParams.height = targetSize
        shutterFill.layoutParams = layoutParams
    }

    private fun createShutterRingBackground(): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke((4f * density).toInt(), ColorTokens.white)
        }
    }

    private fun createShutterFillBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorTokens.white)
        }
    }

    // -------------------------------------------------------------------------
    // Pro mode toggle (circular button above shutter, "PRO" text)
    // -------------------------------------------------------------------------

    private fun createProModeButton(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                val bottomMargin = (BOTTOM_SAFE_AREA_DP + 72f + SpacingTokens.INSETS_MD) * density
                setMargins(0, 0, 0, bottomMargin.toInt())
            }
        }

        proModeButton = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (44f * density).toInt(),
                (44f * density).toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
            text = "PRO"
            textSize = TypographyTokens.footnoteEmphasized.size
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER
            background = createProModeButtonBackground(false)
            contentDescription = "Pro Mode"
            AppleuxGlass.applyBlur(this, AppleuxGlass.DEFAULT_LEVEL)
            setOnClickListener { toggleProMode() }
        }

        container.addView(proModeButton)
        return container
    }

    private fun createProModeButtonBackground(active: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (active) {
                setColor(ColorTokens.systemBlue)
            } else {
                setColor(if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT)
            }
            setStroke((1.5f * density).toInt(), if (active) ColorTokens.systemBlue else ColorTokens.separator)
        }
    }

    // -------------------------------------------------------------------------
    // Pro controls panel (slides up from bottom when Pro mode is active)
    // -------------------------------------------------------------------------

    private fun createProControlsPanel(): View {
        val density = resources.displayMetrics.density
        proControlsPanel = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(0, 0, 0, 0)
            }
            visibility = View.GONE
            alpha = 0f
        }

        // Glass background
        val glassBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = createProPanelBackground()
        }
        AppleuxGlass.applyBlur(glassBg, AppleuxGlass.DEFAULT_LEVEL)
        proControlsPanel.addView(glassBg)

        // Grid: 2 columns x 3 rows
        val grid = GridLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val margin = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(margin, margin, margin, margin)
            }
            columnCount = 2
            rowCount = 3
        }

        // Add 5 control cells (6th cell empty)
        grid.addView(createProControlCell("ISO", "ISO"))
        grid.addView(createProControlCell("SHUTTER", "⏱"))
        grid.addView(createProControlCell("FOCUS", "🔍"))
        grid.addView(createProControlCell("EXPOSURE", "☀️"))
        grid.addView(createProControlCell("TEMP", "🌡"))

        proControlsPanel.addView(grid)
        return proControlsPanel
    }

    private fun createProPanelBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = SizingTokens.CORNER_RADIUS_MEDIUM * resources.displayMetrics.density
            setColor(if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT)
        }
    }

    // -------------------------------------------------------------------------
    // Pro control cell (icon + label + slider + current value)
    // -------------------------------------------------------------------------

    private fun createProControlCell(label: String, icon: String): View {
        val density = resources.displayMetrics.density
        val cell = LinearLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = (60f * density).toInt()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(
                    (SpacingTokens.INSETS_SM * density).toInt(),
                    (SpacingTokens.INSETS_SM * density).toInt(),
                    (SpacingTokens.INSETS_SM * density).toInt(),
                    (SpacingTokens.INSETS_SM * density).toInt()
                )
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = createProControlCellBackground(false)
            AppleuxGlass.applyBlur(this, AppleuxGlass.DEFAULT_LEVEL)
        }

        // Icon + label row
        val headerRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "$icon $label"
            textSize = TypographyTokens.caption1Emphasized.size
            setTextColor(ColorTokens.label)
        }
        headerRow.addView(iconLabel)
        cell.addView(headerRow)

        // Slider
        val slider = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (SpacingTokens.INSETS_XS * density).toInt()
            }
            max = 100
            progress = 50
            contentDescription = label
        }
        cell.addView(slider)

        // Value label
        val valueLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            textSize = TypographyTokens.caption2.size
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
        }
        cell.addView(valueLabel)

        // Wire up based on control type
        when (label) {
            "ISO" -> {
                isoSlider = slider
                isoValueLabel = valueLabel
                slider.setOnSeekBarChangeListener(createProControlListener("ISO") { progress ->
                    val iso = 32 + (progress * (1600 - 32) / 100)
                    isoValueLabel.text = "$iso"
                })
            }
            "SHUTTER" -> {
                shutterSlider = slider
                shutterValueLabel = valueLabel
                slider.setOnSeekBarChangeListener(createProControlListener("SHUTTER") { progress ->
                    // 1/1000s to 1/30s (inverted: 0 = 1/1000, 100 = 1/30)
                    val shutterDenominator = 1000 - (progress * (1000 - 30) / 100)
                    shutterValueLabel.text = "1/$shutterDenominator"
                })
            }
            "FOCUS" -> {
                focusSlider = slider
                focusValueLabel = valueLabel
                slider.setOnSeekBarChangeListener(createProControlListener("FOCUS") { progress ->
                    val focus = progress / 100f
                    focusValueLabel.text = String.format("%.1f", focus)
                })
            }
            "EXPOSURE" -> {
                exposureSlider = slider
                exposureValueLabel = valueLabel
                slider.setOnSeekBarChangeListener(createProControlListener("EXPOSURE") { progress ->
                    val ev = -3f + (progress * 6f / 100f)
                    exposureValueLabel.text = String.format("%+.1f EV", ev)
                })
            }
            "TEMP" -> {
                temperatureSlider = slider
                temperatureValueLabel = valueLabel
                slider.setOnSeekBarChangeListener(createProControlListener("TEMP") { progress ->
                    val temp = 2200 + (progress * (10000 - 2200) / 100)
                    temperatureValueLabel.text = "${temp}K"
                })
            }
        }

        return cell
    }

    private fun createProControlCellBackground(active: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = SizingTokens.CORNER_RADIUS_MEDIUM * density
            if (active) {
                setColor(ColorTokens.systemBlue)
            } else {
                setColor(if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT)
            }
        }
    }

    private fun createProControlListener(controlId: String, onProgress: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    activeProControl = controlId
                    onProgress(progress)
                    updateCaptureParams()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                activeProControl = controlId
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                activeProControl = null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pro mode toggle
    // -------------------------------------------------------------------------

    private fun toggleProMode() {
        isProMode = !isProMode
        AppleuxHaptics.getInstance(this).lightImpact()

        if (isProMode) {
            proControlsPanel.visibility = View.VISIBLE
            proControlsPanel.animate()
                .alpha(1f)
                .setDuration(250)
                .start()
            proModeButton.background = createProModeButtonBackground(true)
            proModeButton.setTextColor(ColorTokens.white)
        } else {
            proControlsPanel.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    proControlsPanel.visibility = View.GONE
                }
                .start()
            proModeButton.background = createProModeButtonBackground(false)
            proModeButton.setTextColor(ColorTokens.label)
        }
    }

    // -------------------------------------------------------------------------
    // Apply pro control values to camera
    // -------------------------------------------------------------------------

    private fun updateCaptureParams() {
        val device = cameraDevice ?: return
        val builder = previewRequestBuilder ?: return

        try {
            // Apply ISO
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, 32 + (isoSlider.progress * (1600 - 32) / 100))

            // Apply shutter speed (exposure time in nanoseconds)
            val shutterDenominator = 1000 - (shutterSlider.progress * (1000 - 30) / 100)
            val exposureTimeNs = (1_000_000_000.0 / shutterDenominator).toLong()
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeNs)

            // Apply focus (0.0 = macro, 1.0 = infinity)
            val focusDistance = 1f - (focusSlider.progress / 100f)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)

            // Apply exposure compensation
            val ev = -3f + (exposureSlider.progress * 6f / 100f)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, (ev * 6).toInt()) // 1/3 EV steps

            // Apply temperature (color correction)
            val temp = 2200 + (temperatureSlider.progress * (10000 - 2200) / 100)
            val r = (1f + (temp - 6500) / 10000f).coerceIn(0.5f, 2f)
            val b = (1f - (temp - 6500) / 10000f).coerceIn(0.5f, 2f)
            // COLOR_CORRECTION_GAINS is a Key<RggbChannelVector> on API 33+
            // (javap on android-36 confirms it); it USED to be a Rational[4].
            // The constructor takes red, green, blue, evenGreen, so the green
            // pair is set from the same 1f here.
            builder.set(
                CaptureRequest.COLOR_CORRECTION_GAINS,
                RggbChannelVector(r, 1f, b, 1f)
            )

            // Re-apply to session
            captureSession?.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to apply pro control", e)
        }
    }

    // -------------------------------------------------------------------------
    // Thumbnail preview (bottom-left, 44dp rounded square, 8dp radius)
    // -------------------------------------------------------------------------

    private fun createThumbnailButton(): View {
        val density = resources.displayMetrics.density
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                val margin = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(margin, 0, 0, (BOTTOM_SAFE_AREA_DP * density).toInt())
            }
        }

        thumbnailView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                (SizingTokens.CONTROL_SIZE_XL * density).toInt(),
                (SizingTokens.CONTROL_SIZE_XL * density).toInt()
            )
            background = createThumbnailBackground()
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Last Photo"
            setOnClickListener { openLastPhoto() }
        }

        container.addView(thumbnailView)
        return container
    }

    private fun createThumbnailBackground(): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = SizingTokens.CORNER_RADIUS_SMALL * density
            setColor(ColorTokens.systemGray)
        }
    }

    // -------------------------------------------------------------------------
    // Glass circle background helper
    // -------------------------------------------------------------------------

    private fun createGlassCircleBackground(sizeDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (ColorTokens.darkMode) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT)
        }
    }

    // -------------------------------------------------------------------------
    // Icon helper
    // -------------------------------------------------------------------------

    private fun getIcon(name: String): android.graphics.drawable.Drawable? {
        return AppleuxIconRegistry.getDrawableRes(name)?.let {
            ContextCompat.getDrawable(this, it)
        }
    }

    // -------------------------------------------------------------------------
    // Camera2 API — Setup
    // -------------------------------------------------------------------------

    private fun setupCamera() {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            val id = getCameraId(isFrontCamera) ?: run {
                AppleuxToast.show(this, "Camera not available")
                finish()
                return
            }
            cameraId = id
            openCamera(id)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access error", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while opening camera", e)
        }
    }

    private fun getCameraId(front: Boolean): String? {
        return try {
            cameraManager.cameraIdList.find { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (front) facing == CameraCharacteristics.LENS_FACING_FRONT
                else facing == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: CameraAccessException) {
            null
        }
    }

    private fun openCamera(id: String) {
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    cameraDevice = camera
                    startPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                    finish()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied", e)
        }
    }

    // -------------------------------------------------------------------------
    // Camera2 API — Preview
    // -------------------------------------------------------------------------

    private fun startPreview() {
        val device = cameraDevice ?: return
        val surface = previewView.holder.surface

        try {
            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }

            device.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            previewRequestBuilder?.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            session.setRepeatingRequest(
                                previewRequestBuilder?.build()!!,
                                null,
                                backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Failed to start preview", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure capture session")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    // -------------------------------------------------------------------------
    // Camera2 API — Capture
    // -------------------------------------------------------------------------

    private fun capturePhoto() {
        val device = cameraDevice ?: return
        val session = captureSession ?: return

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(previewView.holder.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())
            }

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    // Photo captured — thumbnail will be updated via saved file
                    runOnUiThread {
                        AppleuxHaptics.getInstance(this@CameraActivity).mediumImpact()
                    }
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to capture photo", e)
        }
    }

    private fun getJpegOrientation(): Int {
        val rotation = windowManager.defaultDisplay.rotation
        val sensorOrientation = cameraManager.getCameraCharacteristics(cameraId!!)
            .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val surfaceRotation = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (sensorOrientation + surfaceRotation + 270) % 360
    }

    // -------------------------------------------------------------------------
    // Flash toggle
    // // -------------------------------------------------------------------------

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        try {
            cameraManager.setTorchMode(cameraId!!, isFlashOn)
            flashButton.setColorFilter(if (isFlashOn) ColorTokens.systemYellow else ColorTokens.label)
            AppleuxHaptics.getInstance(this).lightImpact()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to toggle flash", e)
            isFlashOn = !isFlashOn
        }
    }

    // -------------------------------------------------------------------------
    // Camera flip
    // -------------------------------------------------------------------------

    private fun flipCamera() {
        isFrontCamera = !isFrontCamera
        closeCamera()
        setupCamera()
        AppleuxHaptics.getInstance(this).lightImpact()
    }

    // -------------------------------------------------------------------------
    // Thumbnail / last photo
    // -------------------------------------------------------------------------

    private fun openLastPhoto() {
        lastCapturedFile?.let { file ->
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                thumbnailView.setImageBitmap(bitmap)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            setupCamera()
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while stopping background thread", e)
        }
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while closing camera", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    // -------------------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                setupCamera()
            } else {
                AppleuxToast.show(this, "Camera permission required")
                finish()
            }
        }
    }
}
