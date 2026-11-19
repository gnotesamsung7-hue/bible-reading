package com.markdave.biblejourney

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily reading reminder", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val line = LINES[(System.currentTimeMillis() / 86_400_000L % LINES.size).toInt()]
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle("Time for today’s Bible reading")
            .setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(line))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }

    companion object {
        private const val CHANNEL = "daily_reading"
        private val LINES = listOf(
            "“Thy word is a lamp unto my feet, and a light unto my path.” Psalm 119:105",
            "“Man shall not live by bread alone, but by every word that proceedeth out of the mouth of God.” Matthew 4:4",
            "“The grass withereth, the flower fadeth: but the word of our God shall stand for ever.” Isaiah 40:8",
            "“Blessed is he that readeth.” Revelation 1:3",
            "“Thy words were found, and I did eat them.” Jeremiah 15:16",
            "Open the app to see today’s chapters."
        )
    }
}
