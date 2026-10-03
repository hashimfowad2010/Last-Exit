package com.lastexit.app.ui.kit

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Minimal snackbar for "Entry deleted · Undo". */
@SuppressLint("ViewConstructor")
class SnackbarHost(context: Context, palette: Palette) : LinearLayout(context) {
    private val message: TextView
    private val action: TextView
    private val hideRunnable = Runnable { hide() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val bg = if (palette.isDark) 0xFFE6ECE9.toInt() else 0xFF1F2624.toInt()
        val fg = if (palette.isDark) 0xFF17201D.toInt() else 0xFFF1F5F3.toInt()
        val accent = if (palette.isDark) palette.primaryContainer else 0xFF8FE3D6.toInt()
        background = Ui.rounded(bg, dp(14f))
        elevation = dp(8f)
        setPadding(dpi(18), dpi(4), dpi(6), dpi(4))
        minimumHeight = dpi(52)
        message = Ui.text(context, 14f, fg)
        action = Ui.text(context, 14f, if (palette.isDark) palette.primary else accent, bold = true).apply {
            gravity = Gravity.CENTER
            minHeight = dpi(48)
            setPadding(dpi(14), 0, dpi(14), 0)
        }
        addView(message, Ui.weight())
        addView(action)
        visibility = GONE
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun show(text: String, actionLabel: String?, durationMs: Long = 4500, onAction: (() -> Unit)?) {
        message.text = text
        action.text = actionLabel?.uppercase() ?: ""
        action.visibility = if (actionLabel != null) View.VISIBLE else View.GONE
        action.setOnClickListener {
            hide()
            onAction?.invoke()
        }
        removeCallbacks(hideRunnable)
        visibility = View.VISIBLE
        alpha = 0f
        translationY = dp(24f)
        animate().alpha(1f).translationY(0f).setDuration(200).start()
        postDelayed(hideRunnable, durationMs)
    }

    fun hide() {
        removeCallbacks(hideRunnable)
        if (visibility != View.VISIBLE) return
        animate().alpha(0f).translationY(dp(24f)).setDuration(180).withEndAction { visibility = View.GONE }.start()
    }
}
