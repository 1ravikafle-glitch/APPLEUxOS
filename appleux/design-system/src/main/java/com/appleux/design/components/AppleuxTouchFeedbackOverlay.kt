package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SizingTokens

class AppleuxTouchFeedbackOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rippleX = 0f
    private var rippleY = 0f
    private var rippleRadius = 0f
    private var rippleAlpha = 0f
    private var isPressed = false
    private var isAnimating = false
    private var animationStartTime = 0L
    private var animationDuration = 200L
    private var rippleColor = ColorTokens.white48
    private val animatorLock = Any()

    private val fadeRunnable = object : Runnable {
        override fun run() {
            invalidate()
            if (rippleAlpha > 0f) {
                postDelayed(this, 16L)
            } else {
                isAnimating = false
            }
        }
    }

    init {
        isClickable = true
        isFocusable = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                rippleX = event.x
                rippleY = event.y
                rippleRadius = 0f
                rippleAlpha = 0.25f
                isAnimating = true
                animationStartTime = System.currentTimeMillis()
                fadeInRipple()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - rippleX
                val dy = event.y - rippleY
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val reach = Math.sqrt(
                    (width * width + height * height).toDouble()
                ).toFloat() / 2f

                if (distance < reach) {
                    rippleX = event.x
                    rippleY = event.y
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                startRippleFadeOut()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun fadeInRipple() {
        postDelayed(fadeRunnable, 16L)
    }

    private fun startRippleFadeOut() {
        isAnimating = true
        animationStartTime = System.currentTimeMillis()
        postDelayed(object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - animationStartTime
                val progress = (elapsed.toFloat() / animationDuration).coerceAtMost(1f)
                rippleAlpha = 0.25f * (1f - progress)
                rippleRadius = width.toFloat() * progress
                if (progress < 1f) {
                    invalidate()
                    postDelayed(this, 16L)
                } else {
                    rippleAlpha = 0f
                    rippleRadius = 0f
                    isAnimating = false
                    invalidate()
                }
            }
        }, 16L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rippleAlpha > 0f && isPressed) {
            val radius = rippleRadius.coerceAtLeast(
                Math.min(width, height) / 2f
            )
            paint.color = adjustAlpha(rippleColor, rippleAlpha)
            canvas.drawCircle(rippleX, rippleY, radius, paint)
        }
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val a = (android.graphics.Color.alpha(color) * alpha).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    fun setRippleColor(color: Int) {
        rippleColor = color
    }

    fun attachToParent(parent: ViewGroup) {
        parent.addView(this, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        bringToFront()
    }

    companion object {
        fun attachTo(view: View, rippleColor: Int = ColorTokens.white48): AppleuxTouchFeedbackOverlay {
            val parent = view.parent as? ViewGroup ?: return AppleuxTouchFeedbackOverlay(view.context)
            val overlay = AppleuxTouchFeedbackOverlay(view.context)
            overlay.setRippleColor(rippleColor)
            parent.addView(overlay, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            overlay.bringToFront()
            return overlay
        }
    }
}
