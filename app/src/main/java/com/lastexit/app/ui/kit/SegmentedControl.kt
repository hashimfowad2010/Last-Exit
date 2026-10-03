package com.lastexit.app.ui.kit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Single-choice row of segments (e.g. 1x / 5x / 20x, or tracker type). */
@SuppressLint("ViewConstructor")
class SegmentedControl(
    context: Context,
    private val palette: Palette,
    private val options: List<String>,
    /** Translucent white style for use on gradient headers. */
    private val onGradient: Boolean = false,
    private val onSelected: (Int) -> Unit,
) : LinearLayout(context) {
    private val segments = ArrayList<TextView>()

    var selected: Int = 0
        private set

    init {
        orientation = HORIZONTAL
        background = if (onGradient) {
            Ui.rounded(Color.WHITE.withAlpha(0.14f), dp(24f))
        } else {
            Ui.rounded(palette.surfaceVariant, dp(24f))
        }
        setPadding(dpi(3), dpi(3), dpi(3), dpi(3))
        options.forEachIndexed { index, label ->
            val segment = Ui.text(context, 14f, palette.onSurface, bold = true, value = label).apply {
                gravity = Gravity.CENTER
                minHeight = dpi(44)
                setPadding(dpi(8), 0, dpi(8), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (selected != index) {
                        select(index)
                        onSelected(index)
                    }
                }
            }
            segments += segment
            addView(segment, Ui.weight())
        }
        select(0)
    }

    fun select(index: Int) {
        selected = index.coerceIn(0, options.size - 1)
        segments.forEachIndexed { i, view ->
            val on = i == selected
            val fill = when {
                !on -> Ui.rounded(Color.TRANSPARENT, dp(21f))
                onGradient -> Ui.rounded(Color.WHITE, dp(21f))
                else -> Gradients.horizontal(palette.buttonGradient, dp(21f))
            }
            view.background = Ui.ripple(fill, palette.primary.withAlpha(0.2f), dp(21f))
            view.setTextColor(
                when {
                    onGradient && on -> palette.gradient.last()
                    onGradient -> Color.WHITE.withAlpha(0.85f)
                    on -> Color.WHITE
                    else -> palette.onSurfaceMuted
                },
            )
            view.isSelected = on
            view.contentDescription = options[i] + if (on) ", selected" else ""
        }
    }
}
