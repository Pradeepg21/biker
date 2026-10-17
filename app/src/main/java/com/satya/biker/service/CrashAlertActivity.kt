package com.satya.biker.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.satya.biker.BikerApp
import com.satya.biker.R
import com.satya.biker.data.AuthRepository
import com.satya.biker.data.UserRepository
import com.satya.biker.ui.theme.BikerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen 30-second countdown shown after a suspected crash. No response sends SOS automatically. */
class CrashAlertActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        startAlarm()
        setContent { BikerTheme { CrashCountdown(onDone = { stopAlarm(); finish() }) } }
    }

    private fun startAlarm() {
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            isLooping = true
            play()
        }
        vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0))
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator()?.cancel()
    }

    private fun vibrator(): Vibrator? =
        (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    @Composable
    private fun CrashCountdown(onDone: () -> Unit) {
        var seconds by remember { mutableIntStateOf(COUNTDOWN_SECONDS) }
        var status by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        fun sendSos() {
            if (status != null) return
            status = "Sending SOS…"
            stopAlarm()
            scope.launch {
                val uid = AuthRepository.currentUid
                val me = uid?.let { runCatching { UserRepository.getProfile(it) }.getOrNull() }
                status = if (me != null) {
                    val r = SosManager.trigger(this@CrashAlertActivity, me, "Crash detected – rider not responding")
                    SosManager.textContacts(this@CrashAlertActivity, me, r)
                    "SOS sent to ${r.groupsAlerted} group(s)."
                } else "Could not send SOS – not signed in."
                delay(4000)
                onDone()
            }
        }

        LaunchedEffect(Unit) {
            while (seconds > 0 && status == null) {
                delay(1000); seconds--
            }
            if (status == null) sendSos()
        }

        Column(
            Modifier.fillMaxSize().background(Color(0xFFB71C1C)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("CRASH DETECTED", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text(
                status ?: "Are you OK? An SOS with your location will be sent to your group and emergency contacts in",
                color = Color.White, textAlign = TextAlign.Center, fontSize = 17.sp,
            )
            Spacer(Modifier.height(24.dp))
            if (status == null) {
                Text("$seconds", color = Color.White, fontSize = 110.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        status = "Glad you're safe. Alert cancelled."; stopAlarm()
                        scope.launch { delay(1500); onDone() }
                    },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1B5E20)),
                ) { Text("I'M OK", fontSize = 28.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = ::sendSos,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("SEND SOS NOW", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }

    companion object {
        private const val COUNTDOWN_SECONDS = 30
        private const val NOTIFICATION_ID = 77

        /** Launches through a full-screen notification so it works from the background and lock screen. */
        @SuppressLint("MissingPermission")
        fun launch(context: Context) {
            val intent = Intent(context, CrashAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, BikerApp.CHANNEL_SOS)
                .setSmallIcon(R.drawable.ic_stat_sos)
                .setContentTitle("Crash detected")
                .setContentText("Tap if you're OK. SOS will be sent automatically in 30 s.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
            runCatching { context.startActivity(intent) }
        }
    }
}
