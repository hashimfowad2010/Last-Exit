package com.lastexit.app.ui.demo

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.lastexit.app.R
import com.lastexit.app.data.Entry
import com.lastexit.app.data.Tracker
import com.lastexit.app.data.TrackerRecord
import com.lastexit.app.data.TrackerSnapshot
import com.lastexit.app.ui.Screen
import com.lastexit.app.ui.ScreenHost
import com.lastexit.app.ui.TopBar
import com.lastexit.app.ui.kit.AlertBannerView
import com.lastexit.app.ui.kit.ButtonStyle
import com.lastexit.app.ui.kit.ForecastChartView
import com.lastexit.app.ui.kit.Gradients
import com.lastexit.app.ui.kit.HeroDrawable
import com.lastexit.app.ui.kit.Palette
import com.lastexit.app.ui.kit.RoadProgressView
import com.lastexit.app.ui.kit.withAlpha
import com.lastexit.app.ui.kit.Haptics
import com.lastexit.app.ui.kit.LimitProgressView
import com.lastexit.app.ui.kit.SegmentedControl
import com.lastexit.app.ui.kit.TrackerCardView
import com.lastexit.app.ui.kit.Ui
import com.lastexit.app.ui.kit.dp
import com.lastexit.app.ui.kit.dpi
import com.lastexit.app.ui.kit.icon
import com.lastexit.app.ui.kit.markAsHeading
import com.lastexit.core.DateText
import com.lastexit.core.DemoScenario
import com.lastexit.core.Forecast
import com.lastexit.core.Status
import com.lastexit.core.TrackerType
import java.time.LocalDate

/**
 * Fast-forward through a month of believable spending. Status escalates green -> amber -> red
 * while the limit is still days away, firing an in-app banner, a haptic buzz, an animation and a
 * real notification. Demo data lives only in memory; real trackers are never touched.
 */
class DemoScreen(host: ScreenHost, route: String) : Screen(host, route) {
    private val start: LocalDate = DemoScenario.startDate(LocalDate.now())
    private var day = 1
    private var plan: DemoScenario.ExitPlan? = null
    private var playing = false
    private var speedIndex = 0
    private var autoPause = true
    private val milestones = LinkedHashMap<String, Int>()

    private lateinit var dateText: TextView
    private lateinit var dayText: TextView
    private lateinit var monthProgress: LimitProgressView
    private lateinit var road: RoadProgressView
    private lateinit var outcomeIcon: android.widget.ImageView
    private lateinit var outcomeTitle: TextView
    private lateinit var outcomeButton: TextView
    private lateinit var playButton: ImageButton
    private lateinit var speedControl: SegmentedControl
    private lateinit var banner: AlertBannerView
    private lateinit var permissionRow: LinearLayout
    private lateinit var card: TrackerCardView
    private lateinit var exitButton: TextView
    private lateinit var chart: ForecastChartView
    private lateinit var timeline: LinearLayout
    private lateinit var outcome: LinearLayout
    private lateinit var outcomeText: TextView
    private var chartBound = false

    private val tick = Runnable { onTick() }

    /** "Night drive": deep navy at the top fading into the theme's darkest brand colour. */
    private val night: IntArray by lazy {
        intArrayOf(0xFF0B1224.toInt(), Palette.blend(0xFF0B1224.toInt(), palette.gradient[2], 0.55f), palette.gradient[2])
    }

    override fun statusBarColor(): Int = night[0]

    override fun statusBarDarkIcons(): Boolean = false

