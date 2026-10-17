package com.satya.biker.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

fun Context.hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasLocationPermission() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
    hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

object ImageUtils {
    /** Target size per KYC image so all five fit comfortably inside one 1 MB Firestore document. */
    private const val MAX_BYTES = 170_000
    private const val MAX_DIMENSION = 1280

    suspend fun compress(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = decode(context, uri)
        var dim = MAX_DIMENSION
        var quality = 80
        var out: ByteArray
        do {
            val scaled = scale(bitmap, dim)
            out = ByteArrayOutputStream().use { bos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos); bos.toByteArray()
            }
            if (quality > 45) quality -= 10 else dim = (dim * 0.8).toInt()
        } while (out.size > MAX_BYTES && dim > 320)
        out
    }

    private fun decode(context: Context, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = max(info.size.width, info.size.height)
                if (largest > 2400) {
                    val ratio = 2400f / largest
                    d.setTargetSize((info.size.width * ratio).toInt(), (info.size.height * ratio).toInt())
                }
            }
        } else {
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: error("Unable to read image")
        }

    private fun scale(src: Bitmap, maxDim: Int): Bitmap {
        val largest = max(src.width, src.height)
        if (largest <= maxDim) return src
        val ratio = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
    }

    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun toBitmap(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

object LocationUtils {
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!context.hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        val fresh = withTimeoutOrNull(12_000) {
            runCatching { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await() }.getOrNull()
        }
        if (fresh == null) cts.cancel()
        return fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
    }

    fun mapsLink(lat: Double, lng: Double) = "https://maps.google.com/?q=$lat,$lng"
}

object DeviceUtils {
    fun batteryPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

}

object Intents {
    fun dial(context: Context, phone: String) = start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))

    /** Opens the SMS app with [message] ready to send to all [phones] (no SMS permission needed). */
    fun composeSms(context: Context, phones: List<String>, message: String): Boolean {
        val to = phones.map { it.trim() }.filter { it.isNotBlank() }
        if (to.isEmpty()) return false
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + to.joinToString(";")))
            .putExtra("sms_body", message)
        return start(context, intent)
    }

    fun openMap(context: Context, lat: Double, lng: Double, label: String = "") {
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        if (!start(context, Intent(Intent.ACTION_VIEW, uri))) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(LocationUtils.mapsLink(lat, lng))))
        }
    }

    fun navigateTo(context: Context, lat: Double, lng: Double) {
        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=l")
        if (!start(context, Intent(Intent.ACTION_VIEW, uri))) openMap(context, lat, lng)
    }

    fun searchNearby(context: Context, what: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(what)}")
        if (!start(context, Intent(Intent.ACTION_VIEW, uri))) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/${Uri.encode(what)}")))
        }
    }

    fun shareText(context: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(context, Intent.createChooser(send, subject))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

object Formats {
    private val dateTime = SimpleDateFormat("EEE, d MMM yyyy • h:mm a", Locale.getDefault())
    private val time = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    fun dateTime(ts: Timestamp?) = ts?.toDate()?.let { dateTime.format(it) } ?: "—"
    fun dateTime(millis: Long) = dateTime.format(Date(millis))
    fun time(ts: Timestamp?) = ts?.toDate()?.let { time.format(it) } ?: ""
    fun date(ts: Timestamp?) = ts?.toDate()?.let { date.format(it) } ?: "—"

    fun ago(ts: Timestamp?): String {
        val t = ts?.toDate()?.time ?: return "—"
        val s = (System.currentTimeMillis() - t) / 1000
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86400 -> "${s / 3600} h ago"
            else -> "${s / 86400} d ago"
        }
    }
}
