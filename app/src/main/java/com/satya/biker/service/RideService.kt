package com.satya.biker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.satya.biker.BikerApp
import com.satya.biker.MainActivity
import com.satya.biker.R
import com.satya.biker.data.AuthRepository
import com.satya.biker.data.GroupRepository
import com.satya.biker.data.LiveLocation
import com.satya.biker.data.LocationRepository
import com.satya.biker.data.RiderProfile
import com.satya.biker.data.SosRepository
import com.satya.biker.data.UserRepository
import com.satya.biker.util.DeviceUtils
import com.satya.biker.util.hasLocationPermission
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

data class RideStats(
    val active: Boolean = false,
    val startedAt: Long = 0L,
    val distanceKm: Double = 0.0,
    val speedKmh: Double = 0.0,
    val topSpeedKmh: Double = 0.0,
    val lat: Double? = null,
    val lng: Double? = null,
)

/**
 * Foreground service that runs for the length of a ride: shares live location with the rider's
 * groups, detects crashes from the accelerometer and raises alerts when a group member sends SOS.
 */
class RideService : LifecycleService(), SensorEventListener {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val sensors by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val settings by lazy { SafetySettings(this) }
    private var profile: RiderProfile? = null
    private var lastLocation: Location? = null

    // Crash detection state
    private var impactAt = 0L
    private var stillSamples = 0
    private var movingSamples = 0
    private var lastCrashAlertAt = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopRide(); return START_NOT_STICKY
            }
            ACTION_SOS -> lifecycleScope.launch { sendSos("SOS from ride notification") }
            else -> startRide()
        }
        return START_STICKY
    }

    private fun startRide() {
        val uid = AuthRepository.currentUid
        if (uid == null || !hasLocationPermission()) {
            stopSelf(); return
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (_stats.value.active) return
        _stats.value = RideStats(active = true, startedAt = System.currentTimeMillis())

        lifecycleScope.launch { profile = UserRepository.getProfile(uid) }
        requestLocationUpdates()
        if (settings.crashDetection) {
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        watchGroupSos(uid)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun onLocation(loc: Location) {
        val prev = lastLocation
        val added = if (prev != null && loc.accuracy < 50) prev.distanceTo(loc) / 1000.0 else 0.0
        if (prev == null || loc.accuracy < 50) lastLocation = loc
        val speed = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
        _stats.value = _stats.value.let {
            it.copy(
                distanceKm = it.distanceKm + added,
                speedKmh = speed,
                topSpeedKmh = maxOf(it.topSpeedKmh, speed),
                lat = loc.latitude,
                lng = loc.longitude,
            )
        }
        val p = profile ?: return
        LocationRepository.publish(
            LiveLocation(
                uid = p.uid, name = p.fullName, bike = p.bikeLabel,
                lat = loc.latitude, lng = loc.longitude, speedKmh = speed,
                battery = DeviceUtils.batteryPercent(this),
            ),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchGroupSos(uid: String) {
        lifecycleScope.launch {
            GroupRepository.observeMyGroups(uid)
                .map { groups -> groups.map { it.id } }
                .distinctUntilChanged()
                .flatMapLatest { SosRepository.observeForGroups(it) }
                .filterNotNull()
                .collect { alerts -> alerts.forEach { SosNotifier.notify(this@RideService, it, uid) } }
        }
    }

    // ---- Crash detection -------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = SystemClock.elapsedRealtime()
        if (now - lastCrashAlertAt < 60_000) return

        if (impactAt == 0L) {
            if (g > settings.impactThresholdG) {
                impactAt = now; stillSamples = 0; movingSamples = 0
            }
            return
        }
        // After the impact, give the phone 1.5 s to settle, then watch for ~4 s of stillness.
        if (now - impactAt < 1_500) return
        if (abs(g - 1f) < 0.25f) stillSamples++ else movingSamples++
        if (now - impactAt > 5_500) {
            val stillRatio = stillSamples.toFloat() / (stillSamples + movingSamples).coerceAtLeast(1)
            val slow = _stats.value.speedKmh < 8
            impactAt = 0L
            if (stillRatio > 0.7f && slow) {
                lastCrashAlertAt = now
                CrashAlertActivity.launch(this)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // ---- SOS / stop ------------------------------------------------------------------------

    private suspend fun sendSos(reason: String) {
        val p = profile ?: AuthRepository.currentUid?.let { UserRepository.getProfile(it) } ?: return
        SosManager.trigger(this, p, reason)
    }

    private fun stopRide() {
        fused.removeLocationUpdates(locationCallback)
        sensors.unregisterListener(this)
        AuthRepository.currentUid?.let { LocationRepository.stopSharingAsync(it) }
        _stats.value = _stats.value.copy(active = false, speedKmh = 0.0)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(locationCallback)
        sensors.unregisterListener(this)
        _stats.value = _stats.value.copy(active = false)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RideService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val sos = PendingIntent.getService(
            this, 2, Intent(this, RideService::class.java).setAction(ACTION_SOS), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, BikerApp.CHANNEL_RIDE)
            .setSmallIcon(R.drawable.ic_stat_ride)
            .setContentTitle("Ride in progress")
            .setContentText("Sharing live location with your groups • crash detection " +
                if (settings.crashDetection) "on" else "off")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "SOS", sos)
            .addAction(0, "End ride", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val ACTION_START = "com.satya.biker.START_RIDE"
        private const val ACTION_STOP = "com.satya.biker.STOP_RIDE"
        private const val ACTION_SOS = "com.satya.biker.RIDE_SOS"

        private val _stats = MutableStateFlow(RideStats())
        val stats: StateFlow<RideStats> = _stats.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RideService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RideService::class.java).setAction(ACTION_STOP))
        }

        fun cancelNotification(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
