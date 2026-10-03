package com.lastexit.app.ui.kit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.lastexit.core.AmountFormat
import com.lastexit.core.DateText
import com.lastexit.core.ForecastSeries
import com.lastexit.core.Status
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The forecast chart, drawn directly on a Canvas: actual usage, projection at the current pace,
 * the pace needed to land on the limit, the limit line and a clearly marked LAST EXIT.
 */
class ForecastChartView(context: Context, private val palette: Palette) : View(context) {
    private var series: ForecastSeries? = null
    private var format: AmountFormat? = null
    private var startDate: LocalDate = LocalDate.now()
    private var today: LocalDate = LocalDate.now()
    private var reveal = 1f
    private var revealAnimator: ValueAnimator? = null

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f) * context.resources.configuration.fontScale
        color = palette.onSurfaceMuted
    }
    private val boldText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f) * context.resources.configuration.fontScale
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val path = Path()
    private val rect = RectF()
    private val dash = DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)
    private val dot = DashPathEffect(floatArrayOf(dp(2f), dp(4f)), 0f)

    private val padLeft = dp(46f)
    private val padRight = dp(14f)
    private val padTop = dp(34f)
    private val padBottom = dp(26f)

    fun bind(series: ForecastSeries, format: AmountFormat, startDate: LocalDate, today: LocalDate, animate: Boolean) {
        val first = this.series == null
        this.series = series
        this.format = format
        this.startDate = startDate
        this.today = today
        if (first && animate && ValueAnimator.areAnimatorsEnabled()) {
            revealAnimator?.cancel()
            revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 900
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    reveal = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        reveal = 1f
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dpi(250))
    }

    override fun onDraw(canvas: Canvas) {
        val s = series ?: return
        val fmt = format ?: return
        val plotW = width - padLeft - padRight
        val plotH = height - padTop - padBottom
        val rawMin = min(0.0, s.actual.minOrNull() ?: 0.0)
        val rawMax = max(s.yMax, rawMin + 1.0)
        val step = niceStep(rawMax - rawMin)
        val yMin = floor(rawMin / step) * step
        val yMax = ceil(rawMax / step) * step
        fun x(day: Double) = padLeft + (day / s.totalDays).toFloat() * plotW
        fun y(value: Double) = padTop + plotH - ((value - yMin) / (yMax - yMin)).toFloat() * plotH
        val statusColor = palette.status(s.status).main

        drawGrid(canvas, fmt, yMin, yMax, step, ::y)
        drawDangerZone(canvas, s, ::y)

        // Future area: a faint wash so "what already happened" and "what will happen" read differently.
        fill.color = palette.surfaceVariant.withAlpha(0.55f)
        rect.set(x(s.todayX.toDouble()), padTop, x(s.totalDays.toDouble()), padTop + plotH)
        canvas.drawRect(rect, fill)

        // Clip everything to the reveal progress for the entry animation.
        canvas.save()
        canvas.clipRect(0f, 0f, padLeft + plotW * reveal + dp(2f), height.toFloat())

        drawLimit(canvas, s, fmt, ::x, ::y)
        drawRequired(canvas, s, ::x, ::y)
        drawProjection(canvas, s, statusColor, ::x, ::y)
        drawActual(canvas, s, statusColor, ::x, ::y)
        drawLimitHit(canvas, s, ::x, ::y)
        canvas.restore()

        drawLastExit(canvas, s, ::x)
        drawXAxis(canvas, s, ::x)
    }

    /** Everything above the limit is the danger zone: a red wash fading toward the line. */
    private fun drawDangerZone(canvas: Canvas, s: ForecastSeries, y: (Double) -> Float) {
        val ly = y(s.limit)
        if (ly <= padTop) return
        val red = palette.status(Status.LAST_EXIT).main
        fill.shader = LinearGradient(0f, padTop, 0f, ly, red.withAlpha(0.16f), red.withAlpha(0.03f), Shader.TileMode.CLAMP)
        canvas.drawRect(padLeft, padTop, width - padRight, ly, fill)
        fill.shader = null
    }

    private fun drawGrid(canvas: Canvas, fmt: AmountFormat, yMin: Double, yMax: Double, step: Double, y: (Double) -> Float) {
        line.pathEffect = null
        line.strokeWidth = dp(1f)
        line.color = palette.outline
        text.textAlign = Paint.Align.RIGHT
        var value = yMin
        while (value <= yMax + step / 2) {
            val gy = y(value)
            canvas.drawLine(padLeft, gy, width - padRight, gy, line)
            canvas.drawText(compact(fmt, value), padLeft - dp(6f), gy + dp(4f), text)
            value += step
        }
    }

    /** Round tick spacing (1, 2, 2.5 or 5 times a power of ten) giving about three grid lines. */
    private fun niceStep(range: Double): Double {
        val raw = range / 3.0
        val magnitude = 10.0.pow(floor(log10(raw)))
        val nice = when (raw / magnitude) {
            in 0.0..1.0 -> 1.0
            in 1.0..2.0 -> 2.0
            in 2.0..2.5 -> 2.5
            in 2.5..5.0 -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }

    private fun drawLimit(canvas: Canvas, s: ForecastSeries, fmt: AmountFormat, x: (Double) -> Float, y: (Double) -> Float) {
        val color = palette.status(Status.LAST_EXIT).main
        val ly = y(s.limit)
        line.color = color
        line.strokeWidth = dp(2f)
        line.pathEffect = dash
        canvas.drawLine(padLeft, ly, width - padRight, ly, line)
        boldText.color = color
        boldText.textAlign = Paint.Align.LEFT
        canvas.drawText("LIMIT ${fmt.amount(s.limit)}", padLeft + dp(4f), ly - dp(5f), boldText)
    }

    private fun drawRequired(canvas: Canvas, s: ForecastSeries, x: (Double) -> Float, y: (Double) -> Float) {
        val end = s.requiredEnd ?: return
        if (s.status == Status.SAFE) return // only meaningful when the current pace overshoots
        line.color = palette.status(Status.SAFE).main
        line.strokeWidth = dp(2f)
        line.pathEffect = dot
        canvas.drawLine(x(s.todayX.toDouble()), y(s.used), x(s.totalDays.toDouble()), y(end), line)
    }

    private fun drawProjection(canvas: Canvas, s: ForecastSeries, color: Int, x: (Double) -> Float, y: (Double) -> Float) {
        if (s.todayX >= s.totalDays) return
        line.color = color
        line.strokeWidth = dp(2.5f)
        line.pathEffect = dash
        canvas.drawLine(x(s.todayX.toDouble()), y(s.used), x(s.totalDays.toDouble()), y(s.projectedEnd), line)
    }

    private fun drawActual(canvas: Canvas, s: ForecastSeries, color: Int, x: (Double) -> Float, y: (Double) -> Float) {
        if (s.actual.isEmpty()) return
        path.reset()
        s.actual.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(0.0), y(v)) else path.lineTo(x(i.toDouble()), y(v)) }
        val area = Path(path).apply {
            lineTo(x((s.actual.size - 1).toDouble()), y(0.0))
            lineTo(x(0.0), y(0.0))
            close()
        }
        // Area fades from the line down to the baseline.
        val top = y(s.actual.maxOrNull() ?: s.used)
        fill.shader = LinearGradient(0f, top, 0f, y(0.0), color.withAlpha(0.32f), color.withAlpha(0.02f), Shader.TileMode.CLAMP)
        canvas.drawPath(area, fill)
        fill.shader = null
        line.pathEffect = null
        // Soft glow under the line, then the line itself.
        line.color = color.withAlpha(0.18f)
        line.strokeWidth = dp(9f)
        canvas.drawPath(path, line)
        line.color = color
        line.strokeWidth = dp(3f)
        canvas.drawPath(path, line)
        // "You are here": haloed dot.
        val cx = x(s.todayX.toDouble())
        val cy = y(s.used)
        fill.color = color.withAlpha(0.22f)
        canvas.drawCircle(cx, cy, dp(11f), fill)
        fill.color = palette.surface
        canvas.drawCircle(cx, cy, dp(6.5f), fill)
        fill.color = color
        canvas.drawCircle(cx, cy, dp(4.5f), fill)
    }

    private fun drawLimitHit(canvas: Canvas, s: ForecastSeries, x: (Double) -> Float, y: (Double) -> Float) {
        val hitX = s.limitHitX ?: return
        val cx = x(hitX)
        val cy = y(s.limit)
        val color = palette.status(Status.PAST_THE_LINE).main
        line.pathEffect = null
        line.color = color
        line.strokeWidth = dp(2.5f)
        val r = dp(5f)
        canvas.drawLine(cx - r, cy - r, cx + r, cy + r, line)
        canvas.drawLine(cx - r, cy + r, cx + r, cy - r, line)
    }

    private fun drawLastExit(canvas: Canvas, s: ForecastSeries, x: (Double) -> Float) {
        val exitX = s.lastExitX ?: return
        val stage = if (s.status == Status.ACT_SOON) Status.ACT_SOON else Status.LAST_EXIT
        val color = palette.status(stage)
        val gradient = palette.statusGradient(stage)
        val lx = x(exitX)
        line.pathEffect = null
        line.color = color.main.withAlpha(0.2f)
        line.strokeWidth = dp(8f)
        canvas.drawLine(lx, padTop - dp(4f), lx, height - padBottom, line)
        line.color = color.main
        line.strokeWidth = dp(2.5f)
        canvas.drawLine(lx, padTop - dp(4f), lx, height - padBottom, line)
        fill.color = color.main
        canvas.drawCircle(lx, height - padBottom, dp(4f), fill)

        val date = startDate.plusDays(exitX.toLong() - 1)
        val label = "LAST EXIT · ${DateText.short(date, today)}"
        boldText.textAlign = Paint.Align.LEFT
        boldText.color = color.onMain
        val textW = boldText.measureText(label)
        val pillW = textW + dp(16f)
        val pillH = dp(22f)
        val left = (lx - pillW / 2f).coerceIn(dp(2f), width - pillW - dp(2f))
        rect.set(left, dp(4f), left + pillW, dp(4f) + pillH)
        fill.shader = LinearGradient(rect.left, 0f, rect.right, 0f, gradient[0], gradient[1], Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, fill)
        fill.shader = null
        boldText.color = android.graphics.Color.WHITE
        canvas.drawText(label, left + dp(8f), rect.centerY() - (boldText.descent() + boldText.ascent()) / 2f, boldText)
    }

    private fun drawXAxis(canvas: Canvas, s: ForecastSeries, x: (Double) -> Float) {
        val baseline = height - dp(8f)
        text.textAlign = Paint.Align.LEFT
        val startLabel = DateText.short(startDate, today)
        canvas.drawText(startLabel, padLeft, baseline, text)
        text.textAlign = Paint.Align.RIGHT
        val endLabel = DateText.short(startDate.plusDays(s.totalDays - 1L), today)
        canvas.drawText(endLabel, width - padRight, baseline, text)

        val tx = x(s.todayX.toDouble())
        val todayLabel = "Now"
        boldText.color = palette.onSurface
        boldText.textAlign = Paint.Align.CENTER
        val half = boldText.measureText(todayLabel) / 2f + dp(4f)
        val startEnd = padLeft + text.measureText(startLabel)
        val endStart = width - padRight - text.measureText(endLabel)
        if (tx - half > startEnd && tx + half < endStart) canvas.drawText(todayLabel, tx, baseline, boldText)
    }

    private fun compact(fmt: AmountFormat, value: Double): String {
        if (abs(value) < 1000) return fmt.amount(value)
        val k = value / 1000.0
        val whole = abs(k - Math.round(k)) < 0.05
        val digits = if (whole || abs(k) >= 10) "%.0fk".format(k) else "%.1fk".format(k)
        return if (fmt.type == com.lastexit.core.TrackerType.MONEY) "${fmt.unit}$digits" else digits
    }
}
