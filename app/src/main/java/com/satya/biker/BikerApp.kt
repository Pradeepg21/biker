package com.satya.biker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration

class BikerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            FirebaseFirestore.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 8080)
            FirebaseAuth.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099)
        }
        Configuration.getInstance().userAgentValue = packageName
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val sos = NotificationChannel(CHANNEL_SOS, "SOS alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Emergency alerts from riders in your groups"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setSound(alarm, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            setBypassDnd(true)
        }
        val ride = NotificationChannel(CHANNEL_RIDE, "Ride tracking", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while live location sharing and crash detection are running"
        }
        val general = NotificationChannel(CHANNEL_GENERAL, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Verification status, group messages and ride updates"
        }
        nm.createNotificationChannels(listOf(sos, ride, general))
    }

    companion object {
        const val CHANNEL_SOS = "sos_alerts"
        const val CHANNEL_RIDE = "ride_tracking"
        const val CHANNEL_GENERAL = "general"
    }
}
