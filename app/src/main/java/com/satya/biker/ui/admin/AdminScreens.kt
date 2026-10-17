package com.satya.biker.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.satya.biker.data.AdminRepository
import com.satya.biker.data.AuthRepository
import com.satya.biker.data.KycDoc
import com.satya.biker.data.RiderProfile
import com.satya.biker.data.SosAlert
import com.satya.biker.data.UserRepository
import com.satya.biker.data.VerificationStatus
import com.satya.biker.ui.components.AppTextField
import com.satya.biker.ui.components.Avatar
import com.satya.biker.ui.components.BytesImage
import com.satya.biker.ui.components.EmptyState
import com.satya.biker.ui.components.InfoRow
import com.satya.biker.ui.components.LoadingScreen
import com.satya.biker.ui.components.Pill
import com.satya.biker.ui.components.PrimaryButton
import com.satya.biker.ui.components.SectionCard
import com.satya.biker.ui.components.StatusChip
import com.satya.biker.ui.theme.BrandOrange
import com.satya.biker.ui.theme.PendingAmber
import com.satya.biker.ui.theme.SafeGreen
import com.satya.biker.ui.theme.SosRed
import com.satya.biker.util.Formats
import com.satya.biker.util.Intents
import com.satya.biker.util.toast
import kotlinx.coroutines.launch

@Composable
fun AdminApp(admin: RiderProfile) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") { AdminHome(admin, onOpen = { nav.navigate("review/$it") }) }
        composable("review/{uid}") { entry ->
            RiderReviewScreen(entry.arguments?.getString("uid").orEmpty(), admin, onBack = { nav.popBackStack() })
        }
    }
}

private enum class AdminTab(val label: String, val status: VerificationStatus?) {
    PENDING("Pending", VerificationStatus.PENDING),
    APPROVED("Verified", VerificationStatus.APPROVED),
    REJECTED("Rejected", VerificationStatus.REJECTED),
    SUSPENDED("Suspended", VerificationStatus.SUSPENDED),
    INCOMPLETE("Incomplete", VerificationStatus.INCOMPLETE),
    ALL("All users", null),
    ADMINS("Admins", null),
    SOS("Live SOS", null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminHome(admin: RiderProfile, onOpen: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.PENDING) }
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val all by remember { AdminRepository.observeAllUsers() }.collectAsState(null)
    val sos by remember { AdminRepository.observeActiveSos() }.collectAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin console", fontWeight = FontWeight.Bold)
                        Text(admin.email + if (admin.isSuperAdmin) " • Super admin" else "", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { all?.let { exportUsersCsv(context, it) } }) { Icon(Icons.Default.Download, "Export all users (CSV)") }
                    IconButton(onClick = { AuthRepository.signOut() }) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val everyone = all
            val riders = everyone?.filter { !it.isAdmin }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Pending", riders?.count { it.verificationStatus == VerificationStatus.PENDING }, PendingAmber, Modifier.weight(1f))
                StatTile("Verified", riders?.count { it.verificationStatus == VerificationStatus.APPROVED }, SafeGreen, Modifier.weight(1f))
                StatTile("Active SOS", sos.size, SosRed, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 12.dp) {
                AdminTab.entries.forEach { t ->
                    val count = when (t) {
                        AdminTab.SOS -> sos.size
                        AdminTab.ALL -> everyone?.size ?: 0
                        AdminTab.ADMINS -> everyone?.count { it.isAdmin } ?: 0
                        else -> riders?.count { it.verificationStatus == t.status } ?: 0
                    }
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(if (count > 0) "${t.label} ($count)" else t.label) })
                }
            }
            if (tab == AdminTab.SOS) {
                SosList(sos)
                return@Column
            }
            AppTextField(query, { query = it }, "Search name, phone, licence or bike no.",
                leading = Icons.Default.Search, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
            if (everyone == null || riders == null) {
                LoadingScreen(); return@Column
            }
            val q = query.trim().lowercase()
            val base = when (tab) {
                AdminTab.ALL -> everyone
                AdminTab.ADMINS -> everyone.filter { it.isAdmin }
                else -> riders.filter { it.verificationStatus == tab.status }
            }
            val list = base.filter {
                q.isEmpty() || listOf(it.fullName, it.phone, it.email, it.licenseNumber, it.bikeRegNumber)
                    .any { f -> f.lowercase().contains(q) }
            }
            if (list.isEmpty()) {
                EmptyState(Icons.Default.Inbox, "Nothing here", if (tab == AdminTab.PENDING) "No riders waiting for review." else "No riders in this list.")
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.uid }) { r -> RiderRow(r) { onOpen(r.uid) } }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int?, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(14.dp)) {
            Text(value?.toString() ?: "–", fontSize = 26.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RiderRow(r: RiderProfile, onClick: () -> Unit) {
    Card(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.initials)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.fullName.ifBlank { r.email }, fontWeight = FontWeight.SemiBold)
                Text(listOf(r.phone, r.bikeRegNumber).filter { it.isNotBlank() }.joinToString(" • "),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Submitted ${Formats.ago(r.submittedAt ?: r.createdAt)}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                r.isSuperAdmin -> Pill("Super admin", BrandOrange)
                r.isAdmin -> Pill("Admin", BrandOrange)
                else -> StatusChip(r.verificationStatus)
            }
        }
    }
}

