package com.satya.biker.ui.rider

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.satya.biker.data.GroupRepository
import com.satya.biker.data.RiderProfile
import com.satya.biker.data.SosAlert
import com.satya.biker.data.SosRepository
import com.satya.biker.service.RideService
import com.satya.biker.service.RideStats
import com.satya.biker.service.SosManager
import com.satya.biker.service.SosNotifier
import com.satya.biker.ui.components.Avatar
import com.satya.biker.ui.components.QuickAction
import com.satya.biker.ui.components.SectionCard
import com.satya.biker.ui.theme.BrandOrange
import com.satya.biker.ui.theme.SafeGreen
import com.satya.biker.ui.theme.SosRed
import com.satya.biker.util.Formats
import com.satya.biker.util.Intents
import com.satya.biker.util.LocationUtils
import com.satya.biker.util.hasLocationPermission
import com.satya.biker.util.toast
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    GROUPS("Groups", Icons.Default.Groups),
    RIDES("Rides", Icons.Default.Map),
    PROFILE("Profile", Icons.Default.Person),
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun RiderApp(me: RiderProfile, openSosId: String?) {
    val context = LocalContext.current
    val nav = rememberNavController()

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(wanted.toTypedArray())
    }

    // Alert this rider about any SOS raised in their groups while the app is open.
    val groupSos by remember(me.uid) {
        GroupRepository.observeMyGroups(me.uid).map { g -> g.map { it.id } }.distinctUntilChanged()
            .flatMapLatest { SosRepository.observeForGroups(it) }
    }.collectAsState(emptyList())
    LaunchedEffect(groupSos) { groupSos.forEach { SosNotifier.notify(context, it, me.uid) } }

    NavHost(nav, startDestination = "main") {
        composable("main") {
            MainTabs(me, groupSos, openSosId, nav::navigate)
        }
        composable("group/{id}") { e ->
            GroupDetailScreen(e.arguments?.getString("id").orEmpty(), me, onBack = { nav.popBackStack() },
                onCreateRide = { gid -> nav.navigate("createRide?groupId=$gid") })
        }
        composable("ride/{id}") { e ->
            RideDetailScreen(e.arguments?.getString("id").orEmpty(), me, onBack = { nav.popBackStack() })
        }
        composable(
            "createRide?groupId={groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType; defaultValue = "" }),
        ) { e ->
            CreateRideScreen(me, e.arguments?.getString("groupId").orEmpty(), onDone = { nav.popBackStack() })
        }
        composable("editProfile") { EditProfileScreen(me, onDone = { nav.popBackStack() }) }
        composable("settings") { SafetySettingsScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainTabs(me: RiderProfile, groupSos: List<SosAlert>, openSosId: String?, navigate: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeTab(me, groupSos, openSosId, navigate)
                Tab.GROUPS -> GroupsTab(me, onOpen = { navigate("group/$it") })
                Tab.RIDES -> RidesTab(me, onOpen = { navigate("ride/$it") }, onCreate = { navigate("createRide") })
                Tab.PROFILE -> ProfileTab(me, onEdit = { navigate("editProfile") }, onSettings = { navigate("settings") })
            }
        }
    }
}

