package com.lastexit.app.ui.log

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.lastexit.app.R
import com.lastexit.app.data.Entry
import com.lastexit.app.data.TrackerRecord
import com.lastexit.app.data.TrackerSnapshot
import com.lastexit.app.ui.kit.ButtonStyle
import com.lastexit.app.ui.kit.Palette
import com.lastexit.app.ui.kit.SegmentedControl
import com.lastexit.app.ui.kit.StatusChipView
import com.lastexit.app.ui.kit.Ui
import com.lastexit.app.ui.kit.dp
import com.lastexit.app.ui.kit.dpi
import com.lastexit.app.ui.kit.withAlpha
import com.lastexit.core.AmountFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Bottom sheet for logging an amount. It previews the status *before* you commit, so the warning
 * arrives while the decision can still be changed.
 */
class QuickLogSheet(
    private val activity: Activity,
    private val palette: Palette,
    private val record: TrackerRecord,
    private val today: LocalDate,
    private val restored: Bundle?,
    private val onLog: (Entry) -> Unit,
) : Dialog(activity) {
    private val format = AmountFormat(record.tracker.type, record.tracker.unit)
    private lateinit var amount: EditText
    private lateinit var note: EditText
    private lateinit var dateControl: SegmentedControl
    private lateinit var oneOff: Switch
    private lateinit var previewBox: LinearLayout
    private lateinit var previewChip: StatusChipView
    private lateinit var previewText: TextView
    private lateinit var error: TextView
    private lateinit var logButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.BOTTOM)
            w.setWindowAnimations(R.style.SheetAnimation)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.45f)
        }
        restored?.let { restore(it) }
        updatePreview()
        amount.requestFocus()
    }

    private fun buildContent(): View {
        val sheet = Ui.vertical(context).apply {
            setPadding(dpi(20), dpi(10), dpi(20), dpi(16))
            background = Ui.rounded(palette.surface, dp(28f)).apply {
                cornerRadii = floatArrayOf(dp(28f), dp(28f), dp(28f), dp(28f), 0f, 0f, 0f, 0f)
            }
        }
        sheet.addView(View(context).apply {
            background = Ui.rounded(palette.outline, dp(2f))
        }, LinearLayout.LayoutParams(dpi(36), dpi(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpi(14) })

        sheet.addView(Ui.text(context, 20f, palette.onSurface, bold = true, value = "Log to ${record.tracker.name}"))
        sheet.addView(Ui.text(context, 14f, palette.onSurfaceMuted, value = "Positive to add, negative for a refund or correction."))

        val amountRow = Ui.horizontal(context, Gravity.CENTER_VERTICAL).apply {
            background = Ui.rounded(palette.surfaceVariant, dp(18f))
            setPadding(dpi(16), dpi(4), dpi(16), dpi(4))
        }
        val unitLabel = Ui.text(context, 26f, palette.onSurfaceMuted, bold = true, value = format.unit)
        val unitFirst = record.tracker.type == com.lastexit.core.TrackerType.MONEY
        if (unitFirst) amountRow.addView(unitLabel)
        amount = EditText(context).apply {
            textSize = 30f
            setTextColor(palette.onSurface)
            setHintTextColor(palette.onSurfaceMuted.withAlpha(0.6f))
            hint = "0"
            background = null
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            keyListener = DigitsKeyListener.getInstance(Locale.getDefault(), true, true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            contentDescription = "Amount in ${format.unit}"
            fontFeatureSettings = "tnum"
            addTextChangedListener(afterChange { updatePreview() })
        }
        amountRow.addView(amount, Ui.weight().apply { marginStart = dpi(if (unitFirst) 8 else 0) })
        if (!unitFirst) amountRow.addView(unitLabel)
        sheet.addView(amountRow, Ui.matchWrap().apply { topMargin = dpi(16) })

        val chips = Ui.horizontal(context)
        record.tracker.type.quickAmounts.forEachIndexed { i, value ->
            val label = record.tracker.type.quickLabels[i].let { if (it.isEmpty()) "+${format.number(value)}" else "$it +${format.number(value)}" }
            chips.addView(Ui.button(context, palette, label, ButtonStyle.TONAL) { addToAmount(value) }.apply {
                contentDescription = "Add ${format.amount(value)}"
            }, Ui.weight().apply { if (i > 0) marginStart = dpi(8) })
        }
        sheet.addView(chips, Ui.matchWrap().apply { topMargin = dpi(12) })

        note = EditText(context).apply {
            hint = "Note (optional)"
            textSize = 16f
            setTextColor(palette.onSurface)
            setHintTextColor(palette.onSurfaceMuted)
            background = Ui.rounded(palette.surfaceVariant, dp(16f))
            setPadding(dpi(16), dpi(12), dpi(16), dpi(12))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(60))
        }
        sheet.addView(note, Ui.matchWrap().apply { topMargin = dpi(12) })

        dateControl = SegmentedControl(context, palette, listOf("Today", "Yesterday")) { updatePreview() }
        sheet.addView(dateControl, Ui.matchWrap().apply { topMargin = dpi(12) })

        oneOff = Switch(context).apply {
            text = "One-off (don't count toward my pace)"
            textSize = 15f
            setTextColor(palette.onSurface)
            minHeight = dpi(48)
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }
        sheet.addView(oneOff, Ui.matchWrap().apply { topMargin = dpi(4) })

        previewBox = Ui.vertical(context, padding = 14).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val previewHeader = Ui.horizontal(context)
        previewHeader.addView(Ui.label(context, palette, "After this entry"), Ui.weight())
        previewChip = StatusChipView(context, palette)
        previewHeader.addView(previewChip)
        previewBox.addView(previewHeader)
        previewText = Ui.text(context, 14f, palette.onSurface)
        previewBox.addView(previewText, Ui.matchWrap().apply { topMargin = dpi(4) })
        sheet.addView(previewBox, Ui.matchWrap().apply { topMargin = dpi(8) })

        error = Ui.text(context, 13f, palette.status(com.lastexit.core.Status.LAST_EXIT).main).apply { visibility = View.GONE }
        sheet.addView(error, Ui.matchWrap().apply { topMargin = dpi(6) })

        val buttons = Ui.horizontal(context, Gravity.END or Gravity.CENTER_VERTICAL)
        buttons.addView(Ui.button(context, palette, "Cancel", ButtonStyle.TEXT) { dismiss() })
        logButton = Ui.button(context, palette, "Log", ButtonStyle.FILLED, R.drawable.ic_add) { submit() }
        buttons.addView(logButton, Ui.wrap().apply { marginStart = dpi(8) })
        sheet.addView(buttons, Ui.matchWrap().apply { topMargin = dpi(12) })

        return ScrollView(context).apply { addView(sheet) }
    }

    private fun parsedAmount(): Double? {
        val raw = amount.text.toString().trim().replace(',', '.')
        if (raw.isEmpty() || raw == "-" || raw == ".") return null
        val value = raw.toDoubleOrNull() ?: return null
        return value.takeIf { it.isFinite() && abs(it) <= MAX_AMOUNT && abs(it) > 0.0 }
    }

    private fun addToAmount(delta: Double) {
        val next = (parsedAmount() ?: 0.0) + delta
        val text = if (next == Math.floor(next)) next.toLong().toString() else "%.2f".format(Locale.US, next)
        amount.setText(text)
        amount.setSelection(amount.text.length)
    }

    private fun selectedDate(): LocalDate = if (dateControl.selected == 1) today.minusDays(1) else today

    private fun draftEntry(value: Double) = Entry(
        id = -1L,
        trackerId = record.tracker.id,
        date = selectedDate(),
        amount = value,
        note = note.text.toString().trim(),
        oneOff = oneOff.isChecked,
    )

    private fun updatePreview() {
        val value = parsedAmount()
        val before = TrackerSnapshot.of(record, today)
        val after = if (value != null) TrackerSnapshot.of(record, today, extra = draftEntry(value)) else before
        val f = after.forecast
        val colors = palette.status(f.status)
        previewBox.background = Ui.rounded(colors.container, dp(16f))
        previewChip.bind(f.status, after.messages.chipLabel(f), after.messages.spokenStatus(f), animate = true)
        val change = when {
            value == null -> "Right now: "
            f.status.isWorseThan(before.forecast.status) -> "Careful, this moves you from ${before.forecast.status.label} to ${f.status.label}. "
            before.forecast.status.isWorseThan(f.status) -> "This moves you back to ${f.status.label}. "
            else -> ""
        }
        previewText.text = change + after.messages.headline(f)
        previewText.setTextColor(colors.onContainer)
        val raw = amount.text.toString().trim()
        val invalid = raw.isNotEmpty() && raw != "-" && value == null
        error.visibility = if (invalid) View.VISIBLE else View.GONE
        error.text = "Enter a non-zero number up to ${format.number(MAX_AMOUNT)}."
        logButton.isEnabled = value != null
        logButton.alpha = if (value != null) 1f else 0.5f
    }

    private fun submit() {
        val value = parsedAmount() ?: run {
            error.visibility = View.VISIBLE
            error.text = "Enter an amount first."
            return
        }
        onLog(draftEntry(value))
        dismiss()
    }

    fun saveState(): Bundle = Bundle().apply {
        putLong(KEY_TRACKER_ID, record.tracker.id)
        putString(KEY_AMOUNT, amount.text.toString())
        putString(KEY_NOTE, note.text.toString())
        putInt(KEY_DATE, dateControl.selected)
        putBoolean(KEY_ONE_OFF, oneOff.isChecked)
    }

    private fun restore(state: Bundle) {
        amount.setText(state.getString(KEY_AMOUNT, ""))
        note.setText(state.getString(KEY_NOTE, ""))
        dateControl.select(state.getInt(KEY_DATE, 0))
        oneOff.isChecked = state.getBoolean(KEY_ONE_OFF, false)
    }

    private fun dpi(value: Int): Int = context.dpi(value)

    private fun dp(value: Float): Float = context.dp(value)

    private fun afterChange(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = action()
    }

    companion object {
        const val KEY_TRACKER_ID = "tracker_id"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_NOTE = "note"
        private const val KEY_DATE = "date"
        private const val KEY_ONE_OFF = "one_off"
        private const val MAX_AMOUNT = 1_000_000_000.0
    }
}
