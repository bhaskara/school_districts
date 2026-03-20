package com.bmarthi.hello_android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

class SchoolZoneNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_MONITORING = "school_zone_monitoring"
        const val CHANNEL_ALERTS = "school_zone_alerts"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING,
                "School Zone Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while monitoring school zones"
            }
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "School Zone Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when entering a new school zone"
            }
            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setContentTitle("School Zone Monitor")
            .setContentText("Monitoring school zones")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    fun sendSchoolAlertNotification(schools: List<School>) {
        val person = Person.Builder().setName("SchoolZone").build()
        val names = schools.joinToString(", ") { it.name }
        val style = NotificationCompat.MessagingStyle(person)
            .addMessage("Entering school zone: $names", System.currentTimeMillis(), person)

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }
}
