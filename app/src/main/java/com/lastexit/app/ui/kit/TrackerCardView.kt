package com.lastexit.app.ui.kit

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.lastexit.app.R
import com.lastexit.app.data.TrackerSnapshot
import com.lastexit.core.DateText
import com.lastexit.core.Status
import com.lastexit.core.TrackerType

fun typeIcon(type: TrackerType): Int = when (type) {
    TrackerType.MONEY -> R.drawable.ic_money
    TrackerType.TIME -> R.drawable.ic_time
    TrackerType.RESOURCE -> R.drawable.ic_data
    TrackerType.RISK -> R.drawable.ic_risk
}

/** Per-type accent gradients (template cards, badges). Deliberately clear of status colours. */
fun typeGradient(type: TrackerType): IntArray = when (type) {
    TrackerType.MONEY -> intArrayOf(0xFF14B8A6.toInt(), 0xFF0F766E.toInt())
    TrackerType.TIME -> intArrayOf(0xFF818CF8.toInt(), 0xFF4F46E5.toInt())
    TrackerType.RESOURCE -> intArrayOf(0xFF38BDF8.toInt(), 0xFF2563EB.toInt())
    TrackerType.RISK -> intArrayOf(0xFFC084FC.toInt(), 0xFF7E22CE.toInt())
}

/**
 * Home-screen card: a gradient status strip on top, a ring gauge of the limit used around the type
 * icon, the status chip, big numbers, a projection-aware progress bar and a plain-language message.
 */
@SuppressLint("ViewConstructor")
class TrackerCardView(
    context: Context,
    private val palette: Palette,
    showLogButton: Boolean,
    private val onOpen: () -> Unit,
    private val onLog: () -> Unit,
) : LinearLayout(context) {
    private val surface = AccentCardDrawable(
        surface = palette.surface,
        radius = dp(26f),
        strokeColor = palette.outline,
        strokeWidth = if (palette.isDark) dp(1f) else 0f,
        stripHeight = dp(5f),
    )
    private val ring = RingGaugeView(context, sizeDp = 54, strokeDp = 5f)
    private val name = Ui.text(context, 18f, palette.onSurface, bold = true, maxLines = 2)
    private val period = Ui.text(context, 13f, palette.onSurfaceMuted, maxLines = 1)
    val chip = StatusChipView(context, palette)
    private val usedText = Ui.number(context, 30f, palette.onSurface)
    private val ofText = Ui.text(context, 15f, palette.onSurfaceMuted)
    private val daysLeft = Ui.text(context, 12f, palette.onSurface, bold = true)
    private val progress = LimitProgressView(context, palette)
    private val message = Ui.text(context, 15f, palette.onSurface)
    private val paceLine = Ui.text(context, 13f, palette.onSurfaceMuted, maxLines = 1)
    private val footer = Ui.horizontal(context)
    private var bound = false

    init {
        orientation = VERTICAL
        setPadding(dpi(18), dpi(20), dpi(18), dpi(12))
        background = RippleDrawable(ColorStateList.valueOf(palette.onSurface.withAlpha(0.08f)), surface, Ui.rounded(android.graphics.Color.WHITE, dp(26f)))
        if (!palette.isDark) {
            elevation = dp(3f)
            Gradients.tintShadow(this, 0xFF1E293B.toInt())
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { onOpen() }

        val header = Ui.horizontal(context)
        header.addView(ring, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(14) })
        val titles = Ui.vertical(context)
        titles.addView(name)
        titles.addView(period, Ui.matchWrap().apply { topMargin = dpi(2) })
        header.addView(titles, Ui.weight())
        header.addView(chip, Ui.wrap().apply { marginStart = dpi(4) })
        addView(header)

        val numbers = Ui.horizontal(context, Gravity.BOTTOM)
        numbers.addView(usedText)
        numbers.addView(ofText, Ui.weight().apply { marginStart = dpi(6); bottomMargin = dpi(5) })
        daysLeft.setPadding(dpi(10), dpi(4), dpi(10), dpi(4))
        daysLeft.background = Ui.rounded(palette.surfaceVariant, dp(12f))
        numbers.addView(daysLeft, Ui.wrap().apply { bottomMargin = dpi(6) })
        addView(numbers, Ui.matchWrap().apply { topMargin = dpi(14) })
        addView(progress, Ui.matchWrap().apply { topMargin = dpi(6) })
        addView(message, Ui.matchWrap().apply { topMargin = dpi(10) })

        footer.addView(paceLine, Ui.weight())
        if (showLogButton) {
            footer.addView(
                Ui.button(context, palette, "Log", ButtonStyle.TONAL, R.drawable.ic_add) { onLog() }.apply {
                    contentDescription = "Log an amount"
                },
            )
            addView(footer, Ui.matchWrap().apply { topMargin = dpi(6) })
        } else {
            footer.minimumHeight = dpi(36)
            addView(footer, Ui.matchWrap().apply { topMargin = dpi(8) })
            setPadding(dpi(18), dpi(20), dpi(18), dpi(14))
        }
    }

    fun bind(snapshot: TrackerSnapshot, animate: Boolean = bound) {
        val f = snapshot.forecast
        val t = snapshot.tracker
        val fmt = snapshot.format
        val gradient = palette.statusGradient(f.status)
        surface.accent = intArrayOf(gradient[0], gradient[1])
        ring.bind(
            target = f.usedFraction.toFloat(),
            arcColors = intArrayOf(gradient[0], gradient[1], gradient[0]),
            trackColor = palette.surfaceVariant,
            icon = context.icon(typeIcon(t.type), palette.status(f.status).main),
            animate = animate,
        )
        name.text = t.name
        period.text = "${DateText.short(t.startDate, f.today)} – ${DateText.short(t.deadline, f.today)}"
        chip.bind(f.status, snapshot.messages.chipLabel(f), snapshot.messages.spokenStatus(f), animate)
        usedText.text = fmt.amount(f.used)
        ofText.text = "of ${fmt.amount(t.limit)}"
        daysLeft.text = when {
            f.hasEnded -> "Ended"
            !f.hasStarted -> "Not started"
            f.remainingDays == 0 -> "Last day"
            else -> "${DateText.days(f.remainingDays)} left"
        }
        progress.bind(f.usedFraction, f.projectedFraction, f.status, animate)
        message.text = snapshot.messages.headline(f)
        paceLine.text = when {
            f.entryCount == 0 || f.hasEnded -> ""
            f.status == Status.SAFE || f.requiredPace == null -> "Pace ${fmt.pace(f.pace)}"
            else -> "Pace ${fmt.pace(f.pace)} · needed ${fmt.pace(f.requiredPace!!)}"
        }
        contentDescription = snapshot.messages.accessibilitySummary(t.name, f)
        footer.visibility = if (f.hasEnded) View.GONE else View.VISIBLE
        bound = true
    }

    /** Name text, exposed so lists can find a card in tests. */
    val title: TextView get() = name
}
