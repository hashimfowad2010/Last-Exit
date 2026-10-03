package com.lastexit.app.ui.detail

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.lastexit.app.R
import com.lastexit.app.data.Entry
import com.lastexit.app.data.TrackerRecord
import com.lastexit.app.data.TrackerSnapshot
import com.lastexit.app.ui.Routes
import com.lastexit.app.ui.Screen
import com.lastexit.app.ui.ScreenHost
import com.lastexit.app.ui.TopBar
import com.lastexit.app.ui.kit.ButtonStyle
import com.lastexit.app.ui.kit.ForecastChartView
import com.lastexit.app.ui.kit.Gradients
import com.lastexit.app.ui.kit.HeroDrawable
import com.lastexit.app.ui.kit.RingGaugeView
import com.lastexit.app.ui.kit.Haptics
import com.lastexit.app.ui.kit.StatusChipView
import com.lastexit.app.ui.kit.SwipeToDeleteLayout
import com.lastexit.app.ui.kit.Ui
import com.lastexit.app.ui.kit.dp
import com.lastexit.app.ui.kit.dpi
import com.lastexit.app.ui.kit.icon
import com.lastexit.app.ui.kit.markAsHeading
import com.lastexit.app.ui.kit.statusIcon
import com.lastexit.app.ui.kit.withAlpha
import com.lastexit.app.ui.log.QuickLogSheet
import com.lastexit.core.DateText
import com.lastexit.core.Forecast
import com.lastexit.core.Status
import kotlin.math.max
import kotlin.math.roundToInt

/** Everything about one limit: hero status, chart, what-if, numbers, recovery plan and history. */
class DetailScreen(host: ScreenHost, route: String, private val trackerId: Long) : Screen(host, route) {
    private lateinit var hero: LinearLayout
    private lateinit var heroIcon: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroHeadline: TextView
    private lateinit var heroSubline: TextView
    private lateinit var heroNote: TextView
    private lateinit var heroChip: StatusChipView
    private lateinit var whatIfStrip: LinearLayout
    private lateinit var chart: ForecastChartView
    private lateinit var whatIfSeek: SeekBar
    private lateinit var whatIfValue: TextView
    private lateinit var whatIfReset: TextView
    private lateinit var numbers: LinearLayout
    private lateinit var recoveryCard: LinearLayout
    private lateinit var recoveryList: LinearLayout
    private lateinit var recoveryHeader: LinearLayout
    private lateinit var cutSeek: SeekBar
    private lateinit var cutValue: TextView
    private lateinit var historyTitle: TextView
    private lateinit var historyList: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var moreButton: View
    private lateinit var heroDrawable: HeroDrawable
    private lateinit var topBarView: LinearLayout
    private lateinit var ring: RingGaugeView
    private lateinit var heroGlass: LinearLayout
    private var heroColors: IntArray? = null
    private lateinit var paceLegend: LinearLayout
    private lateinit var actualLegend: LinearLayout

    private var whatIfPace: Double? = null
    private var whatIfMax = 1.0
    private var whatIfMaxReady = false
    private var draggingCut: Double? = null
    private var renderedEntries: List<Entry>? = null
    private var heroAnimator: ValueAnimator? = null
    private var lastStatus: Status? = null
    private var sheet: QuickLogSheet? = null
    private var pendingSheet: Bundle? = null
    private var hasRendered = false

    override fun statusBarColor(): Int = (heroColors ?: palette.statusGradient(Status.SAFE))[0]

    override fun statusBarDarkIcons(): Boolean = false

