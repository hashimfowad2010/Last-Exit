package com.lastexit.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import com.lastexit.app.R
import com.lastexit.app.data.TrackerSnapshot
import com.lastexit.app.ui.MainActivity
import com.lastexit.app.ui.kit.Palette
import com.lastexit.core.Forecast
import com.lastexit.core.ForecastMessages
import com.lastexit.core.Status

/** Owns the notification channel and builds every alert the app posts. */
class Notifier(private val context: Context) {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)!!

    fun ensureChannels() {
        val channel = NotificationChannel(
            CHANNEL_WARNINGS,
            context.getString(R.string.channel_warnings_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_warnings_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 90, 90, 160)
            enableLights(true)
            lightColor = Color.RED
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPost(): Boolean = hasPermission() && manager.areNotificationsEnabled()

    /** Alert for a real tracker. Tapping it deep-links to that tracker's detail screen. */
    fun postTrackerAlert(snapshot: TrackerSnapshot): Boolean {
        val f = snapshot.forecast
        return post(
            id = notificationId(snapshot.tracker.id),
            title = "${snapshot.tracker.name}: ${snapshot.messages.title(f)}",
            body = snapshot.messages.notificationBody(f),
            status = f.status,
            uri = Uri.parse("lastexit://tracker/${snapshot.tracker.id}"),
        )
    }

    /** Alert for Demo mode. Uses the same channel so the presenter sees exactly what a user would. */
    fun postDemoAlert(name: String, forecast: Forecast, messages: ForecastMessages): Boolean = post(
        id = DEMO_NOTIFICATION_ID,
        title = "[Demo] $name: ${messages.title(forecast)}",
        body = messages.notificationBody(forecast),
        status = forecast.status,
        uri = Uri.parse("lastexit://demo"),
    )

    fun cancelDemo() = manager.cancel(DEMO_NOTIFICATION_ID)

    fun cancelTracker(trackerId: Long) = manager.cancel(notificationId(trackerId))

    private fun post(id: Int, title: String, body: String, status: Status, uri: Uri): Boolean {
        if (!canPost()) return false
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val color = Palette.from(context).status(status).main
        val notification = Notification.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_stat_last_exit)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setColor(color)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setContentIntent(pending)
            .build()
        return try {
            manager.notify(id, notification)
            true
        } catch (denied: SecurityException) {
            false
        }
    }

    private fun notificationId(trackerId: Long): Int = (trackerId % 1_000_000L).toInt() + 1

    companion object {
        const val CHANNEL_WARNINGS = "limit_warnings"
        const val DEMO_NOTIFICATION_ID = 9_000_001
    }
}
