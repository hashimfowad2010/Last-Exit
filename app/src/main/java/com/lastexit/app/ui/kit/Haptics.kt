package com.lastexit.app.ui.kit

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.lastexit.core.Status

/** Escalating vibration patterns: the worse the status, the harder the buzz. */
object Haptics {
    fun statusWorsened(context: Context, status: Status, fallbackView: View? = null) {
        val vibrator = vibrator(context)
        if (vibrator == null || !vibrator.hasVibrator()) {
            fallbackView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }
        val pattern = when (status) {
            Status.SAFE -> longArrayOf(0, 25)
            Status.ACT_SOON -> longArrayOf(0, 60)
            Status.LAST_EXIT -> longArrayOf(0, 90, 90, 160)
            Status.PAST_THE_LINE -> longArrayOf(0, 140, 80, 140, 80, 260)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    /** Light tick used while dragging across a status boundary in the "What if?" slider. */
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
