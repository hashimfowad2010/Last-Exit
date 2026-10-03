package com.lastexit.app.ui.kit

import android.animation.ArgbEvaluator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View

/** Gradient building blocks shared by every screen. */
object Gradients {
    fun horizontal(colors: IntArray, radius: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply { cornerRadius = radius }

    fun diagonal(colors: IntArray, radius: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }

    fun oval(colors: IntArray): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { shape = GradientDrawable.OVAL }

    /** Interpolates two gradients stop by stop (used for animated status changes). */
    fun mix(from: IntArray, to: IntArray, fraction: Float): IntArray {
        val evaluator = ArgbEvaluator()
        return IntArray(to.size) { i -> evaluator.evaluate(fraction, from.getOrElse(i) { to[i] }, to[i]) as Int }
    }

    /** Coloured, softer shadow where the platform supports it (API 28+). */
    fun tintShadow(view: View, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineSpotShadowColor = color
            view.outlineAmbientShadowColor = color
        }
    }
}

/**
 * Header background: a vertical brand or status gradient, two soft light glows and a faint dashed
 * "road" curve that nods to the app's name. The top edge is a single colour so it meets the status
 * bar without a seam.
 */
class HeroDrawable(
    colors: IntArray,
    private val bottomRadius: Float,
    private val decorate: Boolean = true,
    private val topRadius: Float = 0f,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val road = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(34, 255, 255, 255)
    }
    private val path = Path()
    private val clip = Path()
    private val rect = RectF()

    var colors: IntArray = colors
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        rect.set(b)
        clip.reset()
        val r = bottomRadius
        val t = topRadius
        clip.addRoundRect(rect, floatArrayOf(t, t, t, t, r, r, r, r), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        fill.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, fill)
        if (decorate) {
            glow.shader = RadialGradient(b.left + w * 0.92f, b.top + h * 0.12f, w * 0.55f,
                Color.argb(56, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, glow)
            glow.shader = RadialGradient(b.left + w * 0.05f, b.bottom - h * 0.05f, w * 0.5f,
                Color.argb(30, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(rect, glow)
            drawRoad(canvas, b, w, h)
        }
        canvas.restore()
    }

    /** Two lane edges and a dashed centre line sweeping off to the right, like an exit ramp. */
    private fun drawRoad(canvas: Canvas, b: Rect, w: Float, h: Float) {
        val unit = w / 100f
        road.strokeWidth = unit * 0.9f
        road.pathEffect = null
        for (offset in floatArrayOf(-7f, 7f)) {
            path.reset()
            path.moveTo(b.left + w * 0.62f + offset * unit, b.bottom + unit * 2)
            path.cubicTo(
                b.left + w * 0.70f + offset * unit, b.top + h * 0.55f,
                b.left + w * 0.86f + offset * unit * 0.6f, b.top + h * 0.30f,
                b.right + unit * 6, b.top + h * 0.12f + offset * unit * 0.5f,
            )
            canvas.drawPath(path, road)
        }
        road.pathEffect = DashPathEffect(floatArrayOf(unit * 3.5f, unit * 3f), 0f)
        path.reset()
        path.moveTo(b.left + w * 0.62f, b.bottom + unit * 2)
        path.cubicTo(b.left + w * 0.70f, b.top + h * 0.55f, b.left + w * 0.86f, b.top + h * 0.30f, b.right + unit * 6, b.top + h * 0.12f)
        canvas.drawPath(path, road)
    }

    override fun getOutline(outline: Outline) {
        outline.setRect(bounds)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Card surface with an optional gradient accent strip along the top edge (status colour), so a
 * card's state is visible at a glance even before reading the chip.
 */
class AccentCardDrawable(
    private val surface: Int,
    private val radius: Float,
    private val strokeColor: Int,
    private val strokeWidth: Float,
    private val stripHeight: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val clip = Path()

    var accent: IntArray? = null
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        paint.shader = null
        paint.color = surface
        canvas.drawRoundRect(rect, radius, radius, paint)
        accent?.let { colors ->
            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.shader = LinearGradient(rect.left, 0f, rect.right, 0f, colors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + stripHeight, paint)
            canvas.restore()
        }
        if (strokeWidth > 0f) {
            stroke.color = strokeColor
            stroke.strokeWidth = strokeWidth
            val inset = strokeWidth / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, radius - inset, radius - inset, stroke)
        }
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radius)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
