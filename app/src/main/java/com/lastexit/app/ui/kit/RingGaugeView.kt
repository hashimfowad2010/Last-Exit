package com.lastexit.app.ui.kit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Circular gauge: a gradient arc for a fraction (runway left, or share of the limit used) with a
 * big value and a caption in the middle, or an icon for small badges.
 */
class RingGaugeView(
    context: Context,
    private val sizeDp: Int,
    private val strokeDp: Float,
) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        isFakeBoldText = false
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.06f
    }
    private val oval = RectF()
    private val matrix = Matrix()
    private var fraction = 0f
    private var arcColors = intArrayOf(Color.WHITE, Color.WHITE)
    private var value = ""
    private var caption = ""
    private var icon: Drawable? = null
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(
        target: Float,
        arcColors: IntArray,
        trackColor: Int,
        value: String = "",
        caption: String = "",
        textColor: Int = Color.WHITE,
        icon: Drawable? = null,
        animate: Boolean = true,
    ) {
        this.arcColors = if (arcColors.size == 1) intArrayOf(arcColors[0], arcColors[0]) else arcColors
        this.value = value
        this.caption = caption
        this.icon = icon
        track.color = trackColor
        valuePaint.color = textColor
        captionPaint.color = textColor.withAlpha(0.82f)
        val goal = target.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            fraction = goal
            invalidate()
            return
        }
        val from = fraction
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                fraction = from + (goal - from) * it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dpi(sizeDp)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = dp(strokeDp)
        val half = stroke / 2f
        oval.set(half, half, width - half, height - half)
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        canvas.drawOval(oval, track)
        if (fraction > 0f) {
            val cx = width / 2f
            val cy = height / 2f
            // The sweep starts at 3 o'clock; rotate so the gradient begins at 12 o'clock with the arc.
            val sweep = SweepGradient(cx, cy, arcColors, null)
            matrix.setRotate(-90f, cx, cy)
            sweep.setLocalMatrix(matrix)
            arc.shader = sweep
            canvas.drawArc(oval, -90f, 360f * fraction.coerceAtMost(0.999f), false, arc)
        }
        val drawable = icon
        if (drawable != null) {
            val s = (width * 0.46f).toInt()
            val l = (width - s) / 2
            val t = (height - s) / 2
            drawable.setBounds(l, t, l + s, t + s)
            drawable.draw(canvas)
            return
        }
        valuePaint.textSize = height * (if (value.length > 3) 0.25f else 0.32f)
        captionPaint.textSize = height * 0.085f
        val gap = height * 0.03f
        val valueHeight = valuePaint.descent() - valuePaint.ascent()
        val captionHeight = if (caption.isEmpty()) 0f else captionPaint.descent() - captionPaint.ascent()
        val top = (height - valueHeight - captionHeight - gap) / 2f
        canvas.drawText(value, width / 2f, top - valuePaint.ascent(), valuePaint)
        if (caption.isNotEmpty()) {
            canvas.drawText(caption.uppercase(), width / 2f, top + valueHeight + gap - captionPaint.ascent(), captionPaint)
        }
    }
}
