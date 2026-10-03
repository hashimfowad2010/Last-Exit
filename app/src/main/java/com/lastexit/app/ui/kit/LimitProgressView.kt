package com.lastexit.app.ui.kit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.lastexit.core.Status

/**
 * Progress toward the limit with a "ghost" segment showing where the current pace ends up, and a
 * tick for the limit itself when the projection runs past it.
 */
class LimitProgressView(context: Context, private val palette: Palette) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var used = 0f
    private var projected = 0f
    private var color = palette.status(Status.SAFE).main
    private var gradient = palette.statusGradient(Status.SAFE)
    private var trackColor = palette.surfaceVariant
    private var tickColor = palette.onSurface

    /** Recolours the bar for use on a gradient header (white track and fill). */
    fun onGradient() {
        trackColor = android.graphics.Color.WHITE.withAlpha(0.22f)
        tickColor = android.graphics.Color.WHITE
    }

    /** Fixed colours instead of status colours (e.g. the demo month progress). */
    fun useColors(colors: IntArray) {
        fixed = colors
    }

    private var fixed: IntArray? = null
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Fractions of the limit (1.0 = exactly at the limit). */
    fun bind(usedFraction: Double, projectedFraction: Double, status: Status, animate: Boolean) {
        color = palette.status(status).main
        gradient = fixed ?: palette.statusGradient(status).let { intArrayOf(it[0], it[1]) }
        val targetUsed = usedFraction.toFloat().coerceIn(0f, 10f)
        val targetProjected = projectedFraction.toFloat().coerceIn(targetUsed, 10f)
        animator?.cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            used = targetUsed
            projected = targetProjected
            invalidate()
            return
        }
        val fromUsed = used
        val fromProjected = projected
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedFraction
                used = fromUsed + (targetUsed - fromUsed) * f
                projected = fromProjected + (targetProjected - fromProjected) * f
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dpi(18))
    }

    override fun onDraw(canvas: Canvas) {
        val barHeight = dp(10f)
        val top = (height - barHeight) / 2f
        val radius = barHeight / 2f
        // When the projection overshoots, the bar is scaled so the limit sits inside it.
        val scale = maxOf(1f, projected, used)
        fun x(fraction: Float) = width * (fraction / scale)

        paint.shader = null
        paint.color = trackColor
        rect.set(0f, top, width.toFloat(), top + barHeight)
        canvas.drawRoundRect(rect, radius, radius, paint)

        paint.color = (fixed?.last() ?: color).withAlpha(0.28f)
        rect.set(0f, top, x(projected), top + barHeight)
        canvas.drawRoundRect(rect, radius, radius, paint)

        val end = maxOf(x(used), if (used > 0f) barHeight else 0f)
        if (end > 0f) {
            paint.color = android.graphics.Color.BLACK // opaque: a shader inherits the paint's alpha
            paint.shader = LinearGradient(0f, 0f, maxOf(end, 1f), 0f, gradient, null, Shader.TileMode.CLAMP)
            rect.set(0f, top, end, top + barHeight)
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.shader = null
        }

        if (scale > 1f) {
            paint.color = tickColor
            val lx = x(1f)
            canvas.drawRoundRect(lx - dp(1.5f), 0f, lx + dp(1.5f), height.toFloat(), dp(1.5f), dp(1.5f), paint)
        }
    }
}