@Composable
private fun HomeTab(me: RiderProfile, groupSos: List<SosAlert>, openSosId: String?, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats by RideService.stats.collectAsState()
    val mySos by remember(me.uid) { SosRepository.observeMine(me.uid) }.collectAsState(emptyList())
    var confirmSos by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    fun sendSos() {
        sending = true
        scope.launch {
            val r = SosManager.trigger(context, me, "Rider pressed SOS")
            sending = false
            context.toast("SOS sent to ${r.groupsAlerted} group(s)" +
                if (!r.hasLocation) " – location unavailable" else "")
            SosManager.textContacts(context, me, r)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hi, ${me.fullName.substringBefore(' ')} 👋", style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, null, tint = SafeGreen, modifier = Modifier.size(16.dp))
                    Text(" Verified rider • ${me.bikeLabel}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Avatar(me.initials, 48.dp)
        }

        (mySos + groupSos.filter { it.uid != me.uid }).distinctBy { it.id }
            .sortedByDescending { it.id == openSosId }
            .forEach { SosCard(it, me) }

        RideCard(stats, onStart = {
            if (!context.hasLocationPermission()) {
                context.toast("Allow location access to start a ride"); return@RideCard
            }
            RideService.start(context)
        }, onStop = { RideService.stop(context) })

        SosButton(sending = sending, onTriggered = { confirmSos = true })

        Text("Quick help", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Default.Call, "Call 112", SosRed, Modifier.weight(1f)) { Intents.dial(context, "112") }
            QuickAction(Icons.Default.LocalHospital, "Hospital", Color(0xFFE53935), Modifier.weight(1f)) {
                Intents.searchNearby(context, "hospital near me")
            }
            QuickAction(Icons.Default.LocalPolice, "Police", Color(0xFF1E88E5), Modifier.weight(1f)) {
                Intents.searchNearby(context, "police station near me")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Default.LocalGasStation, "Fuel", BrandOrange, Modifier.weight(1f)) {
                Intents.searchNearby(context, "petrol pump near me")
            }
            QuickAction(Icons.Default.Build, "Mechanic", Color(0xFF8E24AA), Modifier.weight(1f)) {
                Intents.searchNearby(context, "bike mechanic near me")
            }
            QuickAction(Icons.Default.Share, "Share location", SafeGreen, Modifier.weight(1f)) {
                scope.launch {
                    val loc = LocationUtils.current(context)
                    if (loc == null) context.toast("Location unavailable")
                    else Intents.shareText(context, "My live location",
                        "${me.fullName} is here: ${LocationUtils.mapsLink(loc.latitude, loc.longitude)} (shared from BikersThrill)")
                }
            }
        }

        SectionCard(title = "Emergency contacts") {
            if (me.emergencyContacts.isEmpty()) Text("No emergency contacts added", color = MaterialTheme.colorScheme.onSurfaceVariant)
            me.emergencyContacts.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Avatar(c.name.take(1).uppercase(), 38.dp, SafeGreen)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold)
                        Text("${c.relation} • ${c.phone}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { Intents.dial(context, c.phone) }) { Icon(Icons.Default.Call, "Call ${c.name}", tint = SafeGreen) }
                }
            }
        }

        SectionCard(title = "Emergency card") {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Blood group", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(me.bloodGroup.ifBlank { "—" }, fontSize = 26.sp, fontWeight = FontWeight.Black, color = SosRed)
                }
                Column(Modifier.weight(1.4f)) {
                    Text("Bike", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(me.bikeRegNumber, fontWeight = FontWeight.Bold)
                    Text("Medical", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(me.medicalNotes.ifBlank { "None noted" }, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmSos) {
        AlertDialog(
            onDismissRequest = { confirmSos = false },
            title = { Text("Send SOS?") },
            text = {
                Text("Your location, blood group and bike number go to every group you're in and by SMS to " +
                    "${me.emergencyContacts.size} emergency contact(s).")
            },
            confirmButton = {
                TextButton(onClick = { confirmSos = false; sendSos() }) { Text("SEND SOS", color = SosRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmSos = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SosCard(alert: SosAlert, me: RiderProfile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mine = alert.uid == me.uid
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SosRed)) {
        Column(Modifier.padding(16.dp)) {
            Text(if (mine) "🚨 Your SOS is active" else "🚨 ${alert.name} needs help",
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("${alert.reason} • ${Formats.ago(alert.createdAt)}", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            if (!mine) {
                Text("Blood ${alert.bloodGroup.ifBlank { "—" }} • Bike ${alert.bikeRegNumber.ifBlank { "—" }}",
                    color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
            Row {
                if (mine) {
                    TextButton(onClick = { scope.launch { runCatching { SosRepository.resolve(alert, me) } } }) {
                        Text("I'M SAFE – CANCEL SOS", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { Intents.navigateTo(context, alert.lat, alert.lng) }) {
                        Icon(Icons.Default.Directions, null, tint = Color.White); Text(" Navigate", color = Color.White)
                    }
                    TextButton(onClick = { Intents.dial(context, alert.phone) }) {
                        Icon(Icons.Default.Call, null, tint = Color.White); Text(" Call", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun RideCard(stats: RideStats, onStart: () -> Unit, onStop: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    if (stats.active) listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)) else listOf(Color(0xFF2B2E36), Color(0xFF1C1E24)),
                ),
            ).padding(18.dp),
        ) {
            Column {
                Text(if (stats.active) "RIDE IN PROGRESS" else "READY TO RIDE", color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                if (stats.active) {
                    var now by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
                    val mins = (now - stats.startedAt) / 60000
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Stat("%.1f".format(stats.distanceKm), "km")
                        Stat("%.0f".format(stats.speedKmh), "km/h")
                        Stat("%.0f".format(stats.topSpeedKmh), "top km/h")
                        Stat("${mins / 60}:${"%02d".format(mins % 60)}", "time")
                    }
                } else {
                    Text("Start a ride to share live location with your groups and turn on crash detection.",
                        color = Color.White, fontSize = 14.sp)
                }
                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.Button(
                    onClick = if (stats.active) onStop else onStart,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (stats.active) Color.White else BrandOrange,
                        contentColor = if (stats.active) Color(0xFF1B5E20) else Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(if (stats.active) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Text(if (stats.active) "  End ride" else "  Start ride", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
    }
}

/** Press and hold for 2 seconds to avoid accidental SOS while riding. */
@Composable
private fun SosButton(sending: Boolean, onTriggered: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(170.dp).pointerInput(sending) {
                if (sending) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        progress.animateTo(1f, tween(2000, easing = LinearEasing))
                        onTriggered()
                        progress.snapTo(0f)
                    }
                    waitForUpOrCancellation()
                    if (progress.value < 1f) {
                        job.cancel(); scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(SosRed.copy(alpha = 0.18f))
                drawArc(
                    Color.White, -90f, 360f * progress.value, false,
                    topLeft = Offset(8f, 8f), size = Size(size.width - 16f, size.height - 16f),
                    style = Stroke(10f, cap = StrokeCap.Round),
                )
            }
            Box(
                Modifier.size(136.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFF5252), SosRed))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (sending) "…" else "SOS", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
                    Text("HOLD 2 SEC", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "Alerts your groups with your location, then lets you text your emergency contacts",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}
