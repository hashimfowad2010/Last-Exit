package com.lastexit.app.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lastexit.app.R
import com.lastexit.app.data.TrackerSnapshot
import com.lastexit.app.ui.Routes
import com.lastexit.app.ui.Screen
import com.lastexit.app.ui.ScreenHost
import com.lastexit.app.ui.kit.ButtonStyle
import com.lastexit.app.ui.kit.Gradients
import com.lastexit.app.ui.kit.HeroDrawable
import com.lastexit.app.ui.kit.ThemeSheet
import com.lastexit.app.ui.kit.TrackerCardView
import com.lastexit.app.ui.kit.Ui
import com.lastexit.app.ui.kit.icon
import com.lastexit.app.ui.kit.markAsHeading
import com.lastexit.app.ui.kit.typeGradient
import com.lastexit.app.ui.kit.typeIcon
import com.lastexit.app.ui.kit.withAlpha
import com.lastexit.app.ui.log.QuickLogSheet
import com.lastexit.core.Status
import com.lastexit.core.Templates

/** Gradient hero with today's outlook, then the limits (worst first), quick starts and the FAB. */
class HomeScreen(host: ScreenHost, route: String) : Screen(host, route) {
    private lateinit var list: LinearLayout
    private lateinit var emptyState: View
    private lateinit var outlookTitle: TextView
    private lateinit var outlookBody: TextView
    private lateinit var pills: LinearLayout
    private lateinit var permissionCard: LinearLayout
    private lateinit var permissionButton: TextView
    private lateinit var fab: View
    private val cards = LinkedHashMap<Long, TrackerCardView>()
    private var sheet: QuickLogSheet? = null
    private var pendingSheet: Bundle? = null

    override fun statusBarColor(): Int = palette.gradient[0]

    override fun statusBarDarkIcons(): Boolean = false

