package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.annotation.ColorInt
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens

class AppleuxButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppleuxSurface(context, attrs, defStyleAttr) {

    enum class Style {
        PRIMARY, SECONDARY, TERTIARY, DESTRUCTIVE, PLAIN
    }

    enum class Size {
        SMALL, MEDIUM, LARGE
    }

    private var style: Style = Style.PRIMARY
    private var buttonSize: Size = Size.MEDIUM
    private var buttonText: String = ""
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var textColor: Int = ColorTokens.white
    private var textPaddingStart: Float = SpacingTokens.INSET_XL
    private var textPaddingEnd: Float = SpacingTokens.INSET_XL

    private var animatedAlpha = 1f

    private val density = resources.displayMetrics.density

    init {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isAntiAlias = true
        applyStyle(style)
    }

    fun setText(text: String) {
        buttonText = text
        requestLayout()
        invalidate()
    }

    fun getText(): String = buttonText

    fun setTextColor(color: Int) {
        textColor = color
        invalidate()
    }

    fun setTextStyle(style: AppleuxText.TextStyle) {
        textPaint.typeface = AppleuxText.typefaceForWeight(style.fontWeight)
        invalidate()
    }

    /** Clears any explicit weight so the size's own HIG weight applies again. */
    fun clearTextStyle() {
        textPaint.typeface = null
        invalidate()
    }

    fun setButtonStyle(style: Style) {
        this.style = style
        applyStyle(style)
        invalidate()
    }

    private fun applyStyle(style: Style) {
        borderWidth = 0f
        when (style) {
            Style.PRIMARY -> {
                surfaceColor = ColorTokens.systemBlue
                textColor = ColorTokens.white
            }
            Style.SECONDARY -> {
                surfaceColor = ColorTokens.systemFillSecondary
                textColor = ColorTokens.textPrimary
            }
            // TERTIARY and PLAIN are the iOS "no chrome" buttons: tinted text,
            // no fill. The previous version painted a systemGray7 slab behind
            // them, which is a Material button, not an iOS one — and it is what
            // AppleuxDialog's action row was inheriting.
            Style.TERTIARY -> {
                surfaceColor = Color.TRANSPARENT
                textColor = ColorTokens.systemBlue
            }
            Style.PLAIN -> {
                surfaceColor = Color.TRANSPARENT
                textColor = ColorTokens.systemBlue
            }
            Style.DESTRUCTIVE -> {
                surfaceColor = ColorTokens.systemRed
                textColor = ColorTokens.white
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animatedAlpha = PRESSED_ALPHA
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animatedAlpha = 1f
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val saveCount = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(),
            (255 * animatedAlpha).toInt())
        super.onDraw(canvas)

        textPaint.color = textColor
        val fontSpec = when (buttonSize) {
            Size.SMALL -> TypographyTokens.caption1
            Size.MEDIUM -> TypographyTokens.bodyEmphasized
            Size.LARGE -> TypographyTokens.title3
        }
        textPaint.textSize = fontSpec.size * resources.displayMetrics.scaledDensity
        // Only seed a default the first time. The old code assigned
        // Typeface.DEFAULT on every frame, which silently discarded whatever
        // setTextStyle had set — so every weight looked identical.
        if (textPaint.typeface == null) {
            textPaint.typeface = AppleuxText.typefaceForWeight(fontSpec.fontWeight)
        }
        textPaint.letterSpacing = fontSpec.emTracking

        val textWidth = textPaint.measureText(buttonText)
        val textX = (width - textWidth) / 2f
        val textY = height / 2f + textPaint.textSize / 2f - textPaint.fontMetrics.descent / 2f

        canvas.drawText(buttonText, textX, textY, textPaint)

        canvas.restoreToCount(saveCount)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (textPaint.measureText(buttonText) +
            textPaddingStart * density + textPaddingEnd * density).toInt()
        val desiredHeight = LayoutUtils.dpToPx(
            when (buttonSize) {
                Size.SMALL -> SizingTokens.CONTROL_SIZE_LG
                Size.MEDIUM -> SizingTokens.CONTROL_SIZE_XL
                Size.LARGE -> SizingTokens.CONTROL_SIZE_XXL
            },
            context
        )

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
        updateOutline()
    }

    fun setSize(size: Size) {
        buttonSize = size
        requestLayout()
        invalidate()
    }

    private companion object {
        const val PRESSED_ALPHA = 0.7f
    }
}
