package com.lastexit.app.ui.kit

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lastexit.app.R

/**
 * Bottom sheet for picking a colour theme and light/dark appearance, with a live preview of the
 * header gradient. Status colours are the same in every theme on purpose.
 */
class ThemeSheet(
    private val activity: Activity,
    private val palette: Palette,
    private val onApply: (BrandTheme, Appearance) -> Unit,
) : Dialog(activity) {
    private var brand = ThemePrefs.brand(activity)
    private var appearance = ThemePrefs.appearance(activity)
    private val swatches = LinkedHashMap<BrandTheme, FrameLayout>()
    private lateinit var preview: LinearLayout
    private lateinit var previewHero: HeroDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(build())
        window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.BOTTOM)
            w.setWindowAnimations(R.style.SheetAnimation)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.45f)
        }
        refresh()
    }

    private fun build(): View {
        val ctx = context
        val sheet = Ui.vertical(ctx).apply {
            setPadding(ctx.dpi(20), ctx.dpi(10), ctx.dpi(20), ctx.dpi(16))
            background = Ui.rounded(palette.surface, ctx.dp(28f)).apply {
                cornerRadii = floatArrayOf(ctx.dp(28f), ctx.dp(28f), ctx.dp(28f), ctx.dp(28f), 0f, 0f, 0f, 0f)
            }
        }
        sheet.addView(View(ctx).apply { background = Ui.rounded(palette.outline, ctx.dp(2f)) },
            LinearLayout.LayoutParams(ctx.dpi(36), ctx.dpi(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = ctx.dpi(14) })
        sheet.addView(Ui.text(ctx, 20f, palette.onSurface, bold = true, value = "Make it yours").apply { markAsHeading() })
        sheet.addView(Ui.text(ctx, 14f, palette.onSurfaceMuted,
            value = "Status colours never change, so red always means red."))

        // Live preview of the header.
        previewHero = HeroDrawable(brand.gradient(ctx), ctx.dp(20f), topRadius = ctx.dp(20f))
        preview = Ui.vertical(ctx, padding = 16).apply {
            background = previewHero
            minimumHeight = ctx.dpi(96)
        }
        preview.addView(Ui.text(ctx, 12f, Color.WHITE.withAlpha(0.75f), bold = true, value = "PREVIEW").apply { letterSpacing = 0.14f })
        preview.addView(Ui.text(ctx, 22f, Color.WHITE, bold = true, value = "All clear"))
        preview.addView(Ui.text(ctx, 13f, Color.WHITE.withAlpha(0.85f), value = "Every limit is on track."))
        sheet.addView(preview, Ui.matchWrap().apply { topMargin = ctx.dpi(14) })

        sheet.addView(Ui.label(ctx, palette, "Colour theme"), Ui.matchWrap().apply { topMargin = ctx.dpi(18) })
        val row = Ui.horizontal(ctx, Gravity.TOP)
        BrandTheme.entries.filter { it.isAvailable }.forEach { theme ->
            row.addView(swatch(theme), Ui.weight())
        }
        sheet.addView(row, Ui.matchWrap().apply { topMargin = ctx.dpi(10) })

        sheet.addView(Ui.label(ctx, palette, "Appearance"), Ui.matchWrap().apply { topMargin = ctx.dpi(18) })
        val mode = SegmentedControl(ctx, palette, Appearance.entries.map { it.label }) { index ->
            appearance = Appearance.entries[index]
        }
        mode.select(appearance.ordinal)
        sheet.addView(mode, Ui.matchWrap().apply { topMargin = ctx.dpi(8) })

        val buttons = Ui.horizontal(ctx, Gravity.END or Gravity.CENTER_VERTICAL)
        buttons.addView(Ui.button(ctx, palette, "Cancel", ButtonStyle.TEXT) { dismiss() })
        buttons.addView(Ui.button(ctx, palette, "Apply", ButtonStyle.FILLED, R.drawable.ic_check, gradient = intArrayOf(brand.gradient(ctx)[0], brand.gradient(ctx)[1])) {
            dismiss()
            onApply(brand, appearance)
        }.also { applyButton = it }, Ui.wrap().apply { marginStart = ctx.dpi(8) })
        sheet.addView(buttons, Ui.matchWrap().apply { topMargin = ctx.dpi(18) })
        return sheet
    }

    private var applyButton: TextView? = null

    private fun swatch(theme: BrandTheme): View {
        val ctx = context
        val column = Ui.vertical(ctx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            minimumHeight = ctx.dpi(48)
            contentDescription = "${theme.label} theme"
            setOnClickListener {
                brand = theme
                refresh()
            }
        }
        val circle = FrameLayout(ctx).apply { background = Gradients.oval(theme.gradient(ctx)) }
        circle.addView(ImageView(ctx).apply {
            setImageDrawable(ctx.icon(R.drawable.ic_check, Color.WHITE))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(ctx.dpi(24), ctx.dpi(24), Gravity.CENTER))
        swatches[theme] = circle
        column.addView(circle, LinearLayout.LayoutParams(ctx.dpi(52), ctx.dpi(52)))
        column.addView(Ui.text(ctx, 12f, palette.onSurface, value = theme.label).apply { gravity = Gravity.CENTER },
            Ui.matchWrap().apply { topMargin = ctx.dpi(6) })
        return column
    }

    private fun refresh() {
        val ctx = context
        swatches.forEach { (theme, circle) ->
            val selected = theme == brand
            circle.getChildAt(0).visibility = if (selected) View.VISIBLE else View.INVISIBLE
            circle.foreground = if (selected) {
                Ui.rounded(Color.TRANSPARENT, ctx.dp(26f), palette.onSurface, ctx.dpi(3))
            } else {
                null
            }
            (circle.parent as? View)?.contentDescription = "${theme.label} theme" + if (selected) ", selected" else ""
        }
        val gradient = brand.gradient(ctx)
        previewHero.colors = gradient
        applyButton?.background = Ui.ripple(Gradients.horizontal(intArrayOf(gradient[0], gradient[1]), ctx.dp(24f)), Color.WHITE.withAlpha(0.2f), ctx.dp(24f))
    }
}