@Composable
private fun SosList(alerts: List<SosAlert>) {
    val context = LocalContext.current
    if (alerts.isEmpty()) {
        EmptyState(Icons.Default.Warning, "No active SOS", "All riders are safe right now."); return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(alerts, key = { it.id }) { a ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SosRed.copy(alpha = 0.12f))) {
                Column(Modifier.padding(14.dp)) {
                    Text("🚨 ${a.name}", fontWeight = FontWeight.Bold, color = SosRed)
                    Text(a.reason, fontSize = 14.sp)
                    Text("Blood ${a.bloodGroup.ifBlank { "—" }} • ${a.bikeRegNumber} • ${Formats.ago(a.createdAt)}", fontSize = 12.sp)
                    Row {
                        TextButton(onClick = { Intents.dial(context, a.phone) }) { Icon(Icons.Default.Call, null); Text(" Call rider") }
                        TextButton(onClick = { Intents.openMap(context, a.lat, a.lng, a.name) }) {
                            Icon(Icons.Default.LocationOn, null); Text(" Location")
                        }
                    }
                }
            }
        }
    }
}

private val REJECT_REASONS = listOf(
    "Document photo is blurry or unreadable",
    "Name doesn't match the documents",
    "Driving licence is expired",
    "Selfie doesn't match the ID photo",
    "Required document missing",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RiderReviewScreen(uid: String, admin: RiderProfile, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rider by remember(uid) { UserRepository.observeProfile(uid) }.collectAsState(null)
    val docs by remember(uid) { UserRepository.observeKyc(uid) }.collectAsState(emptyMap())
    val userGroups by remember(uid) { AdminRepository.observeUserGroups(uid) }.collectAsState(emptyList())
    val userSos by remember(uid) { AdminRepository.observeUserSos(uid) }.collectAsState(emptyList())
    var confirmRole by remember { mutableStateOf<Boolean?>(null) }
    var zoom by remember { mutableStateOf<ByteArray?>(null) }
    var reasonFor by remember { mutableStateOf<VerificationStatus?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun act(block: suspend () -> Unit, done: String) {
        busy = true
        scope.launch {
            runCatching { block() }.onSuccess { context.toast(done); onBack() }
                .onFailure { context.toast(it.localizedMessage ?: "Failed") }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            val r = rider ?: return@Scaffold
            if (r.isAdmin) return@Scaffold
            Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (r.verificationStatus) {
                    VerificationStatus.APPROVED -> PrimaryButton(
                        "Suspend rider", { reasonFor = VerificationStatus.SUSPENDED }, loading = busy,
                        icon = Icons.Default.Block, color = SosRed,
                    )
                    VerificationStatus.SUSPENDED -> PrimaryButton(
                        "Reinstate rider", { act({ AdminRepository.approve(uid) }, "Rider reinstated") },
                        loading = busy, icon = Icons.Default.Check, color = SafeGreen,
                    )
                    else -> {
                        OutlinedButton(
                            onClick = { reasonFor = VerificationStatus.REJECTED }, enabled = !busy,
                            modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SosRed),
                        ) { Icon(Icons.Default.Close, null); Text(" Reject") }
                        PrimaryButton(
                            "Approve", { act({ AdminRepository.approve(uid) }, "Rider approved") },
                            loading = busy, icon = Icons.Default.Check, color = SafeGreen, modifier = Modifier.weight(1.4f),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val r = rider
        if (r == null) {
            LoadingScreen(); return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (docs[KycDoc.SELFIE] != null) {
                    BytesImage(docs[KycDoc.SELFIE], Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).clickable { zoom = docs[KycDoc.SELFIE] })
                } else Avatar(r.initials, 72.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.fullName, style = MaterialTheme.typography.titleLarge)
                    Text(r.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(r.verificationStatus)
                        if (r.isSuperAdmin) Pill("Super admin", BrandOrange) else if (r.isAdmin) Pill("Admin", BrandOrange)
                    }
                }
                IconButton(onClick = { Intents.dial(context, r.phone) }) { Icon(Icons.Default.Call, "Call") }
            }
            if (r.rejectionReason.isNotBlank()) {
                Text("Last note: ${r.rejectionReason}", color = SosRed, fontSize = 14.sp)
            }

            SectionCard(title = "Documents (tap to zoom)") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(KycDoc.entries) { d ->
                        Column(Modifier.width(170.dp)) {
                            val bytes = docs[d]
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(enabled = bytes != null) { zoom = bytes },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (bytes != null) BytesImage(bytes, Modifier.fillMaxSize())
                                else Text("Not uploaded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(d.label, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            SectionCard(title = "Personal") {
                InfoRow("Mobile", r.phone); InfoRow("Date of birth", r.dateOfBirth); InfoRow("Gender", r.gender)
                InfoRow("Blood group", r.bloodGroup)
                InfoRow("Address", listOf(r.address, r.city, r.state, r.pincode).filter { it.isNotBlank() }.joinToString(", "))
            }
            SectionCard(title = "Identity") {
                InfoRow(r.idType.ifBlank { "ID" }, r.idNumber); InfoRow("Licence no.", r.licenseNumber)
                InfoRow("Licence valid till", r.licenseExpiry); InfoRow("Experience", r.ridingExperienceYears.let { if (it.isBlank()) "" else "$it years" })
            }
            SectionCard(title = "Bike & emergency") {
                InfoRow("Bike", r.bikeLabel); InfoRow("Registration", r.bikeRegNumber)
                r.emergencyContacts.forEach { InfoRow(it.relation.ifBlank { "Contact" }, "${it.name} • ${it.phone}") }
                InfoRow("Medical notes", r.medicalNotes)
            }
            // Any admin can grant or remove admin; the super admin account can never be changed.
            if (r.uid != admin.uid && !r.isSuperAdmin) {
                SectionCard(title = "Admin access") {
                    Text(
                        if (r.isAdmin) "${r.fullName.ifBlank { r.email }} can review riders and see all user details."
                        else "Admins can approve riders, see every user's details and documents, and watch live SOS.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        if (r.isAdmin) "Remove admin access" else "Make admin",
                        { confirmRole = !r.isAdmin }, loading = busy,
                        icon = Icons.Default.AdminPanelSettings, color = if (r.isAdmin) SosRed else BrandOrange,
                    )
                }
            }
            SectionCard(title = "Groups (${userGroups.size})") {
                if (userGroups.isEmpty()) Text("Not in any group", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                userGroups.forEach { g ->
                    InfoRow(g.name, "${g.members[uid]?.role ?: "Member"} • ${g.memberIds.size} riders • code ${g.code}")
                }
            }
            SectionCard(title = "SOS history (${userSos.size})") {
                if (userSos.isEmpty()) Text("No SOS raised", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                userSos.take(20).forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.reason.ifBlank { "SOS" }, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(Formats.dateTime(a.createdAt) + if (a.active) " • ACTIVE" else " • resolved",
                                fontSize = 12.sp, color = if (a.active) SosRed else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (a.lat != 0.0) IconButton(onClick = { Intents.openMap(context, a.lat, a.lng, a.name) }) {
                            Icon(Icons.Default.LocationOn, "Location")
                        }
                    }
                }
            }
            SectionCard(title = "Account") {
                InfoRow("User ID", r.uid); InfoRow("Role", if (r.isSuperAdmin) "Super admin" else r.role)
                InfoRow("Status", r.status)
            }
            SectionCard(title = "Audit") {
                InfoRow("Registered", Formats.dateTime(r.createdAt)); InfoRow("Submitted", Formats.dateTime(r.submittedAt))
                InfoRow("Last reviewed", Formats.dateTime(r.reviewedAt)); InfoRow("Reviewed by", r.reviewedBy)
            }
        }
    }

    zoom?.let { bytes ->
        Dialog(onDismissRequest = { zoom = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { zoom = null }, contentAlignment = Alignment.Center) {
                BytesImage(
                    bytes,
                    Modifier.fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, z, _ ->
                                scale = (scale * z).coerceIn(1f, 5f); offsetX += pan.x; offsetY += pan.y
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }

    confirmRole?.let { makeAdmin ->
        val name = rider?.fullName?.ifBlank { rider?.email } ?: "this user"
        AlertDialog(
            onDismissRequest = { confirmRole = null },
            title = { Text(if (makeAdmin) "Make $name an admin?" else "Remove admin access?") },
            text = {
                Text(
                    if (makeAdmin) "They'll be able to approve or reject riders and see every user's details and documents."
                    else "$name will go back to being a normal rider.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRole = null
                    act({ AdminRepository.setAdmin(uid, makeAdmin) }, if (makeAdmin) "Admin access granted" else "Admin access removed")
                }) { Text("Confirm", color = if (makeAdmin) BrandOrange else SosRed) }
            },
            dismissButton = { TextButton(onClick = { confirmRole = null }) { Text("Cancel") } },
        )
    }

    reasonFor?.let { target ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reasonFor = null },
            title = { Text(if (target == VerificationStatus.REJECTED) "Reject verification" else "Suspend rider") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The rider will see this reason.", fontSize = 14.sp)
                    if (target == VerificationStatus.REJECTED) {
                        REJECT_REASONS.forEach { r ->
                            FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r, fontSize = 12.sp) })
                        }
                    }
                    AppTextField(reason, { reason = it }, "Reason", singleLine = false, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank(), onClick = {
                    reasonFor = null
                    if (target == VerificationStatus.REJECTED) act({ AdminRepository.reject(uid, reason) }, "Rider rejected")
                    else act({ AdminRepository.suspendRider(uid, reason) }, "Rider suspended")
                }) { Text("Confirm", color = SosRed) }
            },
            dismissButton = { TextButton(onClick = { reasonFor = null }) { Text("Cancel") } },
        )
    }
}

/** Writes every user's profile to a CSV file and opens the share sheet (save to Drive, email, etc.). */
private fun exportUsersCsv(context: android.content.Context, users: List<RiderProfile>) {
    fun esc(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
    val header = listOf(
        "Name", "Email", "Phone", "Role", "Status", "Date of birth", "Gender", "Blood group", "Address", "City",
        "State", "PIN", "ID type", "ID number", "Licence no.", "Licence valid till", "Experience (yrs)", "Bike",
        "Registration", "Emergency contacts", "Medical notes", "Registered", "Submitted", "Reviewed", "Reviewed by",
        "Rejection reason", "User ID",
    )
    val rows = users.map { u ->
        listOf(
            u.fullName, u.email, u.phone, if (u.isSuperAdmin) "super admin" else u.role, u.status, u.dateOfBirth, u.gender,
            u.bloodGroup, u.address, u.city, u.state, u.pincode, u.idType, u.idNumber, u.licenseNumber, u.licenseExpiry,
            u.ridingExperienceYears, u.bikeLabel, u.bikeRegNumber,
            u.emergencyContacts.joinToString("; ") { "${it.name} (${it.relation}) ${it.phone}" },
            u.medicalNotes, Formats.dateTime(u.createdAt), Formats.dateTime(u.submittedAt), Formats.dateTime(u.reviewedAt),
            u.reviewedBy, u.rejectionReason, u.uid,
        )
    }
    val csv = (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { esc(it) } }
    val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
    val file = java.io.File(dir, "bikersthrill_users.csv").apply { writeText("﻿" + csv) }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, "BikersThrill users (${users.size})")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(send, "Export users"))
}