    override fun onCreateView(saved: Bundle?): View {
        saved?.takeIf { it.containsKey(KEY_WHAT_IF) }?.let { whatIfPace = it.getDouble(KEY_WHAT_IF) }
        pendingSheet = saved?.getBundle(KEY_SHEET)

        val root = FrameLayout(context)
        val column = Ui.vertical(context)
        moreButton = Ui.iconButton(context, palette, R.drawable.ic_more, "More options", tint = Color.WHITE) { showMenu(moreButton) }
        topBarView = TopBar.create(context, palette, "", { host.pop() }, moreButton, onGradient = true)
        titleView = (topBarView.getChildAt(1) as TextView)
        column.addView(topBarView)

        val scroll = ScrollView(context)
        val page = Ui.vertical(context)
        page.addView(heroCard())
        val content = Ui.vertical(context).apply {
            setPadding(dpi(16), 0, dpi(16), dpi(112))
            clipToPadding = false
        }
        page.addView(content, Ui.matchWrap().apply { topMargin = -dpi(36) })
        content.addView(chartCard())
        content.addView(whatIfCard(), Ui.matchWrap().apply { topMargin = dpi(12) })
        numbers = Ui.card(context, palette, padding = 18)
        content.addView(numbers, Ui.matchWrap().apply { topMargin = dpi(12) })
        recoveryCard = recoveryCard()
        content.addView(recoveryCard, Ui.matchWrap().apply { topMargin = dpi(12) })
        content.addView(cutBackCard(), Ui.matchWrap().apply { topMargin = dpi(12) })
        content.addView(historyCard(), Ui.matchWrap().apply { topMargin = dpi(12) })
        scroll.addView(page)
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column)

