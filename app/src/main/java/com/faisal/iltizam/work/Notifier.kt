package com.faisal.iltizam.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.faisal.iltizam.MainActivity
import com.faisal.iltizam.R
import com.faisal.iltizam.domain.Alert
import com.faisal.iltizam.ui.money

object Notifier {
    private const val URGENT = "urgent"
    private const val INFO = "info"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(URGENT, "تنفيذ الخطة (هدف أو وقف)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "وصل السعر لهدف بيع أو كسر الوقف"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(INFO, "تحديثات الخطة", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "اقتراب من هدف أو ارتفاع الوقف المتحرك"
            }
        )
    }

    fun send(context: Context, alert: Alert) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val (channel, title, text) = when (alert) {
            is Alert.TargetHit -> Triple(
                URGENT, "${alert.symbol} وصل هدفك ${money(alert.target.price)}",
                "بع ${alert.target.qty} سهم الحين حسب خطتك. السعر ${money(alert.price)}"
            )
            is Alert.StopHit -> Triple(
                URGENT, "${alert.symbol} كسر الوقف ${money(alert.stop.price)}",
                "الخطة تقول: بع ${alert.stop.qty} سهم. السعر ${money(alert.price)}"
            )
            is Alert.TargetApproaching -> Triple(
                INFO, "${alert.symbol} قريب من الهدف ${money(alert.target.price)}",
                "السعر ${money(alert.price)}. جهّز أمر بيع ${alert.target.qty} سهم"
            )
            is Alert.StopRaised -> Triple(
                INFO, "${alert.symbol}: ارفع الوقف إلى ${money(alert.newPrice)}",
                "حدّث أمر الوقف عند الوسيط من ${money(alert.oldPrice)} إلى ${money(alert.newPrice)}"
            )
        }
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (channel == URGENT) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (channel == URGENT) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify((title + System.nanoTime()).hashCode(), n)
    }
}
