package com.lastexit.app.ui.kit

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import com.lastexit.core.Status

/**
 * Status pill: icon + text + colour, so status is never conveyed by colour alone. Urgent stages
 * pulse gently, and a worsening status pops and shakes once.
 */
class StatusChipView(
    context: Context,
    private val palette: Palette,
    /** Translucent white chip for use on gradient headers. */
    private val onGradient: Boolean = false,
) : View(context) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dp(13f) * context.resources.configuration.fontScale
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val haloInset = dp(5f)
    private val iconSize = dpi(16)
    private var icon: Drawable? = null
    private var label: String = ""
    private var bgColor = 0
    private var fgColor = 0
    private var haloFraction = 0f
    private var colorAnimator: ValueAnimator? = null
    private var pulse: ValueAnimator? = null

    var status: Status? = null
        private set

    fun bind(newStatus: Status, newLabel: String, spoken: String, animate: Boolean) {
        val previous = status
        status = newStatus
        label = newLabel
        contentDescription = "Status: $spoken"
        val colors = palette.status(newStatus)
        val filled = newStatus == Status.LAST_EXIT || newStatus == Status.PAST_THE_LINE
        val targetBg = when {
            onGradient -> android.graphics.Color.WHITE.withAlpha(0.24f)
            filled -> colors.main
            else -> colors.container
        }
        val targetFg = if (onGradient) android.graphics.Color.WHITE else if (filled) colors.onMain else colors.onContainer
        icon = context.icon(statusIcon(newStatus), targetFg)
        if (animate && previous != null && previous != newStatus && ValueAnimator.areAnimatorsEnabled()) {
            animateColors(targetBg, targetFg)
            if (newStatus.isWorseThan(previous)) popAndShake()
        } else {
            bgColor = targetBg
            fgColor = targetFg
        }
        updatePulse()
        requestLayout()
        invalidate()
    }

    private fun animateColors(targetBg: Int, targetFg: Int) {
        val fromBg = bgColor
        val fromFg = fgColor
        val evaluator = ArgbEvaluator()
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            addUpdateListener {
                val f = it.animatedFraction
                bgColor = evaluator.evaluate(f, fromBg, targetBg) as Int
                fgColor = evaluator.evaluate(f, fromFg, targetFg) as Int
                icon?.setTint(fgColor)
                invalidate()
            }
            start()
        }
    }

    private fun popAndShake() {
        animate().scaleX(1.18f).scaleY(1.18f).setDuration(140).setInterpolator(OvershootInterpolator())
            .withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(220).start() }
            .start()
        ObjectAnimator.ofFloat(this, "translationX", 0f, dp(7f), -dp(7f), dp(5f), -dp(5f), dp(2f), 0f).apply {
            duration = 480
            startDelay = 120
            start()
        }
    }

    private fun updatePulse() {
        val urgent = status == Status.LAST_EXIT || status == Status.PAST_THE_LINE
        if (urgent && isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()) {
            if (pulse == null) {
                pulse = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1400
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        haloFraction = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            pulse?.cancel()
            pulse = null
            haloFraction = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePulse()
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        pulse = null
        colorAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textWidth = textPaint.measureText(label)
        val width = haloInset * 2 + dp(10f) + iconSize + dp(6f) + textWidth + dp(12f)
        val height = haloInset * 2 + maxOf(dp(30f), textPaint.fontSpacing + dp(12f))
        setMeasuredDimension(
            resolveSize(width.toInt(), widthMeasureSpec),
            resolveSize(height.toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(haloInset, haloInset, width - haloInset, height - haloInset)
        val radius = rect.height() / 2f
        if (haloFraction > 0f) {
            // Expanding, fading ring: reads as a heartbeat without moving any text.
            val grow = haloInset * haloFraction
            haloPaint.color = (if (onGradient) fgColor else bgColor).withAlpha(0.55f * (1f - haloFraction))
            haloPaint.strokeWidth = dp(2f)
            canvas.drawRoundRect(
                rect.left - grow, rect.top - grow, rect.right + grow, rect.bottom + grow,
                radius + grow, radius + grow, haloPaint,
            )
        }
        bgPaint.color = bgColor
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        val iconLeft = (rect.left + dp(10f)).toInt()
        val iconTop = (rect.centerY() - iconSize / 2f).toInt()
        icon?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        icon?.draw(canvas)
        textPaint.color = fgColor
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, iconLeft + iconSize + dp(6f), baseline, textPaint)
    }
}
