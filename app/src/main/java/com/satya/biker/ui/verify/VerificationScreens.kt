package com.satya.biker.ui.verify

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satya.biker.data.AuthRepository
import com.satya.biker.data.KycDoc
import com.satya.biker.data.RiderProfile
import com.satya.biker.ui.rider.DeleteAccountDialog
import com.satya.biker.data.UserRepository
import com.satya.biker.data.VerificationStatus
import com.satya.biker.ui.components.BytesImage
import com.satya.biker.ui.components.InfoRow
import com.satya.biker.ui.components.PrimaryButton
import com.satya.biker.ui.components.SectionCard
import com.satya.biker.ui.theme.PendingAmber
import com.satya.biker.ui.theme.SafeGreen
import com.satya.biker.ui.theme.SosRed
import com.satya.biker.util.Formats
import com.satya.biker.util.ImageUtils
import com.satya.biker.util.toast
import kotlinx.coroutines.launch

private val STEPS = listOf("Basic details", "ID & licence", "Bike & safety", "Documents", "Review")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationWizard(initial: RiderProfile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var form by remember(initial.uid) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    val docs by remember(initial.uid) { UserRepository.observeKyc(initial.uid) }.collectAsState(emptyMap())

    fun next() {
        error = when (step) {
            0 -> ProfileValidation.personal(form)
            1 -> ProfileValidation.identity(form)
            2 -> ProfileValidation.bikeAndSafety(form)
            3 -> KycDoc.entries.firstOrNull { it.required && it !in docs }?.let { "Upload: ${it.label}" }
            else -> if (!consent) "Please confirm the declaration" else null
        }
        if (error != null) return
        saving = true
        scope.launch {
            runCatching {
                UserRepository.saveDetails(form)
                if (step == STEPS.lastIndex) UserRepository.submitForVerification(form.uid)
            }.onSuccess {
                if (step < STEPS.lastIndex) step++ else context.toast("Submitted for verification")
            }.onFailure { error = it.localizedMessage }
            saving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rider verification", fontWeight = FontWeight.Bold)
                        Text("Step ${step + 1} of ${STEPS.size} • ${STEPS[step]}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { AuthRepository.signOut() }) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                error?.let { Text(it, color = SosRed, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { error = null; step-- },
                            modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp),
                        ) { Text("Back") }
                    }
                    PrimaryButton(
                        text = if (step == STEPS.lastIndex) "Submit for verification" else "Save & continue",
                        onClick = ::next, loading = saving, modifier = Modifier.weight(2f),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1f) / STEPS.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            if (initial.verificationStatus == VerificationStatus.REJECTED) {
                Banner(
                    Icons.Default.ErrorOutline, SosRed, "Your previous submission was rejected",
                    initial.rejectionReason.ifBlank { "Please review your details and documents, then submit again." },
                )
            } else if (step == 0) {
                Banner(
                    Icons.Default.VerifiedUser, MaterialTheme.colorScheme.primary, "Why we verify riders",
                    "Every BikersThrill rider is verified so groups know exactly who they're riding with. " +
                        "Your documents are only visible to our verification team.",
                )
            }
            when (step) {
                0 -> PersonalSection(form) { form = it }
                1 -> IdentitySection(form) { form = it }
                2 -> {
                    BikeSection(form) { form = it }
                    EmergencySection(form) { form = it }
                }
                3 -> DocumentsStep(form.uid, docs)
                else -> ReviewStep(form, docs, consent) { consent = it }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, body: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = color)
                Text(body, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun DocumentsStep(uid: String, docs: Map<KycDoc, ByteArray>) {
    Text(
        "Take clear photos in good light. All four corners of the card should be visible and text readable.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
    )
    KycDoc.entries.forEach { DocumentSlot(uid, it, docs[it]) }
}

@Composable
private fun DocumentSlot(uid: String, doc: KycDoc, bytes: ByteArray?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun upload(uri: Uri) {
        uploading = true
        scope.launch {
            runCatching {
                val jpeg = ImageUtils.compress(context, uri)
                UserRepository.uploadKycDoc(uid, doc, jpeg)
            }.onFailure { context.toast("Upload failed: ${it.localizedMessage}") }
            uploading = false
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::upload) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) captureUri?.let { upload(Uri.parse(it)) }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(96.dp, 68.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uploading -> CircularProgressIndicator(Modifier.size(26.dp))
                    bytes != null -> BytesImage(bytes, Modifier.fillMaxSize())
                    else -> Icon(
                        if (doc == KycDoc.SELFIE) Icons.Default.CameraAlt else Icons.Default.PhotoLibrary, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(doc.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (bytes != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.CheckCircle, null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                    }
                }
                Text(if (doc.required) "Required" else "Optional", fontSize = 12.sp,
                    color = if (doc.required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = {
                        val uri = ImageUtils.newCaptureUri(context)
                        captureUri = uri.toString()
                        camera.launch(uri)
                    }, enabled = !uploading) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp)); Text(" Camera")
                    }
                    if (doc != KycDoc.SELFIE) {
                        TextButton(onClick = {
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, enabled = !uploading) {
                            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Text(" Gallery")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(p: RiderProfile, docs: Map<KycDoc, ByteArray>, consent: Boolean, onConsent: (Boolean) -> Unit) {
    SectionCard(title = "Personal") {
        InfoRow("Name", p.fullName); InfoRow("Mobile", p.phone); InfoRow("Date of birth", p.dateOfBirth)
        InfoRow("Gender", p.gender); InfoRow("Blood group", p.bloodGroup)
        InfoRow("Address", listOf(p.address, p.city, p.state, p.pincode).filter { it.isNotBlank() }.joinToString(", "))
    }
    SectionCard(title = "ID & licence") {
        InfoRow(p.idType.ifBlank { "ID" }, p.idNumber); InfoRow("Licence no.", p.licenseNumber)
        InfoRow("Valid till", p.licenseExpiry); InfoRow("Experience", p.ridingExperienceYears.let { if (it.isBlank()) "" else "$it years" })
    }
    SectionCard(title = "Bike & emergency") {
        InfoRow("Bike", p.bikeLabel); InfoRow("Registration", p.bikeRegNumber)
        p.emergencyContacts.filter { it.name.isNotBlank() }.forEach { InfoRow(it.relation.ifBlank { "Contact" }, "${it.name} • ${it.phone}") }
    }
    SectionCard(title = "Documents") {
        KycDoc.entries.forEach { d ->
            InfoRow(d.label, if (d in docs) "Uploaded ✓" else if (d.required) "Missing" else "Not provided")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(consent, onConsent)
        Text(
            "I confirm these details are true and the documents belong to me. I agree to share my location with my groups during rides.",
            fontSize = 13.sp,
        )
    }
}

@Composable
fun PendingScreen(p: RiderProfile) {
    StatusScreen(
        icon = Icons.Default.HourglassTop,
        color = PendingAmber,
        title = "Verification in progress",
        message = "Thanks ${p.fullName.substringBefore(' ')}! Our team is reviewing your ID and driving licence. " +
            "This usually takes less than 24 hours. You'll be able to use BikersThrill as soon as you're approved.",
        footer = "Submitted ${Formats.dateTime(p.submittedAt)}",
        profile = p,
    )
}

@Composable
fun BlockedScreen(p: RiderProfile) {
    StatusScreen(
        icon = Icons.Default.Block,
        color = SosRed,
        title = "Account suspended",
        message = p.rejectionReason.ifBlank { "Your account has been suspended by an administrator." } +
            "\n\nIf you think this is a mistake, please contact support.",
        footer = null,
        profile = p,
    )
}

@Composable
private fun StatusScreen(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    title: String,
    message: String,
    footer: String?,
    profile: RiderProfile,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(110.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        footer?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = { AuthRepository.signOut() }) {
            Icon(Icons.AutoMirrored.Filled.Logout, null); Text("  Sign out")
        }
        TextButton(onClick = { confirmDelete = true }) { Text("Delete account", color = SosRed) }
    }
    if (confirmDelete) DeleteAccountDialog(profile, onDismiss = { confirmDelete = false })
}
