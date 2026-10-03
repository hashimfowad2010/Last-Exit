package com.lastexit.app.ui.kit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lastexit.core.Status

/** In-app alert that slides in when a status gets worse (or recovers). */
@SuppressLint("ViewConstructor")
class AlertBannerView(context: Context, private val palette: Palette) : LinearLayout(context) {
    private val iconView = ImageView(context)
    private val title: TextView
    private val body: TextView
    private val actions = Ui.horizontal(context, Gravity.END or Gravity.CENTER_VERTICAL)
    private var hideRunnable: Runnable? = null

    init {
        orientation = VERTICAL
        setPadding(dpi(16), dpi(14), dpi(10), dpi(8))
        elevation = dp(6f)
        visibility = GONE
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        val row = Ui.horizontal(context, Gravity.TOP)
        iconView.layoutParams = LayoutParams(dpi(34), dpi(34)).apply { marginEnd = dpi(12) }
        row.addView(iconView)
        val texts = Ui.vertical(context)
        title = Ui.text(context, 16f, palette.onSurface, bold = true)
        body = Ui.text(context, 14f, palette.onSurface)
        texts.addView(title)
        texts.addView(body, Ui.matchWrap().apply { topMargin = dpi(2) })
        row.addView(texts, Ui.weight())
        addView(row)
        addView(actions, Ui.matchWrap().apply { topMargin = dpi(4) })
    }

    fun show(
        status: Status,
        heading: String,
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        autoHideMs: Long = 0,
    ) {
        val gradient = palette.statusGradient(status)
        background = Gradients.diagonal(intArrayOf(gradient[0], gradient[1]), dp(22f))
        Gradients.tintShadow(this, gradient[1])
        iconView.setImageDrawable(context.icon(statusIcon(status), Color.WHITE))
        iconView.background = Gradients.oval(intArrayOf(Color.WHITE.withAlpha(0.28f), Color.WHITE.withAlpha(0.12f)))
        iconView.setPadding(dpi(5), dpi(5), dpi(5), dpi(5))
        title.text = heading
        title.setTextColor(Color.WHITE)
        body.text = message
        body.setTextColor(Color.WHITE.withAlpha(0.92f))
        actions.removeAllViews()
        if (actionLabel != null && onAction != null) {
            actions.addView(
                Ui.button(context, palette, actionLabel, ButtonStyle.FILLED, accent = Color.WHITE, onAccent = gradient[2], gradient = null) {
                    hide()
                    onAction()
                }.apply { elevation = 0f },
            )
        }
        actions.addView(Ui.button(context, palette, "Dismiss", ButtonStyle.TEXT, accent = Color.WHITE) { hide() })
        contentDescription = "$heading. $message"
        hideRunnable?.let { removeCallbacks(it) }
        if (visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = -dp(24f)
            animate().alpha(1f).translationY(0f).setDuration(260).start()
        } else {
            animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }.start()
        }
        announceForAccessibility("$heading. $message")
        if (autoHideMs > 0) {
            hideRunnable = Runnable { hide() }.also { postDelayed(it, autoHideMs) }
        }
    }

    fun hide() {
        hideRunnable?.let { removeCallbacks(it) }
        if (visibility != View.VISIBLE) return
        animate().alpha(0f).translationY(-dp(16f)).setDuration(200).withEndAction { visibility = View.GONE }.start()
    }
}