    override fun onCreateView(saved: Bundle?): View {
        pendingSheet = saved?.getBundle(KEY_SHEET)
        val root = FrameLayout(context)
        val scroll = ScrollView(context).apply { isFillViewport = true }
        val column = Ui.vertical(context)
        column.addView(hero())

        // Content slides up over the hero's rounded bottom edge.
        val content = Ui.vertical(context).apply {
            setPadding(dpi(16), 0, dpi(16), dpi(112))
            clipToPadding = false
        }
        permissionCard = permissionCard()
        content.addView(permissionCard, Ui.matchWrap().apply { bottomMargin = dpi(14) })
        list = Ui.vertical(context)
        content.addView(list)
        emptyState = emptyState()
        content.addView(emptyState)
        column.addView(content, Ui.matchWrap().apply { topMargin = -dpi(40) })
        scroll.addView(column)
        root.addView(scroll)

        fab = Ui.fab(context, palette, R.drawable.ic_add, "Add a limit") { host.push(Routes.NEW_TRACKER) }
        root.addView(fab, FrameLayout.LayoutParams(dpi(64), dpi(64), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dpi(20), dpi(24))
        })
        render()
        return root
    }

    private fun hero(): View {
        val hero = Ui.vertical(context).apply {
            setPadding(dpi(20), dpi(10), dpi(16), dpi(64))
            background = HeroDrawable(palette.gradient, dp(34f))
        }
        val top = Ui.horizontal(context)
        top.addView(ImageView(context).apply {
            setImageDrawable(context.icon(R.drawable.ic_exit, Color.WHITE))
            setPadding(dpi(8), dpi(8), dpi(8), dpi(8))
            background = Gradients.oval(intArrayOf(Color.WHITE.withAlpha(0.28f), Color.WHITE.withAlpha(0.1f)))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dpi(40), dpi(40)).apply { marginEnd = dpi(12) })
        val brand = Ui.vertical(context)
        brand.addView(Ui.text(context, 22f, Color.WHITE, bold = true, value = "Last Exit").apply { markAsHeading() })
        brand.addView(Ui.text(context, 12f, Color.WHITE.withAlpha(0.78f), value = "Warns before it's too late", maxLines = 1))
        top.addView(brand, Ui.weight())
        top.addView(Ui.iconButton(context, palette, R.drawable.ic_palette, "Theme and appearance", tint = Color.WHITE,
            background = Color.WHITE.withAlpha(0.16f), sizeDp = 44) {
            ThemeSheet(context, palette, onApply = { brand, appearance -> host.applyTheme(brand, appearance) }).show()
        }, LinearLayout.LayoutParams(dpi(44), dpi(44)).apply { marginEnd = dpi(8) })
        top.addView(Ui.button(context, palette, "Demo", ButtonStyle.FILLED, R.drawable.ic_play, accent = Color.WHITE,
            onAccent = palette.gradient[1], gradient = null) { host.push(Routes.DEMO) }.apply {
            contentDescription = "Open the 60-second demo"
            elevation = 0f
            setPadding(dpi(16), 0, dpi(18), 0)
        })
        hero.addView(top)

        hero.addView(Ui.text(context, 12f, Color.WHITE.withAlpha(0.72f), bold = true, value = "TODAY'S OUTLOOK").apply {
            letterSpacing = 0.14f
        }, Ui.matchWrap().apply { topMargin = dpi(26) })
        outlookTitle = Ui.text(context, 28f, Color.WHITE, bold = true).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        hero.addView(outlookTitle, Ui.matchWrap().apply { topMargin = dpi(4) })
        outlookBody = Ui.text(context, 15f, Color.WHITE.withAlpha(0.88f))
        hero.addView(outlookBody, Ui.matchWrap().apply { topMargin = dpi(4) })
        pills = Ui.horizontal(context)
        hero.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(pills)
        }, Ui.matchWrap().apply { topMargin = dpi(14) })
        return hero
    }

    private fun pill(status: Status, count: Int): View {
        val row = Ui.horizontal(context).apply {
            setPadding(dpi(10), dpi(6), dpi(12), dpi(6))
            background = Ui.rounded(Color.WHITE.withAlpha(0.16f), dp(16f))
        }
        row.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.statusGradient(status)[0])
                setStroke(dpi(2), Color.WHITE.withAlpha(0.8f))
            }
        }, LinearLayout.LayoutParams(dpi(12), dpi(12)).apply { marginEnd = dpi(8) })
        row.addView(Ui.text(context, 13f, Color.WHITE, bold = true, value = "$count ${status.label}"))
        row.contentDescription = "$count ${status.label}"
        return row
    }

    private fun permissionCard(): LinearLayout {
        val card = Ui.card(context, palette, padding = 16)
        val row = Ui.horizontal(context, Gravity.TOP)
        row.addView(Ui.gradientTile(context, R.drawable.ic_notifications, palette.buttonGradient, sizeDp = 36),
            LinearLayout.LayoutParams(dpi(36), dpi(36)).apply { marginEnd = dpi(12) })
        val texts = Ui.vertical(context)
        texts.addView(Ui.text(context, 16f, palette.onSurface, bold = true, value = "Get warned before it's too late"))
        texts.addView(Ui.text(
            context, 14f, palette.onSurfaceMuted,
            value = "Last Exit only notifies you when a limit moves closer to the point of no return. No daily spam.",
        ), Ui.matchWrap().apply { topMargin = dpi(2) })
        row.addView(texts, Ui.weight())
        card.addView(row)
        val buttons = Ui.horizontal(context, Gravity.END or Gravity.CENTER_VERTICAL)
        buttons.addView(Ui.button(context, palette, "Not now", ButtonStyle.TEXT, accent = palette.onSurfaceMuted) {
            graph.prefs.edit().putBoolean(PREF_DISMISSED_PERMISSION, true).apply()
            render()
        })
        permissionButton = Ui.button(context, palette, "Allow warnings") {
            if (host.isNotificationPermissionBlocked()) host.openNotificationSettings()
            else host.requestNotifications { render() }
        }
        buttons.addView(permissionButton, Ui.wrap().apply { marginStart = dpi(4) })
        card.addView(buttons, Ui.matchWrap().apply { topMargin = dpi(10) })
        return card
    }

    private fun emptyState(): View {
        val box = Ui.card(context, palette, padding = 24).apply { gravity = Gravity.CENTER_HORIZONTAL }
        // Illustration: glowing gradient disc with the exit arrow.
        val art = FrameLayout(context)
        art.addView(View(context).apply {
            background = Gradients.oval(intArrayOf(palette.gradient[0].withAlpha(0.22f), palette.gradient[1].withAlpha(0.06f)))
        }, FrameLayout.LayoutParams(dpi(132), dpi(132), Gravity.CENTER))
        art.addView(ImageView(context).apply {
            setImageDrawable(context.icon(R.drawable.ic_exit, Color.WHITE))
            setPadding(dpi(22), dpi(22), dpi(22), dpi(22))
            background = Gradients.oval(palette.buttonGradient)
            elevation = dp(6f)
            Gradients.tintShadow(this, palette.gradient.last())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dpi(92), dpi(92), Gravity.CENTER))
        box.addView(art, LinearLayout.LayoutParams(dpi(140), dpi(140)))
        box.addView(Ui.text(context, 22f, palette.onSurface, bold = true, value = "Nothing tracked yet").apply {
            gravity = Gravity.CENTER
        }, Ui.matchWrap().apply { topMargin = dpi(12) })
        box.addView(Ui.text(
            context, 15f, palette.onSurfaceMuted,
            value = "Add a limit (a budget, a deadline, a data plan) and Last Exit will tell you the last day " +
                "you can still change course.",
        ).apply { gravity = Gravity.CENTER }, Ui.matchWrap().apply { topMargin = dpi(8) })
        box.addView(Ui.button(context, palette, "Add a limit", ButtonStyle.FILLED, R.drawable.ic_add) {
            host.push(Routes.NEW_TRACKER)
        }, Ui.wrap().apply { topMargin = dpi(22) })
        box.addView(Ui.button(context, palette, "Watch the 60-second demo", ButtonStyle.TEXT, R.drawable.ic_play) {
            host.push(Routes.DEMO)
        }, Ui.wrap().apply { topMargin = dpi(4) })

        box.addView(Ui.label(context, palette, "Or start from a template"), Ui.matchWrap().apply { topMargin = dpi(18) })
        val templates = Ui.horizontal(context)
        Templates.all.forEach { template ->
            val chip = Ui.horizontal(context).apply {
                setPadding(dpi(8), dpi(6), dpi(14), dpi(6))
                background = Ui.ripple(Ui.rounded(palette.surfaceVariant, dp(20f)), palette.primary.withAlpha(0.2f), dp(20f))
                minimumHeight = dpi(48)
                isClickable = true
                isFocusable = true
                contentDescription = "${template.title} template"
                setOnClickListener { host.push(Routes.newTracker(template.id)) }
            }
            chip.addView(Ui.gradientTile(context, typeIcon(template.type), typeGradient(template.type), sizeDp = 28),
                LinearLayout.LayoutParams(dpi(28), dpi(28)).apply { marginEnd = dpi(8) })
            chip.addView(Ui.text(context, 14f, palette.onSurface, bold = true, value = template.title))
            templates.addView(chip, Ui.wrap().apply { marginEnd = dpi(8) })
        }
        box.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(templates)
        }, Ui.matchWrap().apply { topMargin = dpi(8) })
        return box
    }

    override fun onDataChanged() = render()

    override fun onShown() = render()

    private fun render() {
        val store = graph.store
        if (!store.isLoaded) {
            emptyState.visibility = View.GONE
            permissionCard.visibility = View.GONE
            outlookTitle.text = ""
            return
        }
        val today = graph.today()
        val snapshots = store.all().map { TrackerSnapshot.of(it, today) }.sortedWith(SEVERITY_FIRST)
        renderCards(snapshots)
        renderOutlook(snapshots)
        val showPermission = !graph.notifier.canPost() && !graph.prefs.getBoolean(PREF_DISMISSED_PERMISSION, false)
        permissionCard.visibility = if (showPermission) View.VISIBLE else View.GONE
        permissionButton.text = if (host.isNotificationPermissionBlocked()) "Open settings" else "Allow warnings"
        emptyState.visibility = if (snapshots.isEmpty()) View.VISIBLE else View.GONE
        fab.visibility = if (snapshots.isEmpty()) View.GONE else View.VISIBLE
        restoreSheet()
    }

    private fun renderCards(snapshots: List<TrackerSnapshot>) {
        val ids = snapshots.map { it.tracker.id }.toSet()
        cards.keys.filterNot { it in ids }.forEach { id -> cards.remove(id)?.let { list.removeView(it) } }
        snapshots.forEachIndexed { index, snapshot ->
            val id = snapshot.tracker.id
            val card = cards.getOrPut(id) {
                TrackerCardView(context, palette, showLogButton = true, onOpen = { host.push(Routes.detail(id)) }, onLog = { openSheet(id, null) })
            }
            if (list.indexOfChild(card) != index) {
                list.removeView(card)
                list.addView(card, index, Ui.matchWrap().apply { bottomMargin = dpi(14) })
            }
            card.bind(snapshot)
        }
    }

    private fun renderOutlook(snapshots: List<TrackerSnapshot>) {
        pills.removeAllViews()
        if (snapshots.isEmpty()) {
            outlookTitle.text = "Set your first limit"
            outlookBody.text = "Pick a template below, or watch the demo to see a warning arrive in time."
            return
        }
        val worst = snapshots.first()
        val needAttention = snapshots.count { it.forecast.status != Status.SAFE }
        if (needAttention == 0) {
            outlookTitle.text = "All clear"
            outlookBody.text = if (snapshots.size == 1) "Your limit is on track." else "All ${snapshots.size} limits are on track."
        } else {
            outlookTitle.text = if (needAttention == 1) "1 limit needs attention" else "$needAttention limits need attention"
            outlookBody.text = "${worst.tracker.name}: ${worst.messages.title(worst.forecast).replaceFirstChar { it.lowercase() }}."
        }
        Status.entries.sortedByDescending { it.severity }.forEach { status ->
            val count = snapshots.count { it.forecast.status == status }
            if (count > 0) pills.addView(pill(status, count), Ui.wrap().apply { marginEnd = dpi(8) })
        }
    }

    private fun openSheet(trackerId: Long, state: Bundle?) {
        val record = graph.store.get(trackerId) ?: return
        sheet?.dismiss()
        sheet = QuickLogSheet(context, palette, record, graph.today(), state) { entry ->
            graph.store.addEntry(entry.copy(id = graph.store.newEntryId()))
            host.showSnackbar("Logged ${TrackerSnapshot.of(record, graph.today()).format.amount(entry.amount)} to ${record.tracker.name}")
        }.also {
            it.setOnDismissListener { _ -> if (sheet === it) sheet = null }
            it.show()
        }
    }

    private fun restoreSheet() {
        val state = pendingSheet ?: return
        pendingSheet = null
        openSheet(state.getLong(QuickLogSheet.KEY_TRACKER_ID), state)
    }

    override fun onSaveState(out: Bundle) {
        sheet?.takeIf { it.isShowing }?.let { out.putBundle(KEY_SHEET, it.saveState()) }
    }

    override fun onDestroy() {
        sheet?.dismiss()
        sheet = null
    }

    private companion object {
        const val KEY_SHEET = "quick_log_sheet"
        const val PREF_DISMISSED_PERMISSION = "dismissed_permission_card"

        /** Worst status first; within a status, the soonest last exit first. */
        val SEVERITY_FIRST = Comparator<TrackerSnapshot> { a, b ->
            val bySeverity = b.forecast.status.severity.compareTo(a.forecast.status.severity)
            if (bySeverity != 0) return@Comparator bySeverity
            val aExit = a.forecast.daysToLastExit ?: Int.MAX_VALUE
            val bExit = b.forecast.daysToLastExit ?: Int.MAX_VALUE
            if (aExit != bExit) aExit.compareTo(bExit) else a.tracker.name.compareTo(b.tracker.name, ignoreCase = true)
        }
    }
}
