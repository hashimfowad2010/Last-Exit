package com.lastexit.app.ui.edit

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.lastexit.app.R
import com.lastexit.app.data.Tracker
import com.lastexit.app.ui.Routes
import com.lastexit.app.ui.Screen
import com.lastexit.app.ui.ScreenHost
import com.lastexit.app.ui.TopBar
import com.lastexit.app.ui.kit.ButtonStyle
import com.lastexit.app.ui.kit.Gradients
import com.lastexit.app.ui.kit.HeroDrawable
import com.lastexit.app.ui.kit.typeGradient
import com.lastexit.app.ui.kit.SegmentedControl
import com.lastexit.app.ui.kit.Ui
import com.lastexit.app.ui.kit.dp
import com.lastexit.app.ui.kit.dpi
import com.lastexit.app.ui.kit.markAsHeading
import com.lastexit.app.ui.kit.typeIcon
import com.lastexit.app.ui.kit.withAlpha
import com.lastexit.core.AmountFormat
import com.lastexit.core.DateText
import com.lastexit.core.ForecastEngine
import com.lastexit.core.Status
import com.lastexit.core.TrackerTemplate
import com.lastexit.core.TrackerType
import com.lastexit.core.Templates
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Create a limit (optionally from a template) or edit an existing one. Every input is validated. */
class EditTrackerScreen(
    host: ScreenHost,
    route: String,
    private val trackerId: Long?,
    private val initialTemplate: String? = null,
) : Screen(host, route) {
    private lateinit var nameField: Field
    private lateinit var unitField: Field
    private lateinit var limitField: Field
    private lateinit var typeControl: SegmentedControl
    private lateinit var startButton: TextView
    private lateinit var deadlineButton: TextView
    private lateinit var dateError: TextView
    private lateinit var cutSeek: SeekBar
    private lateinit var cutLabel: TextView
    private lateinit var hint: TextView
    private val templateCards = LinkedHashMap<String, LinearLayout>()

    private var type = TrackerType.MONEY
    private var startDate: LocalDate = LocalDate.now()
    private var deadline: LocalDate = LocalDate.now().plusDays(29)
    private var factor = ForecastEngine.DEFAULT_MIN_PACE_FACTOR
    private var templateId: String? = null
    private var showErrors = false
    private var existing: Tracker? = null

    private val isNew: Boolean get() = trackerId == null

    override fun statusBarColor(): Int = palette.gradient[0]

    override fun statusBarDarkIcons(): Boolean = false

    /** Label, input and inline error for one form field. */
    private class Field(val container: LinearLayout, val input: EditText, val error: TextView)

    override fun onCreateView(saved: Bundle?): View {
        val today = graph.today()
        startDate = today
        deadline = today.plusDays(29)
        existing = trackerId?.let { graph.store.get(it)?.tracker }

        val column = Ui.vertical(context)
        val header = Ui.vertical(context).apply {
            background = HeroDrawable(palette.gradient, dp(28f))
            setPadding(0, 0, dpi(16), dpi(if (isNew) 22 else 18))
        }
        header.addView(TopBar.create(context, palette, if (isNew) "New limit" else "Edit limit", { host.pop() }, onGradient = true))
        header.addView(Ui.text(
            context, 14f, Color.WHITE.withAlpha(0.85f),
            value = if (isNew) "Pick a template or start from scratch. You can change everything later." else "Changes apply to the forecast immediately.",
        ), Ui.matchWrap().apply { marginStart = dpi(20) })
        column.addView(header)
        val scroll = ScrollView(context)
        val content = Ui.vertical(context).apply {
            setPadding(dpi(16), dpi(16), dpi(16), dpi(32))
            clipToPadding = false
        }
        if (isNew) {
            content.addView(Ui.label(context, palette, "Start from a template"))
            content.addView(templates(), Ui.matchWrap().apply { topMargin = dpi(8) })
        }
        content.addView(form(), Ui.matchWrap().apply { topMargin = dpi(16) })
        val actions = Ui.horizontal(context, Gravity.END or Gravity.CENTER_VERTICAL)
        actions.addView(Ui.button(context, palette, "Cancel", ButtonStyle.TEXT) { host.pop() })
        actions.addView(Ui.button(context, palette, if (isNew) "Start tracking" else "Save changes", ButtonStyle.FILLED) { save() },
            Ui.wrap().apply { marginStart = dpi(8) })
        content.addView(actions, Ui.matchWrap().apply { topMargin = dpi(16) })
        scroll.addView(content)
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        when {
            saved != null -> restore(saved)
            existing != null -> load(existing!!)
            else -> applyTemplate(Templates.byId(initialTemplate) ?: Templates.monthlyBudget)
        }
        refresh()
        return column
    }

    // ---- Layout -------------------------------------------------------------------------------

    private fun templates(): View {
        val row = Ui.horizontal(context, Gravity.TOP).apply { setPadding(dpi(2), dpi(4), dpi(8), dpi(10)) }
        Templates.all.forEach { template ->
            val card = Ui.vertical(context, padding = 14).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener { applyTemplate(template); refresh() }
                contentDescription = "${template.title} template. ${template.description}"
            }
            card.addView(Ui.gradientTile(context, typeIcon(template.type), typeGradient(template.type), sizeDp = 40))
            card.addView(Ui.text(context, 15f, palette.onSurface, bold = true, value = template.title, maxLines = 2),
                Ui.matchWrap().apply { topMargin = dpi(8) })
            card.addView(Ui.text(context, 13f, palette.onSurfaceMuted, value = template.description, maxLines = 3),
                Ui.matchWrap().apply { topMargin = dpi(2) })
            templateCards[template.id] = card
            row.addView(card, LinearLayout.LayoutParams(dpi(180), LinearLayout.LayoutParams.MATCH_PARENT).apply { marginEnd = dpi(10) })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(row)
        }
    }

    private fun form(): View {
        val card = Ui.card(context, palette, padding = 18)
        card.addView(Ui.sectionHeader(context, palette, "Details", R.drawable.ic_flag))

        nameField = field("Name", "e.g. Groceries this month", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        card.addView(nameField.container, Ui.matchWrap().apply { topMargin = dpi(12) })

        card.addView(Ui.label(context, palette, "What are you limiting?"), Ui.matchWrap().apply { topMargin = dpi(14) })
        typeControl = SegmentedControl(context, palette, TrackerType.entries.map { it.label }) { index ->
            val previous = type
            type = TrackerType.entries[index]
            // Swap the unit only if the user had not customised it.
            if (unitField.input.text.toString().trim() == previous.defaultUnit) unitField.input.setText(defaultUnit(type))
            templateId = null
            refresh()
        }
        card.addView(typeControl, Ui.matchWrap().apply { topMargin = dpi(6) })

        val row = Ui.horizontal(context, Gravity.TOP)
        limitField = field("Limit", "2000", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).also {
            it.input.keyListener = DigitsKeyListener.getInstance(Locale.getDefault(), false, true)
        }
        unitField = field("Unit", "$", InputType.TYPE_CLASS_TEXT)
        row.addView(limitField.container, Ui.weight(2f).apply { marginEnd = dpi(10) })
        row.addView(unitField.container, Ui.weight(1f))
        card.addView(row, Ui.matchWrap().apply { topMargin = dpi(12) })

        val dates = Ui.horizontal(context, Gravity.TOP)
        startButton = dateButton("Starts") { pickDate(startDate) { startDate = it; refresh() } }
        deadlineButton = dateButton("Deadline") { pickDate(deadline) { deadline = it; refresh() } }
        dates.addView(startButton, Ui.weight().apply { marginEnd = dpi(10) })
        dates.addView(deadlineButton, Ui.weight())
        card.addView(dates, Ui.matchWrap().apply { topMargin = dpi(14) })
        dateError = errorText()
        card.addView(dateError)

        card.addView(Ui.label(context, palette, "How hard can you cut back?"), Ui.matchWrap().apply { topMargin = dpi(16) })
        cutSeek = SeekBar(context).apply {
            max = CUT_STEPS
            minimumHeight = dpi(48)
            contentDescription = "How much you could cut back"
            progressTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            thumbTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    factor = 1.0 - (10 + 5 * progress) / 100.0
                    refresh()
                }

                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }
        card.addView(cutSeek)
        cutLabel = Ui.text(context, 14f, palette.onSurface)
        card.addView(cutLabel)

        hint = Ui.text(context, 14f, palette.onPrimaryContainer).apply {
            background = Ui.rounded(palette.primaryContainer, dp(14f))
            setPadding(dpi(14), dpi(10), dpi(14), dpi(10))
        }
        card.addView(hint, Ui.matchWrap().apply { topMargin = dpi(14) })
        return card
    }

    private fun field(label: String, placeholder: String, inputType: Int): Field {
        val container = Ui.vertical(context)
        container.addView(Ui.label(context, palette, label))
        val input = EditText(context).apply {
            this.inputType = inputType
            hint = placeholder
            textSize = 17f
            setTextColor(palette.onSurface)
            setHintTextColor(palette.onSurfaceMuted.withAlpha(0.7f))
            setSingleLine(true)
            minHeight = dpi(52)
            setPadding(dpi(14), dpi(10), dpi(14), dpi(10))
            background = Ui.rounded(palette.surfaceVariant, dp(14f))
            contentDescription = label
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = refresh()
            })
        }
        container.addView(input, Ui.matchWrap().apply { topMargin = dpi(6) })
        val error = errorText()
        container.addView(error)
        return Field(container, input, error)
    }

    private fun errorText(): TextView =
        Ui.text(context, 13f, palette.status(Status.LAST_EXIT).main).apply {
            visibility = View.GONE
            setPadding(dpi(4), dpi(4), 0, 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }

    private fun dateButton(label: String, onClick: () -> Unit): TextView =
        Ui.text(context, 16f, palette.onSurface, bold = true).apply {
            minHeight = dpi(56)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(14), dpi(6), dpi(14), dpi(6))
            background = Ui.ripple(Ui.rounded(palette.surfaceVariant, dp(14f)), palette.primary.withAlpha(0.2f), dp(14f))
            val icon = context.getDrawable(R.drawable.ic_event)!!.mutate().also { it.setTint(palette.onSurfaceMuted) }
            icon.setBounds(0, 0, dpi(20), dpi(20))
            setCompoundDrawablesRelative(null, null, icon, null)
            tag = label
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, year, month, day ->
            onPicked(LocalDate.of(year, month + 1, day))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    // ---- State --------------------------------------------------------------------------------

    private fun applyTemplate(template: TrackerTemplate) {
        val today = graph.today()
        templateId = template.id
        type = template.type
        nameField.input.setText(template.name)
        unitField.input.setText(if (template.type == TrackerType.MONEY) defaultUnit(TrackerType.MONEY) else template.unit)
        limitField.input.setText(plain(template.limit))
        startDate = template.startDate(today)
        deadline = template.deadline(today)
        factor = template.minPaceFactor
    }

    private fun load(tracker: Tracker) {
        type = tracker.type
        nameField.input.setText(tracker.name)
        unitField.input.setText(tracker.unit)
        limitField.input.setText(plain(tracker.limit))
        startDate = tracker.startDate
        deadline = tracker.deadline
        factor = tracker.minPaceFactor
    }

    private fun restore(saved: Bundle) {
        type = TrackerType.fromName(saved.getString(KEY_TYPE))
        nameField.input.setText(saved.getString(KEY_NAME, ""))
        unitField.input.setText(saved.getString(KEY_UNIT, ""))
        limitField.input.setText(saved.getString(KEY_LIMIT, ""))
        startDate = LocalDate.ofEpochDay(saved.getLong(KEY_START, startDate.toEpochDay()))
        deadline = LocalDate.ofEpochDay(saved.getLong(KEY_DEADLINE, deadline.toEpochDay()))
        factor = saved.getDouble(KEY_FACTOR, factor)
        templateId = saved.getString(KEY_TEMPLATE)
        showErrors = saved.getBoolean(KEY_SHOW_ERRORS, false)
    }

    override fun onSaveState(out: Bundle) {
        out.putString(KEY_TYPE, type.name)
        out.putString(KEY_NAME, nameField.input.text.toString())
        out.putString(KEY_UNIT, unitField.input.text.toString())
        out.putString(KEY_LIMIT, limitField.input.text.toString())
        out.putLong(KEY_START, startDate.toEpochDay())
        out.putLong(KEY_DEADLINE, deadline.toEpochDay())
        out.putDouble(KEY_FACTOR, factor)
        out.putString(KEY_TEMPLATE, templateId)
        out.putBoolean(KEY_SHOW_ERRORS, showErrors)
    }

    private fun defaultUnit(type: TrackerType): String =
        if (type == TrackerType.MONEY) localCurrencySymbol() else type.defaultUnit

    private fun localCurrencySymbol(): String = try {
        java.util.Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault())
    } catch (unsupported: IllegalArgumentException) {
        "$"
    }

    private fun plain(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    // ---- Validation ---------------------------------------------------------------------------

    private data class Validation(
        val name: String?,
        val unit: String?,
        val limit: String?,
        val dates: String?,
        val limitValue: Double?,
    ) {
        val isValid: Boolean get() = name == null && unit == null && limit == null && dates == null
    }

    private fun validate(): Validation {
        val today = graph.today()
        val name = nameField.input.text.toString().trim()
        val unit = unitField.input.text.toString().trim()
        val limitRaw = limitField.input.text.toString().trim().replace(',', '.')
        val limit = limitRaw.toDoubleOrNull()
        val days = ForecastEngine.periodDays(startDate, deadline)
        return Validation(
            name = when {
                name.isEmpty() -> "Give this limit a name."
                name.length > 40 -> "Keep the name under 40 characters."
                else -> null
            },
            unit = when {
                unit.isEmpty() -> "Add a unit, like $, h or GB."
                unit.length > 6 -> "Use a short unit (6 characters max)."
                else -> null
            },
            limit = when {
                limitRaw.isEmpty() -> "Enter the limit you must stay under."
                limit == null || !limit.isFinite() -> "That isn't a number."
                limit <= 0.0 -> "The limit must be more than zero."
                limit > 1_000_000_000.0 -> "That limit is too large."
                else -> null
            },
            dates = when {
                deadline.isBefore(startDate) -> "The deadline must be on or after the start date."
                isNew && deadline.isBefore(today) -> "The deadline has already passed."
                days > 366 -> "Keep the period to a year or less."
                else -> null
            },
            limitValue = limit,
        )
    }

    private fun refresh() {
        if (!::cutLabel.isInitialized) return
        typeControl.select(type.ordinal)
        templateCards.forEach { (id, card) ->
            val selected = id == templateId
            card.background = Ui.ripple(
                Ui.rounded(
                    if (selected) palette.primaryContainer else palette.surface, dp(22f),
                    if (selected) palette.primary else palette.outline, dpi(if (selected) 2 else 1),
                ),
                palette.primary.withAlpha(0.2f), dp(22f),
            )
            card.elevation = if (selected && !palette.isDark) dp(4f) else 0f
            Gradients.tintShadow(card, palette.primary)
            card.isSelected = selected
        }
        startButton.text = "Starts\n${DateText.short(startDate, graph.today())}"
        deadlineButton.text = "Deadline\n${DateText.short(deadline, graph.today())}"
        startButton.contentDescription = "Start date, ${DateText.short(startDate, graph.today())}. Double tap to change."
        deadlineButton.contentDescription = "Deadline, ${DateText.short(deadline, graph.today())}. Double tap to change."
        cutSeek.progress = (((1.0 - factor) * 100 - 10) / 5).roundToInt().coerceIn(0, CUT_STEPS)
        cutLabel.text = "I could cut back by up to ${((1 - factor) * 100).roundToInt()}% if I had to."

        val v = validate()
        val days = ForecastEngine.periodDays(startDate, deadline).coerceAtLeast(1)
        val fmt = AmountFormat(type, unitField.input.text.toString())
        hint.text = if (v.limitValue != null && v.limitValue > 0) {
            "That's about ${fmt.pace(v.limitValue / days)} on average over ${DateText.days(days)}."
        } else {
            "Pick a limit and a period to see the average daily pace."
        }
        showError(nameField.error, v.name)
        showError(unitField.error, v.unit)
        showError(limitField.error, v.limit)
        showError(dateError, v.dates)
    }

    private fun showError(view: TextView, message: String?) {
        view.text = message ?: ""
        view.visibility = if (showErrors && message != null) View.VISIBLE else View.GONE
    }

    private fun save() {
        showErrors = true
        refresh()
        val v = validate()
        if (!v.isValid) {
            val first = listOf(nameField to v.name, unitField to v.unit, limitField to v.limit).firstOrNull { it.second != null }?.first
            first?.input?.requestFocus()
            host.showSnackbar("Check the highlighted fields")
            return
        }
        val tracker = Tracker(
            id = existing?.id ?: 0L,
            name = nameField.input.text.toString().trim(),
            type = type,
            unit = unitField.input.text.toString().trim(),
            limit = v.limitValue!!,
            startDate = startDate,
            deadline = deadline,
            minPaceFactor = factor,
            lastNotifiedStatus = existing?.lastNotifiedStatus ?: Status.SAFE,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        if (existing == null) {
            val id = graph.store.addTracker(tracker)
            host.replace(Routes.detail(id))
        } else {
            graph.store.updateTracker(tracker)
            host.pop()
        }
    }

    private companion object {
        const val CUT_STEPS = 18
        const val KEY_TYPE = "type"
        const val KEY_NAME = "name"
        const val KEY_UNIT = "unit"
        const val KEY_LIMIT = "limit"
        const val KEY_START = "start"
        const val KEY_DEADLINE = "deadline"
        const val KEY_FACTOR = "factor"
        const val KEY_TEMPLATE = "template"
        const val KEY_SHOW_ERRORS = "show_errors"
    }
}
