package com.satya.biker.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.satya.biker.BikerApp
import com.satya.biker.MainActivity
import com.satya.biker.R
import com.satya.biker.data.AuthRepository
import com.satya.biker.data.UserRepository

/**
 * Receives push messages (e.g. from a Cloud Function that fans out SOS alerts or verification
 * results) and keeps the rider's FCM token up to date.
 */
class BikerMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AuthRepository.currentUid?.let { UserRepository.updateFcmToken(it, token) }
    }

    @SuppressLint("MissingPermission")
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val isSos = message.data["type"] == "SOS"
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, if (isSos) BikerApp.CHANNEL_SOS else BikerApp.CHANNEL_GENERAL)
            .setSmallIcon(if (isSos) R.drawable.ic_stat_sos else R.drawable.ic_stat_ride)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(if (isSos) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), n) }
    }
}