        val fab = Ui.button(context, palette, "Log", ButtonStyle.FILLED, R.drawable.ic_add) { openSheet(null) }.apply {
            elevation = dp(8f)
            minHeight = dpi(56)
            contentDescription = "Log an amount"
        }
        root.addView(fab, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dpi(56), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dpi(20), dpi(24))
        })
        render()
        return root
    }

    // ---- Building blocks ----------------------------------------------------------------------

    private fun whatIfStrip(): LinearLayout {
        val strip = Ui.horizontal(context).apply {
            setPadding(dpi(12), dpi(4), dpi(4), dpi(4))
            background = Ui.rounded(Color.WHITE.withAlpha(0.18f), dp(16f))
            visibility = View.GONE
        }
        strip.addView(Ui.tag(context, "What-if", palette.gradient.last(), Color.WHITE))
        strip.addView(
            Ui.text(context, 13f, Color.WHITE, value = "Exploring a different pace. Nothing is saved."),
            Ui.weight().apply { marginStart = dpi(10) },
        )
        strip.addView(Ui.button(context, palette, "Reset", ButtonStyle.TEXT, accent = Color.WHITE) { setWhatIf(null) })
        return strip
    }

    /** Full-width status gradient: ring gauge, title, chip, headline and the "not crossed yet" box. */
    private fun heroCard(): LinearLayout {
        heroDrawable = HeroDrawable(palette.statusGradient(Status.SAFE), dp(34f))
        hero = Ui.vertical(context).apply {
            setPadding(dpi(20), dpi(4), dpi(20), dpi(60))
            background = heroDrawable
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        whatIfStrip = whatIfStrip()
        hero.addView(whatIfStrip, Ui.matchWrap().apply { bottomMargin = dpi(12) })
        val top = Ui.horizontal(context)
        ring = RingGaugeView(context, sizeDp = 118, strokeDp = 10f)
        top.addView(ring, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(18) })
        val texts = Ui.vertical(context)
        heroTitle = Ui.text(context, 24f, Color.WHITE, bold = true).apply { markAsHeading() }
        texts.addView(heroTitle)
        heroChip = StatusChipView(context, palette, onGradient = true)
        texts.addView(heroChip, Ui.wrap().apply { topMargin = dpi(8); marginStart = -dpi(5) })
        top.addView(texts, Ui.weight())
        hero.addView(top)
        heroHeadline = Ui.text(context, 16f, Color.WHITE.withAlpha(0.95f))
        hero.addView(heroHeadline, Ui.matchWrap().apply { topMargin = dpi(16) })
        heroGlass = Ui.horizontal(context).apply {
            setPadding(dpi(14), dpi(10), dpi(14), dpi(10))
            background = Ui.rounded(Color.WHITE.withAlpha(0.16f), dp(16f), Color.WHITE.withAlpha(0.28f), dpi(1))
        }
        heroIcon = ImageView(context)
        heroGlass.addView(heroIcon, LinearLayout.LayoutParams(dpi(20), dpi(20)).apply { marginEnd = dpi(10) })
        heroSubline = Ui.text(context, 14f, Color.WHITE, bold = true)
        heroGlass.addView(heroSubline, Ui.weight())
        hero.addView(heroGlass, Ui.matchWrap().apply { topMargin = dpi(12) })
        heroNote = Ui.text(context, 12f, Color.WHITE.withAlpha(0.75f))
        hero.addView(heroNote, Ui.matchWrap().apply { topMargin = dpi(10) })
        return hero
    }

    private fun chartCard(): LinearLayout {
        val card = Ui.card(context, palette, padding = 14)
        card.addView(Ui.sectionHeader(context, palette, "Forecast", R.drawable.ic_trending))
        chart = ForecastChartView(context, palette)
        card.addView(chart, Ui.matchWrap().apply { topMargin = dpi(4) })
        val legend = Ui.horizontal(context).apply { setPadding(dpi(4), dpi(6), dpi(4), 0) }
        actualLegend = legendItem("Actual", palette.status(Status.SAFE).main, dashed = false)
        legend.addView(actualLegend)
        paceLegend = legendItem("Your pace", palette.onSurfaceMuted, dashed = true)
        legend.addView(paceLegend)
        legend.addView(legendItem("Needed", palette.status(Status.SAFE).main, dashed = true))
        legend.addView(legendItem("Limit", palette.status(Status.LAST_EXIT).main, dashed = true))
        card.addView(HorizontalLegend(legend))
        return card
    }

    @Suppress("FunctionName")
    private fun HorizontalLegend(row: LinearLayout): View =
        android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }

    private fun legendItem(label: String, color: Int, dashed: Boolean): LinearLayout {
        val row = Ui.horizontal(context).apply { setPadding(0, 0, dpi(14), 0) }
        row.addView(View(context).apply { background = swatch(color, dashed) },
            LinearLayout.LayoutParams(dpi(16), dpi(4)).apply { marginEnd = dpi(6) })
        row.addView(Ui.text(context, 12f, palette.onSurfaceMuted, value = label))
        return row
    }

    private fun swatch(color: Int, dashed: Boolean) = GradientDrawable().apply {
        setColor(if (dashed) color.withAlpha(0.35f) else color)
        cornerRadius = dp(2f)
        if (dashed) setStroke(dpi(2), color, dp(4f), dp(3f))
    }

    private fun whatIfCard(): LinearLayout {
        val card = Ui.card(context, palette, padding = 18)
        card.addView(Ui.sectionHeader(context, palette, "What if?", R.drawable.ic_bolt))
        card.addView(Ui.text(context, 14f, palette.onSurfaceMuted, value = "Drag to try a different daily pace. The forecast updates live; nothing is saved."))
        whatIfSeek = SeekBar(context).apply {
            max = SEEK_STEPS
            minimumHeight = dpi(48)
            contentDescription = "What-if daily pace"
            progressTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            thumbTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val before = lastStatus
                    whatIfPace = whatIfMax * progress / SEEK_STEPS
                    render()
                    if (before != null && before != lastStatus) Haptics.tick(bar)
                }

                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }
        card.addView(whatIfSeek, Ui.matchWrap().apply { topMargin = dpi(8) })
        val row = Ui.horizontal(context)
        whatIfValue = Ui.text(context, 15f, palette.onSurface, bold = true)
        row.addView(whatIfValue, Ui.weight())
        whatIfReset = Ui.button(context, palette, "My real pace", ButtonStyle.TEXT) { setWhatIf(null) }
        row.addView(whatIfReset)
        card.addView(row)
        return card
    }

    private fun recoveryCard(): LinearLayout {
        val card = Ui.card(context, palette, padding = 18)
        recoveryHeader = Ui.sectionHeader(context, palette, "How to get back under", R.drawable.ic_exit)
        card.addView(recoveryHeader)
        recoveryList = Ui.vertical(context)
        card.addView(recoveryList)
        return card
    }

    private fun cutBackCard(): LinearLayout {
        val card = Ui.card(context, palette, padding = 18)
        card.addView(Ui.sectionHeader(context, palette, "How hard can you cut back?", R.drawable.ic_risk))
        card.addView(Ui.text(
            context, 14f, palette.onSurfaceMuted,
            value = "Be honest: this is the lowest pace you could really hold. The last exit is computed from it.",
        ))
        cutSeek = SeekBar(context).apply {
            max = CUT_STEPS
            minimumHeight = dpi(48)
            contentDescription = "How much you could cut back"
            progressTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            thumbTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    draggingCut = factorFor(progress)
                    render()
                }

                override fun onStartTrackingTouch(bar: SeekBar) {}

                override fun onStopTrackingTouch(bar: SeekBar) {
                    val factor = draggingCut ?: return
                    draggingCut = null
                    graph.store.get(trackerId)?.let { graph.store.updateTracker(it.tracker.copy(minPaceFactor = factor)) }
                }
            })
        }
        card.addView(cutSeek, Ui.matchWrap().apply { topMargin = dpi(8) })
        cutValue = Ui.text(context, 15f, palette.onSurface, bold = true)
        card.addView(cutValue)
        return card
    }

    private fun historyCard(): LinearLayout {
        val card = Ui.card(context, palette, padding = 18)
        val header = Ui.sectionHeader(context, palette, "History", R.drawable.ic_event)
        historyTitle = header.getChildAt(1) as TextView
        card.addView(header)
        card.addView(Ui.text(context, 13f, palette.onSurfaceMuted, value = "Swipe an entry left or right to delete it."))
        historyList = Ui.vertical(context)
        card.addView(historyList, Ui.matchWrap().apply { topMargin = dpi(8) })
        return card
    }

    // ---- Rendering ----------------------------------------------------------------------------

    override fun onDataChanged() = render()

    private fun render() {
        if (!graph.store.isLoaded) return
        val record = graph.store.get(trackerId)
        if (record == null) {
            if (hasRendered) host.pop() else graph.main.post { host.pop() }
            return
        }
        val today = graph.today()
        val real = TrackerSnapshot.of(record, today, minPaceFactor = draggingCut ?: record.tracker.minPaceFactor)
        val snapshot = whatIfPace?.let {
            TrackerSnapshot.of(record, today, paceOverride = it, minPaceFactor = draggingCut ?: record.tracker.minPaceFactor)
        } ?: real
        val f = snapshot.forecast
        titleView.text = record.tracker.name
        renderHero(snapshot)
        chart.bind(snapshot.series, snapshot.format, record.tracker.startDate, today, animate = !hasRendered)
        chart.contentDescription = chartDescription(snapshot)
        paceLegend.getChildAt(0).background = swatch(palette.status(f.status).main, dashed = true)
        actualLegend.getChildAt(0).background = swatch(palette.status(f.status).main, dashed = false)
        renderWhatIf(real, snapshot)
        renderNumbers(snapshot)
        renderRecovery(snapshot)
        renderCutBack(record, real.forecast)
        renderHistory(record)
        lastStatus = f.status
        hasRendered = true
        restoreSheet()
    }

    private fun renderHero(snapshot: TrackerSnapshot) {
        val f = snapshot.forecast
        animateHeroColor(palette.statusGradient(f.status))
        heroIcon.setImageDrawable(context.icon(statusIcon(f.status), Color.WHITE))
        heroIcon.contentDescription = null
        val (value, caption, fraction) = ringContent(f)
        ring.bind(fraction, intArrayOf(Color.WHITE.withAlpha(0.65f), Color.WHITE), Color.WHITE.withAlpha(0.22f), value, caption, animate = hasRendered)
        heroTitle.text = snapshot.messages.title(f)
        heroHeadline.text = snapshot.messages.headline(f)
        heroSubline.text = snapshot.messages.subline(f)
        heroNote.text = when {
            f.isWhatIf -> "What-if at ${snapshot.format.pace(f.pace)}"
            f.isEarlyEstimate && f.entryCount > 0 -> "Early estimate: based on ${DateText.days(f.elapsedDays)} of data"
            else -> "Pace weighted 70% on the last 7 days"
        }
        heroChip.bind(f.status, snapshot.messages.chipLabel(f), snapshot.messages.spokenStatus(f), animate = hasRendered)
        hero.contentDescription = "${heroTitle.text}. ${heroHeadline.text} ${heroSubline.text}"
    }

    /** Ring: runway left before the last exit when one is coming, otherwise the share of the limit used. */
    private fun ringContent(f: Forecast): Triple<String, String, Float> {
        val used = "${(f.usedFraction * 100).roundToInt()}%"
        val days = f.daysToLastExit
        return when {
            (f.status == Status.ACT_SOON || f.status == Status.LAST_EXIT) && days != null -> Triple(
                days.toString(),
                when (days) {
                    0 -> "exit today"
                    1 -> "day to exit"
                    else -> "days to exit"
                },
                if (f.remainingDays > 0) days.toFloat() / f.remainingDays else 0f,
            )
            f.status == Status.PAST_THE_LINE -> Triple(used, "of limit", f.usedFraction.toFloat())
            else -> Triple(used, "used", f.usedFraction.toFloat())
        }
    }

    /** Cross-fades the hero (and top bar, status bar) from one status gradient to another. */
    private fun animateHeroColor(target: IntArray) {
        val from = heroColors
        fun apply(colors: IntArray) {
            heroColors = colors
            heroDrawable.colors = colors
            topBarView.setBackgroundColor(colors[0])
            host.refreshSystemBars()
        }
        if (from == null || !hasRendered || !ValueAnimator.areAnimatorsEnabled()) {
            apply(target)
            return
        }
        if (from.contentEquals(target)) return
        heroAnimator?.cancel()
        heroAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            addUpdateListener { apply(Gradients.mix(from, target, it.animatedFraction)) }
            start()
        }
    }

    private fun renderWhatIf(real: TrackerSnapshot, shown: TrackerSnapshot) {
        val measured = real.forecast.pace
        val required = real.forecast.requiredPace ?: 0.0
        if (whatIfPace == null || !whatIfMaxReady) {
            // Range: up to double the real pace (or 1.5x the needed pace), and always past the current choice.
            whatIfMax = max(max(max(measured * 2.0, required * 1.5), 1.0), (whatIfPace ?: 0.0) * 1.2)
            whatIfMaxReady = true
        }
        val current = whatIfPace ?: measured
        whatIfSeek.progress = ((current / whatIfMax) * SEEK_STEPS).roundToInt().coerceIn(0, SEEK_STEPS)
        val f = shown.forecast
        whatIfValue.text = "At ${shown.format.pace(current)}: finish at ${shown.format.amount(f.projectedTotal)} · ${f.status.label}"
        whatIfValue.setTextColor(palette.status(f.status).main.let { if (palette.isDark) it else palette.onSurface })
        whatIfReset.visibility = if (whatIfPace != null) View.VISIBLE else View.INVISIBLE
        whatIfStrip.visibility = if (whatIfPace != null) View.VISIBLE else View.GONE
        whatIfSeek.isEnabled = !f.hasEnded && f.hasStarted
    }

    private fun setWhatIf(pace: Double?) {
        whatIfPace = pace
        render()
    }

    private fun renderNumbers(snapshot: TrackerSnapshot) {
        val f = snapshot.forecast
        val fmt = snapshot.format
        numbers.removeAllViews()
        numbers.addView(Ui.sectionHeader(context, palette, "Key numbers", R.drawable.ic_flag))
        val items = listOf(
            "Used" to fmt.amount(f.used),
            "Left" to fmt.amount(f.remaining),
            "Your pace" to fmt.pace(f.pace),
            "Needed pace" to (f.requiredPace?.let { fmt.pace(it) } ?: "—"),
            "Projected" to fmt.amount(f.projectedTotal),
            "Limit" to fmt.amount(f.limit),
            "Last exit" to lastExitText(f),
            "Limit hit" to (f.limitHitDate?.let { DateText.short(it, f.today) } ?: if (f.isOverLimit) "Crossed" else "Not by deadline"),
            "Days left" to f.remainingDays.toString(),
            "Lowest pace" to fmt.pace(f.minPace),
        )
        val gradient = palette.statusGradient(f.status)
        items.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = Ui.horizontal(context, Gravity.TOP)
            pair.forEachIndexed { i, (label, value) ->
                // The last-exit tile is the headline number, so it wears the status gradient.
                val highlight = label == "Last exit" && f.status != Status.SAFE
                val cell = Ui.vertical(context).apply {
                    setPadding(dpi(12), dpi(10), dpi(10), dpi(10))
                    background = if (highlight) Gradients.diagonal(intArrayOf(gradient[0], gradient[1]), dp(16f)) else Ui.rounded(palette.surfaceVariant, dp(16f))
                }
                cell.addView(Ui.text(context, 11f, if (highlight) Color.WHITE.withAlpha(0.85f) else palette.onSurfaceMuted, bold = true, value = label.uppercase()).apply { letterSpacing = 0.08f })
                cell.addView(Ui.number(context, 19f, if (highlight) Color.WHITE else palette.onSurface).apply { text = value }, Ui.matchWrap().apply { topMargin = dpi(2) })
                cell.contentDescription = "$label: $value"
                row.addView(cell, Ui.weight().apply { if (i == 0) marginEnd = dpi(8) })
            }
            numbers.addView(row, Ui.matchWrap().apply { if (rowIndex > 0) topMargin = dpi(8) })
        }
    }

    private fun lastExitText(f: Forecast): String = when {
        f.status == Status.SAFE -> "Not needed"
        f.lastExitDate != null && f.daysToLastExit == 0 -> "Today"
        f.lastExitDate != null -> DateText.short(f.lastExitDate!!, f.today)
        else -> "Passed"
    }

    private fun renderRecovery(snapshot: TrackerSnapshot) {
        val steps = snapshot.messages.recoverySteps(snapshot.forecast)
        recoveryCard.visibility = if (steps.isEmpty()) View.GONE else View.VISIBLE
        recoveryList.removeAllViews()
        val gradient = palette.statusGradient(snapshot.forecast.status)
        (recoveryHeader.getChildAt(0) as ImageView).background = Gradients.diagonal(intArrayOf(gradient[0], gradient[1]), dp(10f))
        steps.forEachIndexed { index, step ->
            val row = Ui.horizontal(context, Gravity.TOP).apply { setPadding(0, dpi(6), 0, dpi(6)) }
            row.addView(Ui.text(context, 14f, Color.WHITE, bold = true, value = "${index + 1}").apply {
                gravity = Gravity.CENTER
                background = Gradients.oval(intArrayOf(gradient[0], gradient[1]))
            }, LinearLayout.LayoutParams(dpi(28), dpi(28)).apply { marginEnd = dpi(12) })
            row.addView(Ui.text(context, 15f, palette.onSurface, value = step), Ui.weight())
            recoveryList.addView(row)
        }
    }

    private fun renderCutBack(record: TrackerRecord, f: Forecast) {
        val factor = draggingCut ?: record.tracker.minPaceFactor
        if (draggingCut == null) cutSeek.progress = progressFor(factor)
        val cutPercent = ((1 - factor) * 100).roundToInt()
        val fmt = TrackerSnapshot.of(record, f.today).format
        cutValue.text = "I could cut back by up to $cutPercent% (to about ${fmt.pace(f.minPace)})"
    }

    private fun renderHistory(record: TrackerRecord) {
        if (renderedEntries === record.entries) return
        renderedEntries = record.entries
        historyTitle.text = "History (${record.entries.size})"
        historyList.removeAllViews()
        if (record.entries.isEmpty()) {
            historyList.addView(Ui.text(context, 15f, palette.onSurfaceMuted, value = "No entries yet. Tap Log to add the first one."))
            return
        }
        val fmt = TrackerSnapshot.of(record, graph.today()).format
        val today = graph.today()
        record.entries.forEach { entry ->
            val rowContent = Ui.horizontal(context).apply {
                setPadding(dpi(12), dpi(10), dpi(12), dpi(10))
                background = Ui.rounded(palette.surfaceVariant, dp(16f))
                minimumHeight = dpi(56)
            }
            val texts = Ui.vertical(context)
            texts.addView(Ui.text(context, 15f, palette.onSurface, bold = true, value = DateText.relative(entry.date, today)))
            val subtitle = listOfNotNull(entry.note.ifBlank { null }, if (entry.oneOff) "One-off" else null).joinToString(" · ")
            if (subtitle.isNotEmpty()) texts.addView(Ui.text(context, 13f, palette.onSurfaceMuted, value = subtitle, maxLines = 1))
            rowContent.addView(texts, Ui.weight())
            rowContent.addView(Ui.number(context, 17f, palette.onSurface).apply { text = fmt.amount(entry.amount) })
            rowContent.contentDescription = "${DateText.relative(entry.date, today)}, ${fmt.amount(entry.amount)}" +
                (if (entry.note.isNotBlank()) ", ${entry.note}" else "") + (if (entry.oneOff) ", one-off" else "")
            val danger = palette.status(Status.LAST_EXIT)
            val row = SwipeToDeleteLayout(context, rowContent, danger.main, danger.onMain) { deleteEntry(entry, fmt.amount(entry.amount)) }
            historyList.addView(row, Ui.matchWrap().apply { topMargin = dpi(8) })
        }
    }

    private fun deleteEntry(entry: Entry, label: String) {
        val removed = graph.store.deleteEntry(entry.id) ?: return
        host.showSnackbar("Deleted $label", "Undo") { graph.store.addEntry(removed) }
    }

    private fun chartDescription(snapshot: TrackerSnapshot): String {
        val f = snapshot.forecast
        val fmt = snapshot.format
        val exit = f.lastExitDate?.let { " Last exit ${DateText.short(it, f.today)}." } ?: ""
        return "Forecast chart. Used ${fmt.amount(f.used)} of ${fmt.amount(f.limit)} after ${DateText.days(f.elapsedDays)}. " +
            "At the current pace you reach ${fmt.amount(f.projectedTotal)} by ${DateText.short(f.deadline, f.today)}.$exit"
    }

    // ---- Actions ------------------------------------------------------------------------------

    private fun showMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add(0, MENU_EDIT, 0, "Edit limit")
            menu.add(0, MENU_DELETE, 1, "Delete limit")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> host.push(Routes.edit(trackerId))
                    MENU_DELETE -> confirmDelete()
                }
                true
            }
            show()
        }
    }

    private fun confirmDelete() {
        val name = graph.store.get(trackerId)?.tracker?.name ?: return
        AlertDialog.Builder(context)
            .setTitle("Delete \"$name\"?")
            .setMessage("This removes the limit and all of its entries. It can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                graph.notifier.cancelTracker(trackerId)
                graph.store.deleteTracker(trackerId)
                host.showSnackbar("Deleted $name")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openSheet(state: Bundle?) {
        val record = graph.store.get(trackerId) ?: return
        sheet?.dismiss()
        sheet = QuickLogSheet(context, palette, record, graph.today(), state) { entry ->
            graph.store.addEntry(entry.copy(id = graph.store.newEntryId()))
        }.also {
            it.setOnDismissListener { _ -> if (sheet === it) sheet = null }
            it.show()
        }
    }

    private fun restoreSheet() {
        val state = pendingSheet ?: return
        pendingSheet = null
        openSheet(state)
    }

    override fun onSaveState(out: Bundle) {
        whatIfPace?.let { out.putDouble(KEY_WHAT_IF, it) }
        sheet?.takeIf { it.isShowing }?.let { out.putBundle(KEY_SHEET, it.saveState()) }
    }

    override fun onDestroy() {
        heroAnimator?.cancel()
        sheet?.dismiss()
        sheet = null
    }

    private companion object {
        const val KEY_WHAT_IF = "what_if_pace"
        const val KEY_SHEET = "quick_log_sheet"
        const val SEEK_STEPS = 1000
        const val CUT_STEPS = 18 // 10%..100% in 5% steps
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2

        /** Slider position -> min pace factor: "cut back by 10%" is factor 0.9, "by 100%" is 0.0. */
        fun factorFor(progress: Int): Double = 1.0 - (10 + 5 * progress) / 100.0

        fun progressFor(factor: Double): Int = (((1.0 - factor) * 100 - 10) / 5).roundToInt().coerceIn(0, CUT_STEPS)
    }
}
