package com.lastexit.app.ui.kit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lastexit.core.Status

fun Context.dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

fun Context.dpi(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun View.dpi(value: Int): Int = context.dpi(value)

fun View.dp(value: Float): Float = context.dp(value)

/** Headings help TalkBack users jump between sections (API 28+; a no-op below that). */
fun View.markAsHeading() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) isAccessibilityHeading = true
}

fun Int.withAlpha(alpha: Float): Int = Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(this), Color.green(this), Color.blue(this))

fun Context.icon(resId: Int, tint: Int): Drawable =
    getDrawable(resId)!!.mutate().also { it.setTint(tint) }

fun statusIcon(status: Status): Int = when (status) {
    Status.SAFE -> com.lastexit.app.R.drawable.ic_check_circle
    Status.ACT_SOON -> com.lastexit.app.R.drawable.ic_warning
    Status.LAST_EXIT -> com.lastexit.app.R.drawable.ic_exit
    Status.PAST_THE_LINE -> com.lastexit.app.R.drawable.ic_stop
}

enum class ButtonStyle { FILLED, TONAL, OUTLINED, TEXT }

/** Small helpers for building views in code with consistent spacing, shapes and touch targets. */
object Ui {
    fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun wrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun weight(weight: Float = 1f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)

    fun LinearLayout.LayoutParams.margins(ctx: Context, top: Int = 0, bottom: Int = 0, start: Int = 0, end: Int = 0) =
        apply {
            topMargin = ctx.dpi(top)
            bottomMargin = ctx.dpi(bottom)
            marginStart = ctx.dpi(start)
            marginEnd = ctx.dpi(end)
        }

