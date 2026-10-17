package com.satya.biker.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.satya.biker.BikerApp
import com.satya.biker.MainActivity
import com.satya.biker.R
import com.satya.biker.data.GroupRepository
import com.satya.biker.data.RiderProfile
import com.satya.biker.data.SosAlert
import com.satya.biker.data.SosRepository
import com.satya.biker.util.Intents
import com.satya.biker.util.LocationUtils
import com.satya.biker.util.hasPermission
import kotlinx.coroutines.flow.first

/** Rider preferences for the safety features. */
class SafetySettings(context: Context) {
    private val prefs = context.getSharedPreferences("safety", Context.MODE_PRIVATE)

    var crashDetection: Boolean
        get() = prefs.getBoolean("crash", true)
        set(v) = prefs.edit().putBoolean("crash", v).apply()

    /** 0 = low (off-road), 1 = medium, 2 = high. */
    var sensitivity: Int
        get() = prefs.getInt("sensitivity", 1)
        set(v) = prefs.edit().putInt("sensitivity", v).apply()

    var smsFallback: Boolean
        get() = prefs.getBoolean("sms", true)
        set(v) = prefs.edit().putBoolean("sms", v).apply()

    /** Impact threshold in g for the chosen sensitivity. */
    val impactThresholdG: Float get() = when (sensitivity) { 0 -> 6.0f; 2 -> 3.5f; else -> 4.5f }
}

data class SosResult(val alertId: String, val groupsAlerted: Int, val hasLocation: Boolean, val smsText: String)

object SosManager {
    /**
     * Raises an SOS: records it in Firestore (which alerts every group the rider belongs to).
     * The returned [SosResult.smsText] is what [textContacts] puts in the rider's SMS app.
     */
    suspend fun trigger(context: Context, me: RiderProfile, reason: String): SosResult {
        val loc = LocationUtils.current(context)
        val lat = loc?.latitude ?: 0.0
        val lng = loc?.longitude ?: 0.0
        val groupIds = runCatching { GroupRepository.observeMyGroups(me.uid).first().map { it.id } }
            .getOrDefault(emptyList())

        val alertId = runCatching { SosRepository.raise(me, lat, lng, reason, groupIds) }.getOrDefault("")

        val where = if (loc != null) LocationUtils.mapsLink(lat, lng) else "location unavailable"
        val msg = buildString {
            append("SOS from ${me.fullName} (BikersThrill). $reason. ")
            append("Location: $where. ")
            if (me.bloodGroup.isNotBlank()) append("Blood group: ${me.bloodGroup}. ")
            if (me.bikeRegNumber.isNotBlank()) append("Bike: ${me.bikeRegNumber}. ")
            append("Call: ${me.phone}")
        }
        return SosResult(alertId, groupIds.size, loc != null, msg)
    }

    /**
     * Opens the SMS app addressed to all emergency contacts with the SOS text, if the rider
     * turned that on. Returns true when the SMS app was opened.
     */
    fun textContacts(context: Context, me: RiderProfile, result: SosResult): Boolean =
        SafetySettings(context).smsFallback &&
            Intents.composeSms(context, me.emergencyContacts.map { it.phone }, result.smsText)
}

/** Shows a loud notification for an SOS raised by another rider; each alert is shown once per process. */
object SosNotifier {
    private val seen = mutableSetOf<String>()

    @SuppressLint("MissingPermission")
    fun notify(context: Context, alert: SosAlert, myUid: String) {
        if (alert.uid == myUid || !seen.add(alert.id)) return
        if (!context.hasPermission(Manifest.permission.POST_NOTIFICATIONS) &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val open = PendingIntent.getActivity(
            context, alert.id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SOS_ID, alert.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, BikerApp.CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_stat_sos)
            .setContentTitle("🚨 SOS: ${alert.name} needs help")
            .setContentText(alert.reason.ifBlank { "Emergency" } + " • tap to see location")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${alert.reason}\nBlood group: ${alert.bloodGroup.ifBlank { "—" }}  •  Bike: ${alert.bikeRegNumber.ifBlank { "—" }}\nPhone: ${alert.phone}",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), n)
    }
}
