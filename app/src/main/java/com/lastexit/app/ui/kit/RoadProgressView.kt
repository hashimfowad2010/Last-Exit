package com.lastexit.app.ui.kit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * The demo month drawn as a road: the car is today, the green/amber/red sign is the last exit, the
 * ✕ is where the limit is hit at the current pace. Designed for a gradient (dark) background.
 */
class RoadProgressView(context: Context, private val palette: Palette) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lane = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE.withAlpha(0.55f)
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE.withAlpha(0.75f)
        textSize = dp(11f) * context.resources.configuration.fontScale
    }
    private val signText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(10f) * context.resources.configuration.fontScale
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = 0.08f
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private var total = 30
    private var dayShown = 1f
    private var exitDay: Int? = null
    private var hitDay: Int? = null
    private var stage = com.lastexit.core.Status.SAFE
    private var animator: ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(totalDays: Int, day: Int, exitDay: Int?, hitDay: Int?, status: com.lastexit.core.Status, animate: Boolean) {
        total = totalDays
        this.exitDay = exitDay
        this.hitDay = hitDay
        stage = status
        animator?.cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            dayShown = day.toFloat()
            invalidate()
            return
        }
        val from = dayShown
        animator = ValueAnimator.ofFloat(from, day.toFloat()).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                dayShown = it.animatedValue as Float
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dpi(78))
    }

    override fun onDraw(canvas: Canvas) {
        val pad = dp(12f)
        val roadTop = dp(28f)
        val roadH = dp(22f)
        val left = pad
        val right = width - pad
        fun x(day: Float) = left + (right - left) * (day / total)

        // Asphalt and the travelled part of the road.
        rect.set(left - dp(6f), roadTop, right + dp(6f), roadTop + roadH)
        paint.shader = null
        paint.color = Color.BLACK.withAlpha(0.22f)
        canvas.drawRoundRect(rect, roadH / 2f, roadH / 2f, paint)
        val travelled = x(dayShown)
        rect.set(left - dp(6f), roadTop, travelled, roadTop + roadH)
        paint.shader = LinearGradient(left, 0f, travelled.coerceAtLeast(left + 1f), 0f, Color.WHITE.withAlpha(0.05f), Color.WHITE.withAlpha(0.28f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, roadH / 2f, roadH / 2f, paint)
        paint.shader = null

        // Dashed centre line.
        lane.strokeWidth = dp(2f)
        lane.pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(7f)), 0f)
        canvas.drawLine(left, roadTop + roadH / 2f, right, roadTop + roadH / 2f, lane)

        hitDay?.let { drawHit(canvas, x(it.toFloat()), roadTop, roadH) }
        exitDay?.let { drawExitSign(canvas, x(it.toFloat()), roadTop) }
        drawFinish(canvas, right, roadTop, roadH)
        drawCar(canvas, travelled, roadTop + roadH / 2f)

        val baseline = roadTop + roadH + dp(18f)
        label.textAlign = Paint.Align.LEFT
        canvas.drawText("Day 1", left - dp(4f), baseline, label)
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText("Day $total", right + dp(4f), baseline, label)
    }

    private fun drawCar(canvas: Canvas, cx: Float, cy: Float) {
        paint.color = Color.WHITE.withAlpha(0.25f)
        canvas.drawCircle(cx, cy, dp(14f), paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, dp(8f), paint)
        paint.color = palette.statusGradient(stage)[1]
        canvas.drawCircle(cx, cy, dp(4f), paint)
    }

    /** A little highway sign on a pole: EXIT, coloured by urgency. */
    private fun drawExitSign(canvas: Canvas, cx: Float, roadTop: Float) {
        val sign = if (stage == com.lastexit.core.Status.ACT_SOON) com.lastexit.core.Status.ACT_SOON else com.lastexit.core.Status.LAST_EXIT
        val g = palette.statusGradient(sign)
        paint.color = Color.WHITE.withAlpha(0.7f)
        canvas.drawRect(cx - dp(1f), dp(18f), cx + dp(1f), roadTop + dp(4f), paint)
        val w = signText.measureText("EXIT") + dp(12f)
        rect.set(cx - w / 2f, dp(2f), cx + w / 2f, dp(20f))
        paint.shader = LinearGradient(rect.left, 0f, rect.right, 0f, g[0], g[1], Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, dp(5f), dp(5f), paint)
        paint.shader = null
        canvas.drawText("EXIT", cx, rect.centerY() - (signText.descent() + signText.ascent()) / 2f, signText)
    }

    private fun drawHit(canvas: Canvas, cx: Float, roadTop: Float, roadH: Float) {
        val cy = roadTop + roadH / 2f
        paint.color = palette.statusGradient(com.lastexit.core.Status.PAST_THE_LINE)[0]
        canvas.drawCircle(cx, cy, dp(8f), paint)
        lane.pathEffect = null
        lane.color = Color.WHITE
        lane.strokeWidth = dp(2f)
        val r = dp(3.5f)
        canvas.drawLine(cx - r, cy - r, cx + r, cy + r, lane)
        canvas.drawLine(cx - r, cy + r, cx + r, cy - r, lane)
        lane.color = Color.WHITE.withAlpha(0.55f)
    }

    private fun drawFinish(canvas: Canvas, x: Float, roadTop: Float, roadH: Float) {
        val cell = roadH / 4f
        for (row in 0 until 4) {
            for (col in 0 until 2) {
                paint.color = if ((row + col) % 2 == 0) Color.WHITE.withAlpha(0.85f) else Color.BLACK.withAlpha(0.35f)
                canvas.drawRect(x + col * cell - cell, roadTop + row * cell, x + (col + 1) * cell - cell, roadTop + (row + 1) * cell, paint)
            }
        }
    }
}