    fun text(
        ctx: Context,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        value: CharSequence = "",
        maxLines: Int = Int.MAX_VALUE,
    ): TextView = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text = value
        if (maxLines != Int.MAX_VALUE) {
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }
        setLineSpacing(0f, 1.15f)
    }

    /** Big numbers use tabular figures so digits do not jump while values animate. */
    fun number(ctx: Context, sizeSp: Float, color: Int): TextView =
        text(ctx, sizeSp, color, bold = true).apply {
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    fun label(ctx: Context, palette: Palette, value: String): TextView =
        text(ctx, 12f, palette.onSurfaceMuted, bold = true, value = value.uppercase()).apply {
            letterSpacing = 0.08f
        }

    fun rounded(color: Int, radius: Float, strokeColor: Int = Color.TRANSPARENT, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    fun ripple(content: Drawable?, rippleColor: Int, radius: Float): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, rounded(Color.WHITE, radius))

    fun vertical(ctx: Context, padding: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = ctx.dpi(padding)
        setPadding(p, p, p, p)
    }

    fun horizontal(ctx: Context, gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
    }

    /** Surface card: soft tinted shadow in light mode, a hairline outline in dark mode. */
    fun card(ctx: Context, palette: Palette, padding: Int = 16, color: Int = palette.surface): LinearLayout =
        vertical(ctx, padding).apply {
            elevate(this, palette, color)
        }

    /** Applies the shared card look (radius, shadow or outline) to any view. */
    fun elevate(view: View, palette: Palette, color: Int = palette.surface, radiusDp: Float = 24f) {
        val ctx = view.context
        if (palette.isDark) {
            view.background = rounded(color, ctx.dp(radiusDp), palette.outline, ctx.dpi(1))
            view.elevation = 0f
        } else {
            view.background = rounded(color, ctx.dp(radiusDp))
            view.elevation = ctx.dp(3f)
            Gradients.tintShadow(view, 0xFF1E293B.toInt())
        }
    }

    /** Rounded square with a gradient fill and a white icon: the accent for section headers. */
    fun gradientTile(ctx: Context, iconRes: Int, colors: IntArray, sizeDp: Int = 30): ImageView =
        ImageView(ctx).apply {
            setImageDrawable(ctx.icon(iconRes, Color.WHITE))
            scaleType = ImageView.ScaleType.FIT_CENTER
            val inset = ctx.dpi(sizeDp) / 5
            setPadding(inset, inset, inset, inset)
            background = Gradients.diagonal(colors, ctx.dp(sizeDp * 0.32f))
            layoutParams = LinearLayout.LayoutParams(ctx.dpi(sizeDp), ctx.dpi(sizeDp))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    /** Section title with a gradient icon tile, e.g. "Forecast", "Key numbers". */
    fun sectionHeader(ctx: Context, palette: Palette, title: String, iconRes: Int, colors: IntArray = palette.buttonGradient): LinearLayout {
        val row = horizontal(ctx)
        row.addView(gradientTile(ctx, iconRes, colors), LinearLayout.LayoutParams(ctx.dpi(30), ctx.dpi(30)).apply { marginEnd = ctx.dpi(10) })
        row.addView(text(ctx, 18f, palette.onSurface, bold = true, value = title).apply { markAsHeading() }, weight())
        row.setPadding(0, 0, 0, ctx.dpi(8))
        return row
    }

    fun spacer(ctx: Context, heightDp: Int): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dpi(heightDp))
    }

    fun divider(ctx: Context, palette: Palette): View = View(ctx).apply {
        setBackgroundColor(palette.outline)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dpi(1))
    }

    /**
     * Pill button with a 48dp minimum touch target. Filled buttons use the brand gradient (or the
     * [gradient] given, e.g. a status gradient) with white text and a soft coloured shadow.
     */
    fun button(
        ctx: Context,
        palette: Palette,
        label: String,
        style: ButtonStyle = ButtonStyle.FILLED,
        iconRes: Int? = null,
        accent: Int = palette.primary,
        onAccent: Int = palette.onPrimary,
        gradient: IntArray? = if (style == ButtonStyle.FILLED && accent == palette.primary) palette.buttonGradient else null,
        onClick: () -> Unit,
    ): TextView {
        val radius = ctx.dp(24f)
        val (bg, fg) = when (style) {
            ButtonStyle.FILLED -> if (gradient != null) Gradients.horizontal(gradient, radius) to Color.WHITE else rounded(accent, radius) to onAccent
            ButtonStyle.TONAL -> rounded(accent.withAlpha(0.16f), radius) to accent
            ButtonStyle.OUTLINED -> rounded(Color.TRANSPARENT, radius, palette.outline, ctx.dpi(1)) to accent
            ButtonStyle.TEXT -> null to accent
        }
        return text(ctx, 15f, fg, bold = true, value = label).apply {
            gravity = Gravity.CENTER
            minHeight = ctx.dpi(48)
            minWidth = ctx.dpi(48)
            setPadding(ctx.dpi(if (style == ButtonStyle.TEXT) 12 else 20), 0, ctx.dpi(if (style == ButtonStyle.TEXT) 12 else 20), 0)
            background = ripple(bg, fg.withAlpha(0.2f), radius)
            if (iconRes != null) {
                val icon = ctx.icon(iconRes, fg)
                val size = ctx.dpi(18)
                icon.setBounds(0, 0, size, size)
                setCompoundDrawablesRelative(icon, null, null, null)
                compoundDrawablePadding = ctx.dpi(8)
            }
            isClickable = true
            isFocusable = true
            if (style == ButtonStyle.FILLED) {
                elevation = ctx.dp(3f)
                Gradients.tintShadow(this, gradient?.last() ?: accent)
            }
            setOnClickListener { onClick() }
        }
    }

    /** Round floating action button with the brand gradient and a coloured glow. */
    fun fab(ctx: Context, palette: Palette, iconRes: Int, description: String, sizeDp: Int = 64, onClick: () -> Unit): ImageButton =
        ImageButton(ctx).apply {
            setImageDrawable(ctx.icon(iconRes, Color.WHITE))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            background = ripple(Gradients.oval(palette.buttonGradient), Color.WHITE.withAlpha(0.25f), ctx.dp(sizeDp / 2f))
            elevation = ctx.dp(8f)
            Gradients.tintShadow(this, palette.gradient.last())
            layoutParams = LinearLayout.LayoutParams(ctx.dpi(sizeDp), ctx.dpi(sizeDp))
            setOnClickListener { onClick() }
        }

    fun iconButton(
        ctx: Context,
        palette: Palette,
        iconRes: Int,
        description: String,
        tint: Int = palette.onSurface,
        background: Int = Color.TRANSPARENT,
        sizeDp: Int = 48,
        onClick: () -> Unit,
    ): ImageButton = ImageButton(ctx).apply {
        setImageDrawable(ctx.icon(iconRes, tint))
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        val radius = ctx.dp(sizeDp / 2f)
        this.background = ripple(rounded(background, radius), tint.withAlpha(0.18f), radius)
        layoutParams = LinearLayout.LayoutParams(ctx.dpi(sizeDp), ctx.dpi(sizeDp))
        setOnClickListener { onClick() }
    }

    /** Circle with an icon inside, used for tracker types. */
    fun iconBadge(ctx: Context, iconRes: Int, tint: Int, background: Int, sizeDp: Int = 40): ImageView =
        ImageView(ctx).apply {
            setImageDrawable(ctx.icon(iconRes, tint))
            // Icon fills half the badge so large badges (empty state) get a large icon too.
            scaleType = ImageView.ScaleType.FIT_CENTER
            val inset = ctx.dpi(sizeDp) / 4
            setPadding(inset, inset, inset, inset)
            this.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(background)
            }
            layoutParams = LinearLayout.LayoutParams(ctx.dpi(sizeDp), ctx.dpi(sizeDp))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    /** Small rounded tag such as "SIMULATED" or "ONE-OFF". */
    fun tag(ctx: Context, value: String, fg: Int, bg: Int): TextView =
        text(ctx, 11f, fg, bold = true, value = value.uppercase()).apply {
            letterSpacing = 0.08f
            setPadding(ctx.dpi(8), ctx.dpi(3), ctx.dpi(8), ctx.dpi(3))
            background = rounded(bg, ctx.dp(8f))
        }

    fun frame(ctx: Context): FrameLayout = FrameLayout(ctx)
}