    override fun onCreateView(saved: Bundle?): View {
        saved?.let { restore(it) }
        val column = Ui.vertical(context)
        column.addView(controlPanel())
        banner = AlertBannerView(context, palette)
        column.addView(banner, Ui.matchWrap().apply { setMargins(dpi(16), dpi(12), dpi(16), 0) })

        val scroll = ScrollView(context)
        val content = Ui.vertical(context).apply {
            setPadding(dpi(16), dpi(14), dpi(16), dpi(32))
            clipToPadding = false
        }
        permissionRow = permissionPrompt()
        content.addView(permissionRow, Ui.matchWrap().apply { bottomMargin = dpi(12) })
        content.addView(Ui.text(
            context, 14f, palette.onSurfaceMuted,
            value = "A believable month against a $1,500 budget. Press play and watch Last Exit turn amber, then red, " +
                "days before the limit is actually crossed.",
        ), Ui.matchWrap().apply { bottomMargin = dpi(12) })
        card = TrackerCardView(context, palette, showLogButton = false, onOpen = { }, onLog = { })
        content.addView(card)
        val exitGradient = palette.statusGradient(Status.LAST_EXIT)
        exitButton = Ui.button(context, palette, "Take the exit", ButtonStyle.FILLED, R.drawable.ic_exit,
            accent = palette.status(Status.LAST_EXIT).main, gradient = intArrayOf(exitGradient[0], exitGradient[1])) { takeExit() }
        content.addView(exitButton, Ui.wrap().apply { topMargin = dpi(14); gravity = Gravity.CENTER_HORIZONTAL })
        val chartCard = Ui.card(context, palette, padding = 14)
        chartCard.addView(Ui.sectionHeader(context, palette, "Forecast", R.drawable.ic_trending))
        chart = ForecastChartView(context, palette)
        chartCard.addView(chart)
        content.addView(chartCard, Ui.matchWrap().apply { topMargin = dpi(12) })
        content.addView(timelineCard(), Ui.matchWrap().apply { topMargin = dpi(12) })
        outcome = Ui.vertical(context, padding = 20).apply { visibility = View.GONE }
        val outcomeTop = Ui.horizontal(context)
        outcomeIcon = android.widget.ImageView(context).apply {
            setPadding(dpi(8), dpi(8), dpi(8), dpi(8))
            background = Gradients.oval(intArrayOf(Color.WHITE.withAlpha(0.3f), Color.WHITE.withAlpha(0.12f)))
        }
        outcomeTop.addView(outcomeIcon, LinearLayout.LayoutParams(dpi(40), dpi(40)).apply { marginEnd = dpi(12) })
        val titles = Ui.vertical(context)
        titles.addView(Ui.text(context, 12f, Color.WHITE.withAlpha(0.78f), bold = true, value = "How the month ended"))
        outcomeTitle = Ui.text(context, 20f, Color.WHITE, bold = true).apply { markAsHeading() }
        titles.addView(outcomeTitle)
        outcomeTop.addView(titles, Ui.weight())
        outcome.addView(outcomeTop)
        outcomeText = Ui.text(context, 15f, Color.WHITE.withAlpha(0.95f))
        outcome.addView(outcomeText, Ui.matchWrap().apply { topMargin = dpi(10) })
        outcomeButton = Ui.button(context, palette, "Run it again", ButtonStyle.FILLED, R.drawable.ic_replay,
            accent = Color.WHITE, onAccent = palette.gradient[2], gradient = null) { restart() }.apply { elevation = 0f }
        outcome.addView(outcomeButton, Ui.wrap().apply { topMargin = dpi(14) })
        content.addView(outcome, Ui.matchWrap().apply { topMargin = dpi(12) })
        scroll.addView(content)
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        render(animate = false)
        return column
    }

