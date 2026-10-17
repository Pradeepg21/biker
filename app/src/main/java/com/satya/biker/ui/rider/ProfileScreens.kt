package com.satya.biker.ui.rider

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satya.biker.data.AuthRepository
import com.satya.biker.data.RiderProfile
import com.satya.biker.data.UserRepository
import com.satya.biker.service.RideService
import com.satya.biker.service.SafetySettings
import com.satya.biker.ui.components.Avatar
import com.satya.biker.ui.components.InfoRow
import com.satya.biker.ui.components.PrimaryButton
import com.satya.biker.ui.components.SectionCard
import com.satya.biker.ui.components.StatusChip
import com.satya.biker.ui.theme.SosRed
import com.satya.biker.ui.verify.BikeSection
import com.satya.biker.ui.verify.EmergencySection
import com.satya.biker.ui.verify.ProfileValidation
import com.satya.biker.util.Formats
import com.satya.biker.util.hasPermission
import com.satya.biker.util.toast
import kotlinx.coroutines.launch

@Composable
fun ProfileTab(me: RiderProfile, onEdit: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(me.initials, 88.dp)
            Spacer(Modifier.height(10.dp))
            Text(me.fullName, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(me.email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            StatusChip(me.verificationStatus)
            Text("Verified on ${Formats.date(me.reviewedAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(title = "Contact & personal") {
            InfoRow("Mobile", me.phone); InfoRow("Date of birth", me.dateOfBirth); InfoRow("Blood group", me.bloodGroup)
            InfoRow("City", listOf(me.city, me.state).filter { it.isNotBlank() }.joinToString(", "))
        }
        SectionCard(title = "Licence & bike") {
            InfoRow("Licence no.", me.licenseNumber); InfoRow("Valid till", me.licenseExpiry)
            InfoRow("Bike", me.bikeLabel); InfoRow("Registration", me.bikeRegNumber)
            InfoRow("Experience", me.ridingExperienceYears.let { if (it.isBlank()) "" else "$it years" })
        }
        SectionCard {
            MenuItem(Icons.Default.Edit, "Edit bike & emergency contacts", onEdit)
            MenuItem(Icons.Default.HealthAndSafety, "Safety settings", onSettings)
            MenuItem(Icons.AutoMirrored.Filled.Logout, "Sign out", tint = SosRed) {
                RideService.stop(context); AuthRepository.signOut()
            }
            MenuItem(Icons.Default.DeleteForever, "Delete account", tint = SosRed) { confirmDelete = true }
        }
        Text("BikersThrill v${com.satya.biker.BuildConfig.VERSION_NAME} (${com.satya.biker.BuildConfig.VERSION_CODE}) • Stay connected", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }

    if (confirmDelete) DeleteAccountDialog(me, onDismiss = { confirmDelete = false })
}

/** Confirms and performs in-app account deletion (required by Google Play). */
@Composable
fun DeleteAccountDialog(me: RiderProfile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    AlertDialog(
            onDismissRequest = { if (!deleting) onDismiss() },
            title = { Text("Delete your account?") },
            text = {
                Text("This permanently deletes your profile, ID and licence photos, live location, SOS history and " +
                    "the rides you created, and removes you from your groups. It can't be undone.")
            },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    if (!AuthRepository.signedInRecently()) {
                        onDismiss()
                        context.toast("For your security, sign in again, then delete your account.")
                        RideService.stop(context); AuthRepository.signOut()
                        return@TextButton
                    }
                    deleting = true
                    scope.launch {
                        RideService.stop(context)
                        runCatching { UserRepository.deleteAccount(me) }
                            .onSuccess { context.toast("Your account has been deleted.") }
                            .onFailure {
                                deleting = false; onDismiss()
                                context.toast("Could not delete account: ${it.message}")
                            }
                    }
                }) { Text(if (deleting) "Deleting…" else "DELETE", color = SosRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = onDismiss) { Text("Cancel") } },
        )
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) =
    MenuItem(icon, label, MaterialTheme.colorScheme.onSurface, onClick)

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, color = tint, fontWeight = FontWeight.Medium) },
        leadingContent = { Icon(icon, null, tint = tint) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(me: RiderProfile, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(me) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Edit profile") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                error?.let { Text(it, color = SosRed, modifier = Modifier.padding(bottom = 8.dp)) }
                PrimaryButton("Save changes", loading = busy, onClick = {
                    error = ProfileValidation.bikeAndSafety(form)
                    if (error != null) return@PrimaryButton
                    busy = true
                    scope.launch {
                        runCatching { UserRepository.saveDetails(form) }
                            .onSuccess { context.toast("Profile updated"); onDone() }
                            .onFailure { error = it.localizedMessage }
                        busy = false
                    }
                })
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("  Name, ID and licence details are locked after verification. Contact an admin to change them.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BikeSection(form) { form = it }
            EmergencySection(form) { form = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SafetySettings(context) }
    var crash by remember { mutableStateOf(settings.crashDetection) }
    var sensitivity by remember { mutableIntStateOf(settings.sensitivity) }
    var sms by remember { mutableStateOf(settings.smsFallback) }
    val fullScreenAllowed = remember {
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Safety settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard(title = "Crash detection") {
                SettingSwitch("Detect crashes during rides",
                    "A hard impact followed by no movement starts a 30-second countdown, then sends SOS automatically.", crash) {
                    crash = it; settings.crashDetection = it
                }
                Spacer(Modifier.height(8.dp))
                Text("Sensitivity", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Low (off-road)", "Medium", "High").forEachIndexed { i, l ->
                        FilterChip(sensitivity == i, { sensitivity = i; settings.sensitivity = i }, label = { Text(l) })
                    }
                }
                Text("Changes apply from your next ride.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(title = "SOS by SMS") {
                SettingSwitch("Text emergency contacts",
                    "After an SOS, your SMS app opens with your location ready to send to your emergency contacts.", sms) {
                    sms = it; settings.smsFallback = it
                }
            }
            // Android 14+: full-screen alerts need the user's OK for apps that aren't alarms or calls.
            if (Build.VERSION.SDK_INT >= 34 && !fullScreenAllowed) {
                SectionCard(title = "Crash alert on lock screen") {
                    Text("Allow full-screen alerts so the crash countdown can wake your screen while you ride.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Allow full-screen alerts", color = SosRed, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp).clickable {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:${context.packageName}")))
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
