package com.lastexit.app

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.lastexit.app.data.Entry
import com.lastexit.app.data.Tracker
import com.lastexit.app.ui.MainActivity
import com.lastexit.core.Status
import com.lastexit.core.TrackerType
import java.io.File
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * End-to-end flows on the real screens, run on the JVM with Robolectric (native graphics) for
 * Android 8.0, 12 and 14. Screenshots of each state are written to app/build/shots.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [26, 31, 34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    private val shots = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
    private val app get() = RuntimeEnvironment.getApplication() as LastExitApp
    private val today = LocalDate.now()

    // ---- helpers ------------------------------------------------------------------------------

    private fun settle(rounds: Int = 8) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun advance(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    private fun launch(intent: Intent? = null): ActivityController<MainActivity> {
        val controller = if (intent == null) {
            Robolectric.buildActivity(MainActivity::class.java)
        } else {
            Robolectric.buildActivity(MainActivity::class.java, intent)
        }
        controller.setup()
        settle()
        return controller
    }

    private fun allViews(root: View): List<View> {
        val out = ArrayList<View>()
        fun walk(v: View) {
            out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return out
    }

    private fun visible(v: View): Boolean {
        var cur: View? = v
        while (cur != null) {
            if (cur.visibility != View.VISIBLE) return false
            cur = cur.parent as? View
        }
        return true
    }

    private fun findText(root: View, text: String): TextView? =
        allViews(root).filterIsInstance<TextView>().firstOrNull { visible(it) && it.text?.toString()?.contains(text) == true }

    private fun findDescription(root: View, text: String): View? =
        allViews(root).firstOrNull { visible(it) && it.contentDescription?.toString()?.contains(text) == true }

    private fun click(root: View, text: String) {
        val texts = allViews(root).filterIsInstance<TextView>().filter { visible(it) }
        val view = texts.firstOrNull { it.text?.toString() == text }
            ?: findText(root, text)
            ?: findDescription(root, text)
            ?: error("No view with '$text'")
        // Like a finger: the tap lands on the closest clickable ancestor (e.g. the whole card).
        var target: View? = view
        while (target != null && !target.isClickable) target = target.parent as? View
        assertTrue("'$text' should be clickable", target?.performClick() == true)
        settle()
    }

    private fun screenText(activity: Activity): String =
        allViews(activity.window.decorView).filterIsInstance<TextView>().filter { visible(it) }.joinToString(" | ") { it.text.toString() }

    private fun capture(view: View, name: String, background: Int? = null) {
        val w = view.width
        val h = view.height
        assertTrue("$name has no size", w > 0 && h > 0)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        background?.let { canvas.drawColor(it) }
        view.draw(canvas)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val sdk = android.os.Build.VERSION.SDK_INT
        writePng(File(shots, if (sdk == 34) "$name.png" else "api${sdk}_$name.png"), w, h, pixels)
    }

    /** Minimal RGBA PNG encoder (java.awt is not on Android's unit-test classpath). */
    private fun writePng(file: File, w: Int, h: Int, argb: IntArray) {
        val raw = java.io.ByteArrayOutputStream(h * (w * 4 + 1))
        for (y in 0 until h) {
            raw.write(0) // filter: none
            for (x in 0 until w) {
                val p = argb[y * w + x]
                raw.write(p shr 16 and 0xFF)
                raw.write(p shr 8 and 0xFF)
                raw.write(p and 0xFF)
                raw.write(p ushr 24)
            }
        }
        val out = java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(file)))
        fun chunk(type: String, data: ByteArray) {
            val crc = java.util.zip.CRC32()
            crc.update(type.toByteArray(Charsets.US_ASCII))
            crc.update(data)
            out.writeInt(data.size)
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(data)
            out.writeInt(crc.value.toInt())
        }
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val header = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(header).apply {
            writeInt(w)
            writeInt(h)
            write(byteArrayOf(8, 6, 0, 0, 0)) // 8-bit RGBA, deflate, no filter, no interlace
        }
        chunk("IHDR", header.toByteArray())
        val compressed = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(compressed).use { it.write(raw.toByteArray()) }
        chunk("IDAT", compressed.toByteArray())
        chunk("IEND", ByteArray(0))
        out.close()
    }

    private fun screenshot(activity: Activity, name: String) = capture(activity.window.decorView, name)

    /** Captures the whole scrolling content of the current screen, not just what fits. */
    private fun fullPage(activity: Activity, name: String, background: Int) {
        val scroll = allViews(activity.window.decorView).filterIsInstance<ScrollView>().first { visible(it) }
        capture(scroll.getChildAt(0), name, background)
    }

    private fun seedTracker(
        name: String,
        type: TrackerType,
        unit: String,
        limit: Double,
        daysAgo: Long,
        length: Long,
        daily: (Int) -> Double,
        factor: Double = 0.4,
    ): Long {
        val store = app.graph.store
        var id = -1L
        store.whenLoaded {
            id = store.addTracker(
                Tracker(0, name, type, unit, limit, today.minusDays(daysAgo), today.minusDays(daysAgo).plusDays(length - 1), factor),
            )
            for (d in 0..daysAgo.toInt()) {
                val amount = daily(d)
                if (amount != 0.0) {
                    store.addEntry(Entry(store.newEntryId(), id, today.minusDays(daysAgo - d), amount, if (d % 3 == 0) "Groceries" else "", false))
                }
            }
        }
        settle()
        return id
    }

    private fun palette(activity: MainActivity) = activity.palette

    // ---- tests --------------------------------------------------------------------------------

    @Test
    fun emptyHomeShowsOnboarding() {
        val controller = launch()
        val activity = controller.get()
        val text = screenText(activity)
        assertTrue(text, text.contains("Nothing tracked yet"))
        assertTrue(text, text.contains("Watch the 60-second demo"))
        screenshot(activity, "01_home_empty")
    }

    @Test
    fun demoEscalatesBeforeTheLimitAndNotifies() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = launch().get()
        click(activity.window.decorView, "Demo")
        val root = activity.window.decorView
        screenshot(activity, "02_demo_start")
        val nm = app.getSystemService(NotificationManager::class.java)

        // Play at 5x until the first warning auto-pauses the demo.
        click(root, "1×")
        click(root, "5×")
        findDescription(root, "Play")!!.performClick()
        var guard = 0
        while (findText(root, "Act soon") == null && guard++ < 60) advance(300)
        settle()
        assertNotNull("amber warning should appear", findText(root, "Act soon"))
        assertNotNull("demo should auto-pause", findDescription(root, "Play"))
        screenshot(activity, "03_demo_amber")
        assertEquals(1, shadowOf(nm).allNotifications.size)

        findDescription(root, "Play")!!.performClick()
        guard = 0
        while (findText(root, "Last exit") == null && guard++ < 60) advance(300)
        settle()
        val red = screenText(activity)
        assertTrue(red, red.contains("The limit isn't crossed yet"))
        screenshot(activity, "04_demo_red")
        val notification = shadowOf(nm).allNotifications.last()
        val title = notification.extras.getCharSequence("android.title").toString()
        assertTrue(title, title.startsWith("[Demo]") && title.contains("Last exit"))

        // Take the exit and play to the end of the month.
        click(root, "Take the exit")
        findDescription(root, "Play")!!.performClick()
        guard = 0
        while (findText(root, "How the month ended") == null && guard++ < 200) {
            advance(300)
            findDescription(root, "Play")?.takeIf { findText(root, "How the month ended") == null }?.performClick()
        }
        settle()
        val end = screenText(activity)
        assertTrue(end, end.contains("You took the exit on day"))
        assertTrue(end, end.contains("Back on track"))
        screenshot(activity, "05_demo_exit_outcome")
        fullPage(activity, "05b_demo_exit_full", palette(activity).background)
    }

    @Test
    fun demoWithoutTheExitCrossesTheLineLater() {
        val activity = launch().get()
        click(activity.window.decorView, "Demo")
        val root = activity.window.decorView
        click(root, "20×")
        val pauseSwitch = findText(root, "Pause on each warning")!!
        pauseSwitch.performClick()
        settle()
        findDescription(root, "Play")!!.performClick()
        var guard = 0
        while (findText(root, "How the month ended") == null && guard++ < 200) advance(100)
        settle()
        val text = screenText(activity)
        assertTrue(text, text.contains("Limit actually crossed"))
        assertTrue(text, text.contains("days before the crossing"))
        assertTrue(text, text.contains("over the budget"))
        screenshot(activity, "06_demo_crossed")
        fullPage(activity, "06b_demo_crossed_full", palette(activity).background)
    }

    @Test
    fun demoSurvivesRotation() {
        val controller = launch()
        click(controller.get().window.decorView, "Demo")
        val root = controller.get().window.decorView
        repeat(12) { click(root, "Advance one day") }
        assertNotNull(findText(root, "Day 13 of 30"))
        controller.recreate()
        settle()
        val text = screenText(controller.get())
        assertTrue(text, text.contains("Day 13 of 30"))
        assertTrue(text, text.contains("Demo mode"))
    }

    @Test
    fun createLimitFromTemplateThenLog() {
        val activity = launch().get()
        val root = activity.window.decorView
        click(root, "Add a limit")
        screenshot(activity, "07_new_limit")
        click(root, "Freelance Project Hours")
        val text = screenText(activity)
        assertTrue(text, text.contains("about 1.43 h/day"))
        click(root, "Start tracking")
        val detail = screenText(activity)
        assertTrue(detail, detail.contains("Client project hours"))
        assertTrue(detail, detail.contains("No entries yet"))
        screenshot(activity, "08_detail_new")

        click(root, "Log an amount")
        val dialog: Dialog = ShadowDialog.getLatestDialog()
        val sheet = dialog.window!!.decorView
        click(sheet, "+5")
        click(sheet, "+10")
        val preview = screenText(activity) + allViews(sheet).filterIsInstance<TextView>().joinToString { it.text.toString() }
        assertTrue(preview, preview.contains("AFTER THIS ENTRY") && preview.contains("Early estimate"))
        capture(sheet, "09_quick_log")
        click(sheet, "Log")
        settle()
        val after = screenText(activity)
        assertTrue(after, after.contains("History (1)"))
        assertTrue(after, after.contains("15 h"))
    }

    @Test
    fun detailShowsLastExitRecoveryAndWhatIf() {
        // 12 days in at ~$73/day against $1,500 for 30 days: well past the needed $50/day.
        val id = seedTracker("Monthly budget", TrackerType.MONEY, "$", 1500.0, 11, 30, { d -> if (d < 6) 45.0 else 92.0 })
        val activity = launch().get()
        val root = activity.window.decorView
        screenshot(activity, "10_home_one")
        click(root, "Monthly budget")
        val text = screenText(activity)
        assertTrue(text, text.contains("How to get back under"))
        assertTrue(text, text.contains("Act today"))
        screenshot(activity, "11_detail")
        fullPage(activity, "11b_detail_full", palette(activity).background)

        // What-if: drag the pace slider all the way down -> safe.
        val seek = allViews(root).filterIsInstance<SeekBar>().first { it.contentDescription == "What-if daily pace" }
        shadowOf(seek).onSeekBarChangeListener.onProgressChanged(seek, 100, true)
        settle()
        val whatIf = screenText(activity)
        assertTrue(whatIf, whatIf.contains("Nothing is saved"))
        assertTrue(whatIf, whatIf.contains("You're on track"))
        screenshot(activity, "12_what_if")
        // What-if never touches stored data.
        assertEquals(1500.0, app.graph.store.get(id)!!.tracker.limit, 0.0)
        assertEquals(12, app.graph.store.get(id)!!.entries.size)
    }

    @Test
    fun homeListsWorstFirstAndAlertsInApp() {
        seedTracker("Mobile data", TrackerType.RESOURCE, "GB", 15.0, 9, 30, { 0.3 })
        seedTracker("Client project hours", TrackerType.TIME, "h", 40.0, 14, 28, { d -> if (d < 5) 1.0 else 2.6 }, factor = 0.5)
        seedTracker("Overload risk", TrackerType.RISK, "pts", 100.0, 6, 14, { d -> if (d % 2 == 0) 20.0 else 5.0 }, factor = 0.3)
        val activity = launch().get()
        val text = screenText(activity)
        val risk = text.indexOf("Overload risk")
        val data = text.indexOf("Mobile data")
        assertTrue(text, risk in 0 until data)
        assertTrue(text, text.contains("need attention"))
        advance(9_000)
        settle()
        screenshot(activity, "13_home_list")
        fullPage(activity, "13b_home_list_full", palette(activity).background)
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkMode() {
        seedTracker("Monthly budget", TrackerType.MONEY, "$", 1500.0, 11, 30, { d -> if (d < 6) 45.0 else 92.0 })
        seedTracker("Mobile data", TrackerType.RESOURCE, "GB", 15.0, 9, 30, { 0.3 })
        val activity = launch().get()
        assertTrue(activity.palette.isDark)
        screenshot(activity, "14_home_dark")
        click(activity.window.decorView, "Monthly budget")
        screenshot(activity, "15_detail_dark")
    }

    @Test
    fun notificationDeepLinkOpensTheTracker() {
        val id = seedTracker("Study hours (burnout cap)", TrackerType.TIME, "h", 30.0, 3, 7, { 6.0 })
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lastexit://tracker/$id"), app, MainActivity::class.java)
        val activity = launch(intent).get()
        val text = screenText(activity)
        assertTrue(text, text.contains("Study hours (burnout cap)") && text.contains("Key numbers"))
    }

    @Test
    fun backgroundCheckNotifiesOnlyWhenStatusWorsens() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val id = seedTracker("Monthly budget", TrackerType.MONEY, "$", 1500.0, 11, 30, { d -> if (d < 6) 45.0 else 92.0 })
        val nm = app.getSystemService(NotificationManager::class.java)
        val first = app.graph.monitor.runBackgroundCheck(today)
        settle()
        assertEquals(1, first.count { it.isWorse })
        assertEquals(1, shadowOf(nm).allNotifications.size)
        val n = shadowOf(nm).allNotifications.first()
        assertEquals("lastexit://tracker/$id", shadowOf(n.contentIntent).savedIntent.data.toString())
        // Same status again: no repeat.
        app.graph.monitor.runBackgroundCheck(today)
        settle()
        assertEquals(1, shadowOf(nm).allNotifications.size)
        assertTrue(app.graph.store.get(id)!!.tracker.lastNotifiedStatus.isWorseThan(Status.SAFE))
    }

    @Test
    fun formValidatesBeforeSaving() {
        val activity = launch().get()
        val root = activity.window.decorView
        click(root, "Add a limit")
        val name = allViews(root).filterIsInstance<android.widget.EditText>().first { it.contentDescription == "Name" }
        val limit = allViews(root).filterIsInstance<android.widget.EditText>().first { it.contentDescription == "Limit" }
        name.setText("")
        limit.setText("0")
        settle()
        click(root, "Start tracking")
        val text = screenText(activity)
        assertTrue(text, text.contains("Give this limit a name."))
        assertTrue(text, text.contains("The limit must be more than zero."))
        assertEquals(0, app.graph.store.all().size)
        screenshot(activity, "16_form_errors")
    }

    @Test
    fun editDeleteAndUndo() {
        val id = seedTracker("Monthly budget", TrackerType.MONEY, "$", 1500.0, 11, 30, { d -> if (d < 6) 45.0 else 92.0 })
        val activity = launch().get()
        val root = activity.window.decorView
        click(root, "Monthly budget")

        // Delete the newest entry through the TalkBack action, then undo it from the snackbar.
        val row = allViews(root).filterIsInstance<com.lastexit.app.ui.kit.SwipeToDeleteLayout>().first()
        row.performAccessibilityAction(com.lastexit.app.R.id.action_delete_entry, null)
        settle()
        assertEquals(11, app.graph.store.get(id)!!.entries.size)
        assertTrue(screenText(activity).contains("History (11)"))
        click(root, "UNDO")
        assertEquals(12, app.graph.store.get(id)!!.entries.size)

        // Cut-back slider is saved when released.
        val cut = allViews(root).filterIsInstance<SeekBar>().first { it.contentDescription == "How much you could cut back" }
        shadowOf(cut).onSeekBarChangeListener.onProgressChanged(cut, 18, true)
        shadowOf(cut).onSeekBarChangeListener.onStopTrackingTouch(cut)
        settle()
        assertEquals(0.0, app.graph.store.get(id)!!.tracker.minPaceFactor, 1e-9)

        // Edit: raise the limit so the forecast turns safe.
        findDescription(root, "More options")!!.performClick()
        settle()
        val popup = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu()
        val edit = popup.menu.getItem(0)
        shadowOf(popup).onMenuItemClickListener.onMenuItemClick(edit)
        settle()
        val limit = allViews(root).filterIsInstance<android.widget.EditText>().first { visible(it) && it.contentDescription == "Limit" }
        limit.setText("3000")
        settle()
        click(root, "Save changes")
        assertEquals(3000.0, app.graph.store.get(id)!!.tracker.limit, 0.0)
        assertTrue(screenText(activity).contains("You're on track"))

        // Delete the whole limit via the confirmation dialog.
        findDescription(root, "More options")!!.performClick()
        settle()
        val popup2 = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu()
        shadowOf(popup2).onMenuItemClickListener.onMenuItemClick(popup2.menu.getItem(1))
        settle()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        settle()
        assertEquals(null, app.graph.store.get(id))
        assertTrue(screenText(activity).contains("Nothing tracked yet"))
    }

    @Test
    fun themePickerAppliesThemeAndAppearance() {
        seedTracker("Monthly budget", TrackerType.MONEY, "$", 1500.0, 11, 30, { d -> if (d < 6) 45.0 else 92.0 })
        val controller = launch()
        findDescription(controller.get().window.decorView, "Theme and appearance")!!.performClick()
        settle()
        val sheet = ShadowDialog.getLatestDialog().window!!.decorView
        capture(sheet, "17_theme_sheet")
        click(sheet, "Midnight theme")
        click(sheet, "Dark")
        click(sheet, "Apply")
        settle()
        assertEquals(com.lastexit.app.ui.kit.BrandTheme.MIDNIGHT, com.lastexit.app.ui.kit.ThemePrefs.brand(app))
        assertEquals(com.lastexit.app.ui.kit.Appearance.DARK, com.lastexit.app.ui.kit.ThemePrefs.appearance(app))
        // A fresh activity picks the saved theme up.
        val again = launch().get()
        assertEquals(com.lastexit.app.ui.kit.BrandTheme.MIDNIGHT, again.palette.brand)
        assertTrue(again.palette.isDark)
    }

    @Test
    fun everyThemeRenders() {
        seedTracker("Monthly budget", TrackerType.MONEY, "$", 1500.0, 11, 30, { d -> if (d < 6) 45.0 else 92.0 })
        seedTracker("Mobile data", TrackerType.RESOURCE, "GB", 15.0, 9, 30, { 0.3 })
        seedTracker("Client project hours", TrackerType.TIME, "h", 40.0, 14, 28, { d -> if (d < 5) 1.0 else 1.6 }, factor = 0.5)
        val themes = com.lastexit.app.ui.kit.BrandTheme.entries.filter { it.isAvailable }
        for (theme in themes) {
            for (appearance in listOf(com.lastexit.app.ui.kit.Appearance.LIGHT, com.lastexit.app.ui.kit.Appearance.DARK)) {
                com.lastexit.app.ui.kit.ThemePrefs.save(app, theme, appearance)
                val activity = launch().get()
                assertEquals(theme, activity.palette.brand)
                assertEquals(appearance == com.lastexit.app.ui.kit.Appearance.DARK, activity.palette.isDark)
                advance(9_000) // let the in-app alert auto-hide for a clean shot
                settle()
                screenshot(activity, "theme_${theme.name.lowercase()}_${appearance.name.lowercase()}_home")
                click(activity.window.decorView, "Monthly budget")
                screenshot(activity, "theme_${theme.name.lowercase()}_${appearance.name.lowercase()}_detail")
            }
        }
        com.lastexit.app.ui.kit.ThemePrefs.save(app, com.lastexit.app.ui.kit.BrandTheme.DEFAULT, com.lastexit.app.ui.kit.Appearance.SYSTEM)
    }

    @Test
    fun everyDrawableInflates() {
        val r = com.lastexit.app.R.drawable::class.java.fields
        r.forEach { field ->
            val drawable = app.getDrawable(field.getInt(null))
            assertNotNull(field.name, drawable)
            drawable!!.setBounds(0, 0, 96, 96)
            drawable.draw(Canvas(Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)))
        }
    }
}