    /** The cockpit: simulated clock, play, the month as a road, speed and auto-pause. */
    private fun controlPanel(): View {
        val panel = Ui.vertical(context).apply {
            background = HeroDrawable(night, dp(32f))
            setPadding(0, 0, dpi(16), dpi(14))
        }
        val simulated = Ui.tag(context, "Simulated", Color.WHITE, Color.WHITE.withAlpha(0.18f))
        panel.addView(TopBar.create(context, palette, "Demo mode", { host.pop() }, simulated, onGradient = true))

        val body = Ui.vertical(context).apply { setPadding(dpi(20), 0, 0, 0) }
        val top = Ui.horizontal(context)
        val texts = Ui.vertical(context)
        texts.addView(Ui.text(context, 11f, Color.WHITE.withAlpha(0.65f), bold = true, value = "SIMULATED DATE").apply { letterSpacing = 0.14f })
        dateText = Ui.number(context, 32f, Color.WHITE).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        texts.addView(dateText)
        dayText = Ui.text(context, 14f, Color.WHITE.withAlpha(0.75f))
        texts.addView(dayText)
        top.addView(texts, Ui.weight())
        // Play: white disc with a soft halo so it reads as the primary control.
        val playFrame = android.widget.FrameLayout(context)
        playFrame.addView(View(context).apply {
            background = Gradients.oval(intArrayOf(Color.WHITE.withAlpha(0.22f), Color.WHITE.withAlpha(0.06f)))
        }, android.widget.FrameLayout.LayoutParams(dpi(84), dpi(84), Gravity.CENTER))
        playButton = Ui.iconButton(context, palette, R.drawable.ic_play, "Play", tint = palette.gradient[1],
            background = Color.WHITE, sizeDp = 64) { togglePlay() }.apply { elevation = dp(6f) }
        playFrame.addView(playButton, android.widget.FrameLayout.LayoutParams(dpi(64), dpi(64), Gravity.CENTER))
        top.addView(playFrame, LinearLayout.LayoutParams(dpi(84), dpi(84)))
        body.addView(top)

        road = RoadProgressView(context, palette)
        body.addView(road, Ui.matchWrap().apply { topMargin = dpi(4) })
        monthProgress = LimitProgressView(context, palette).apply { visibility = View.GONE }
        body.addView(monthProgress)

        val controls = Ui.horizontal(context)
        controls.addView(Ui.iconButton(context, palette, R.drawable.ic_replay, "Restart demo", tint = Color.WHITE,
            background = Color.WHITE.withAlpha(0.14f), sizeDp = 44) { restart() },
            LinearLayout.LayoutParams(dpi(44), dpi(44)).apply { marginEnd = dpi(8) })
        controls.addView(Ui.iconButton(context, palette, R.drawable.ic_skip_next, "Advance one day", tint = Color.WHITE,
            background = Color.WHITE.withAlpha(0.14f), sizeDp = 44) {
            pause()
            advance()
        }, LinearLayout.LayoutParams(dpi(44), dpi(44)))
        speedControl = SegmentedControl(context, palette, SPEED_LABELS, onGradient = true) { index ->
            speedIndex = index
            if (playing) schedule()
        }
        speedControl.select(speedIndex)
        controls.addView(speedControl, Ui.weight().apply { marginStart = dpi(12) })
        body.addView(controls, Ui.matchWrap().apply { topMargin = dpi(6) })

        body.addView(Switch(context).apply {
            text = "Pause on each warning"
            textSize = 14f
            setTextColor(Color.WHITE.withAlpha(0.9f))
            minHeight = dpi(48)
            isChecked = autoPause
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, Color.WHITE.withAlpha(0.8f)),
            )
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.gradient[0], Color.WHITE.withAlpha(0.3f)),
            )
            setOnCheckedChangeListener { _, checked -> autoPause = checked }
        })
        panel.addView(body)
        return panel
    }

    private fun permissionPrompt(): LinearLayout {
        val row = Ui.horizontal(context).apply {
            setPadding(dpi(14), dpi(8), dpi(8), dpi(8))
            background = Ui.rounded(palette.primaryContainer, dp(18f))
        }
        row.addView(Ui.text(context, 14f, palette.onPrimaryContainer,
            value = "Allow notifications to see the real alert fire during the demo."), Ui.weight())
        row.addView(Ui.button(context, palette, "Allow", ButtonStyle.FILLED) {
            if (host.isNotificationPermissionBlocked()) host.openNotificationSettings()
            else host.requestNotifications { updatePermissionRow() }
        })
        return row
    }

    private fun timelineCard(): View {
        val box = Ui.card(context, palette, padding = 18)
        box.addView(Ui.sectionHeader(context, palette, "Timeline", R.drawable.ic_flag))
        timeline = Ui.vertical(context)
        box.addView(timeline, Ui.matchWrap().apply { topMargin = dpi(4) })
        return box
    }

    // ---- Simulation ---------------------------------------------------------------------------

    private fun forecastFor(d: Int): Forecast = DemoScenario.forecast(start, d, plan)

    private fun snapshot(): TrackerSnapshot {
        val tracker = Tracker(
            id = -1L,
            name = DemoScenario.NAME,
            type = TrackerType.MONEY,
            unit = DemoScenario.UNIT,
            limit = DemoScenario.LIMIT,
            startDate = start,
            deadline = DemoScenario.deadline(start),
            minPaceFactor = DemoScenario.MIN_PACE_FACTOR,
        )
        val entries = DemoScenario.entries(start, day, plan).map {
            Entry(id = it.day.toLong(), trackerId = -1L, date = it.date, amount = it.amount, note = it.note, oneOff = false)
        }
        val input = DemoScenario.input(start, day, plan)
        return TrackerSnapshot(TrackerRecord(tracker, entries), input, com.lastexit.core.ForecastEngine.forecast(input))
    }

    private fun togglePlay() = if (playing) pause() else play()

    private fun play() {
        if (day >= DemoScenario.DAYS) restart()
        playing = true
        updatePlayButton()
        schedule()
    }

    private fun pause() {
        playing = false
        graph.main.removeCallbacks(tick)
        updatePlayButton()
    }

    private fun schedule() {
        graph.main.removeCallbacks(tick)
        graph.main.postDelayed(tick, MS_PER_DAY / SPEEDS[speedIndex])
    }

    private fun onTick() {
        if (!playing) return
        advance()
        if (playing && day < DemoScenario.DAYS) schedule()
    }

    /** Moves the simulated clock forward one day and reacts to anything that changed. */
    private fun advance() {
        if (day >= DemoScenario.DAYS) {
            pause()
            return
        }
        val before = forecastFor(day)
        day += 1
        val after = forecastFor(day)
        val snapshot = snapshot()
        render(animate = true)

        if (after.status.isWorseThan(before.status)) {
            milestones.getOrPut(after.status.name) { day }
            onWorsened(snapshot, after)
        } else if (before.status != Status.SAFE && after.status == Status.SAFE && plan != null) {
            milestones.getOrPut(KEY_RECOVERED) { day }
            banner.show(Status.SAFE, "Back on track", "The exit worked: projected ${snapshot.format.amount(after.projectedTotal)} of ${snapshot.format.amount(after.limit)}.")
            Haptics.statusWorsened(context, Status.SAFE, view)
            renderTimeline()
        }
        if (!before.isOverLimit && after.isOverLimit) {
            milestones.getOrPut(KEY_CROSSED) { day }
            onCrossed()
        }
        if (day >= DemoScenario.DAYS) {
            pause()
            renderOutcome()
        }
    }

    private fun onWorsened(snapshot: TrackerSnapshot, f: Forecast) {
        val messages = snapshot.messages
        val left = if (!f.isOverLimit) " The limit isn't crossed yet: ${snapshot.format.amount(f.remaining)} left." else ""
        val canExit = plan == null && (f.status == Status.ACT_SOON || f.status == Status.LAST_EXIT)
        banner.show(
            status = f.status,
            heading = messages.title(f),
            message = messages.headline(f) + left,
            actionLabel = if (canExit) "Take the exit" else null,
            onAction = if (canExit) { { takeExit() } } else null,
        )
        Haptics.statusWorsened(context, f.status, view)
        graph.notifier.postDemoAlert(DemoScenario.NAME, f, messages)
        renderTimeline()
        if (autoPause) pause()
    }

    private fun onCrossed() {
        val warnedDay = milestones[Status.LAST_EXIT.name] ?: milestones[Status.ACT_SOON.name]
        val lead = warnedDay?.let { day - it }
        val message = if (lead != null && lead > 0) {
            "Last Exit warned you $lead days earlier, on ${DateText.short(DemoScenario.dateOf(start, warnedDay), DemoScenario.dateOf(start, day))}."
        } else {
            "Every unit from here adds to the overage."
        }
        banner.show(Status.PAST_THE_LINE, "Limit crossed on ${DateText.short(DemoScenario.dateOf(start, day), DemoScenario.dateOf(start, day))}", message)
        Haptics.statusWorsened(context, Status.PAST_THE_LINE, view)
        renderTimeline()
        if (autoPause) pause()
    }

    private fun takeExit() {
        if (plan != null) return
        plan = DemoScenario.exitPlan(start, day)
        milestones[KEY_EXIT_TAKEN] = day
        val fmt = snapshot().format
        banner.show(
            Status.SAFE,
            "Exit taken",
            "From tomorrow, spending is held to ${fmt.pace(plan!!.dailyPace)}. Press play to see if it was in time.",
        )
        render(animate = true)
        renderTimeline()
    }

    private fun restart() {
        pause()
        day = 1
        plan = null
        milestones.clear()
        chartBound = false
        banner.hide()
        graph.notifier.cancelDemo()
        render(animate = false)
    }

    // ---- Rendering ----------------------------------------------------------------------------

    private fun render(animate: Boolean) {
        val snapshot = snapshot()
        val f = snapshot.forecast
        val date = DemoScenario.dateOf(start, day)
        dateText.text = DateText.withWeekday(date)
        dayText.text = "Day $day of ${DemoScenario.DAYS}"
        monthProgress.bind(day.toDouble() / DemoScenario.DAYS, day.toDouble() / DemoScenario.DAYS, Status.SAFE, animate)
        monthProgress.contentDescription = "Day $day of ${DemoScenario.DAYS}"
        val hitDay = milestones[KEY_CROSSED] ?: f.limitHitDate?.let { day + com.lastexit.core.ForecastEngine.periodDays(date, it) - 1 }
        road.bind(DemoScenario.DAYS, day, f.daysToLastExit?.let { day + it }, hitDay, f.status, animate)
        card.bind(snapshot, animate)
        chart.bind(snapshot.series, snapshot.format, start, date, animate = !chartBound)
        chartBound = true
        chart.contentDescription = "Forecast chart for the demo month. ${snapshot.messages.headline(f)}"
        exitButton.visibility = if (plan == null && (f.status == Status.ACT_SOON || f.status == Status.LAST_EXIT)) View.VISIBLE else View.GONE
        if (plan == null && f.status != Status.SAFE) {
            val pace = DemoScenario.exitPlan(start, day).dailyPace
            exitButton.text = "Take the exit: hold to ${snapshot.format.pace(pace)}"
        }
        updatePlayButton()
        updatePermissionRow()
        renderTimeline()
        if (day >= DemoScenario.DAYS) renderOutcome() else outcome.visibility = View.GONE
    }

    private fun updatePlayButton() {
        if (!::playButton.isInitialized) return
        playButton.setImageDrawable(context.icon(if (playing) R.drawable.ic_pause else R.drawable.ic_play, palette.gradient[1]))
        playButton.contentDescription = if (playing) "Pause" else "Play"
    }

    private fun updatePermissionRow() {
        permissionRow.visibility = if (graph.notifier.canPost()) View.GONE else View.VISIBLE
    }

    private fun renderTimeline() {
        timeline.removeAllViews()
        val rows = ArrayList<Triple<Int, Status, String>>()
        milestones[Status.ACT_SOON.name]?.let { rows += Triple(it, Status.ACT_SOON, "Amber warning: act soon") }
        milestones[Status.LAST_EXIT.name]?.let { rows += Triple(it, Status.LAST_EXIT, "Red warning: last exit") }
        milestones[KEY_EXIT_TAKEN]?.let { rows += Triple(it, Status.SAFE, "You took the exit") }
        milestones[KEY_RECOVERED]?.let { rows += Triple(it, Status.SAFE, "Back on track") }
        milestones[Status.PAST_THE_LINE.name]?.let { rows += Triple(it, Status.PAST_THE_LINE, "Point of no return passed") }
        milestones[KEY_CROSSED]?.let { rows += Triple(it, Status.PAST_THE_LINE, "Limit actually crossed") }
        if (rows.isEmpty()) {
            timeline.addView(Ui.text(context, 14f, palette.onSurfaceMuted, value = "Warnings will appear here as the month plays out."))
            return
        }
        val crossed = milestones[KEY_CROSSED]
        val sorted = rows.sortedBy { it.first }
        sorted.forEachIndexed { index, (d, status, label) ->
            val gradient = palette.statusGradient(status)
            val row = Ui.horizontal(context, Gravity.TOP)
            // Stepper rail: a gradient node with a connector down to the next milestone.
            val rail = Ui.vertical(context).apply { gravity = Gravity.CENTER_HORIZONTAL }
            rail.addView(View(context).apply {
                background = Gradients.oval(intArrayOf(gradient[0], gradient[1]))
                elevation = dp(2f)
            }, LinearLayout.LayoutParams(dpi(16), dpi(16)).apply { topMargin = dpi(3) })
            if (index < sorted.size - 1) {
                rail.addView(View(context).apply { setBackgroundColor(palette.outline) },
                    LinearLayout.LayoutParams(dpi(2), dpi(34)).apply { topMargin = dpi(2) })
            }
            row.addView(rail, LinearLayout.LayoutParams(dpi(16), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpi(14) })
            val texts = Ui.vertical(context).apply { setPadding(0, 0, 0, dpi(10)) }
            texts.addView(Ui.text(context, 15f, palette.onSurface, bold = true, value = label))
            val lead = if (crossed != null && d < crossed && status != Status.SAFE) " · ${crossed - d} days before the crossing" else ""
            texts.addView(Ui.text(context, 13f, palette.onSurfaceMuted,
                value = "Day $d · ${DateText.short(DemoScenario.dateOf(start, d), DemoScenario.dateOf(start, d))}$lead"))
            row.addView(texts, Ui.weight())
            timeline.addView(row)
        }
    }

    private fun renderOutcome() {
        val f = forecastFor(DemoScenario.DAYS)
        val fmt = snapshot().format
        outcome.visibility = View.VISIBLE
        val result = if (f.isOverLimit) Status.PAST_THE_LINE else Status.SAFE
        val gradient = palette.statusGradient(result)
        outcome.background = HeroDrawable(gradient, dp(26f), topRadius = dp(26f))
        outcome.elevation = dp(4f)
        Gradients.tintShadow(outcome, gradient[1])
        outcomeIcon.setImageDrawable(context.icon(if (f.isOverLimit) R.drawable.ic_stop else R.drawable.ic_check_circle, Color.WHITE))
        outcomeTitle.text = if (f.isOverLimit) "The line was crossed" else "Made it under the limit"
        (outcomeButton.compoundDrawablesRelative[0])?.setTint(gradient[2])
        outcomeButton.setTextColor(gradient[2])
        val exitDay = milestones[KEY_EXIT_TAKEN]
        outcomeText.text = if (!f.isOverLimit) {
            val exitPart = exitDay?.let { "You took the exit on day $it and " } ?: "You "
            "${exitPart}finished at ${fmt.amount(f.used)} of ${fmt.amount(f.limit)}, ${fmt.amount(f.remaining)} under."
        } else {
            val red = milestones[Status.LAST_EXIT.name]
            val crossed = milestones[KEY_CROSSED]
            val warning = if (red != null && crossed != null) {
                " The red warning came on day $red, ${crossed - red} days before the limit was crossed on day $crossed."
            } else {
                ""
            }
            "Month over: ${fmt.amount(f.used)} spent, ${fmt.amount(f.used - f.limit)} over the budget.$warning Tap restart and take the exit this time."
        }
    }

    // ---- Lifecycle ----------------------------------------------------------------------------

    override fun onShown() {
        if (!graph.notifier.canPost() && !graph.prefs.getBoolean(PREF_ASKED_IN_DEMO, false)) {
            graph.prefs.edit().putBoolean(PREF_ASKED_IN_DEMO, true).apply()
            host.requestNotifications { updatePermissionRow() }
        }
    }

    override fun onResume() {
        updatePermissionRow()
        if (playing) schedule()
    }

    override fun onPause() {
        graph.main.removeCallbacks(tick)
    }

    override fun onSaveState(out: Bundle) {
        out.putInt(KEY_DAY, day)
        out.putBoolean(KEY_PLAYING, playing)
        out.putInt(KEY_SPEED, speedIndex)
        out.putBoolean(KEY_AUTO_PAUSE, autoPause)
        plan?.let {
            out.putInt(KEY_PLAN_DAY, it.fromDay)
            out.putDouble(KEY_PLAN_PACE, it.dailyPace)
        }
        out.putStringArrayList(KEY_MILESTONE_NAMES, ArrayList(milestones.keys))
        out.putIntArray(KEY_MILESTONE_DAYS, milestones.values.toIntArray())
    }

    private fun restore(saved: Bundle) {
        day = saved.getInt(KEY_DAY, 1).coerceIn(1, DemoScenario.DAYS)
        playing = saved.getBoolean(KEY_PLAYING, false)
        speedIndex = saved.getInt(KEY_SPEED, 0).coerceIn(0, SPEEDS.size - 1)
        autoPause = saved.getBoolean(KEY_AUTO_PAUSE, true)
        if (saved.containsKey(KEY_PLAN_DAY)) {
            plan = DemoScenario.ExitPlan(saved.getInt(KEY_PLAN_DAY), saved.getDouble(KEY_PLAN_PACE))
        }
        val names = saved.getStringArrayList(KEY_MILESTONE_NAMES).orEmpty()
        val days = saved.getIntArray(KEY_MILESTONE_DAYS) ?: IntArray(0)
        names.zip(days.toList()).forEach { (name, d) -> milestones[name] = d }
    }

    override fun onDestroy() {
        graph.main.removeCallbacks(tick)
    }

    private companion object {
        const val MS_PER_DAY = 1200L
        val SPEEDS = longArrayOf(1, 5, 20)
        val SPEED_LABELS = listOf("1×", "5×", "20×")
        const val KEY_DAY = "day"
        const val KEY_PLAYING = "playing"
        const val KEY_SPEED = "speed"
        const val KEY_AUTO_PAUSE = "auto_pause"
        const val KEY_PLAN_DAY = "plan_day"
        const val KEY_PLAN_PACE = "plan_pace"
        const val KEY_MILESTONE_NAMES = "milestone_names"
        const val KEY_MILESTONE_DAYS = "milestone_days"
        const val KEY_CROSSED = "CROSSED"
        const val KEY_EXIT_TAKEN = "EXIT_TAKEN"
        const val KEY_RECOVERED = "RECOVERED"
        const val PREF_ASKED_IN_DEMO = "asked_notifications_in_demo"
    }
}
