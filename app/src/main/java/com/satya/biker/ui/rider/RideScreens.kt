package com.satya.biker.ui.rider

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.satya.biker.data.GroupRepository
import com.satya.biker.data.RideRepository
import com.satya.biker.data.RideVisibility
import com.satya.biker.data.RiderProfile
import com.satya.biker.data.SharedRide
import com.satya.biker.ui.components.AppTextField
import com.satya.biker.ui.components.Avatar
import com.satya.biker.ui.components.DropdownField
import com.satya.biker.ui.components.EmptyState
import com.satya.biker.ui.components.InfoRow
import com.satya.biker.ui.components.LoadingScreen
import com.satya.biker.ui.components.Pill
import com.satya.biker.ui.components.PrimaryButton
import com.satya.biker.ui.components.SectionCard
import com.satya.biker.ui.theme.BrandOrange
import com.satya.biker.ui.theme.SafeGreen
import com.satya.biker.ui.theme.SosRed
import com.satya.biker.util.Formats
import com.satya.biker.util.Intents
import com.satya.biker.util.toast
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

private val DIFFICULTIES = listOf("Easy", "Moderate", "Challenging", "Expert")

private enum class RidesFilter(val label: String) { UPCOMING("Upcoming"), MINE("My rides"), PAST("Past") }

fun rideShareText(r: SharedRide) = buildString {
    appendLine("🏍️ ${r.title}")
    appendLine("📅 ${Formats.dateTime(r.startTime)}")
    appendLine("🟢 From: ${r.startPoint}")
    appendLine("🏁 To: ${r.destination}")
    if (r.meetingPoint.isNotBlank()) appendLine("📍 Meet at: ${r.meetingPoint}")
    if (r.distanceKm > 0) appendLine("🛣️ ${"%.0f".format(r.distanceKm)} km • ${r.difficulty}")
    if (r.description.isNotBlank()) appendLine("\n${r.description}")
    appendLine("\nRoute: https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(r.startPoint)}&destination=${Uri.encode(r.destination)}&travelmode=driving")
    append("Ride with me on BikersThrill – organised by ${r.creatorName}")
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun RidesTab(me: RiderProfile, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(RidesFilter.UPCOMING) }
    val rides by remember(me.uid) {
        val groupRides = GroupRepository.observeMyGroups(me.uid).map { g -> g.map { it.id } }
            .flatMapLatest { RideRepository.observeForGroups(it) }
        RideRepository.observePublic().combine(groupRides) { a, b -> (a + b).distinctBy { it.id } }
    }.collectAsState(null)

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, containerColor = BrandOrange, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Plan ride") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().fillMaxSize()) {
            Text("Rides", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
            PrimaryTabRow(selectedTabIndex = filter.ordinal) {
                RidesFilter.entries.forEach { f -> Tab(selected = filter == f, onClick = { filter = f }, text = { Text(f.label) }) }
            }
            val all = rides
            if (all == null) {
                LoadingScreen(); return@Column
            }
            val now = System.currentTimeMillis() - 6 * 3600_000L
            val list = when (filter) {
                RidesFilter.UPCOMING -> all.filter { (it.startTime?.toDate()?.time ?: 0) >= now }.sortedBy { it.startTime?.seconds }
                RidesFilter.MINE -> all.filter { me.uid in it.participantIds }.sortedByDescending { it.startTime?.seconds }
                RidesFilter.PAST -> all.filter { (it.startTime?.toDate()?.time ?: 0) < now }.sortedByDescending { it.startTime?.seconds }
            }
            if (list.isEmpty()) {
                EmptyState(Icons.Default.Route, "No rides here", "Plan a ride and share it with your group or the whole community.")
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { r -> RideRow(r, me) { onOpen(r.id) } }
            }
        }
    }
}

@Composable
fun RideRow(r: SharedRide, me: RiderProfile, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val joined = me.uid in r.participantIds
    Card(
        onClick = { onClick?.invoke() }, enabled = onClick != null, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (joined) Pill("Joined", SafeGreen)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, Modifier.size(15.dp), tint = BrandOrange)
                Text(" ${Formats.dateTime(r.startTime)}", fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, null, Modifier.size(15.dp), tint = BrandOrange)
                Text(" ${r.startPoint} → ${r.destination}", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(r.difficulty, BrandOrange)
                Spacer(Modifier.width(6.dp))
                if (r.groupName.isNotBlank()) Pill(r.groupName, Color(0xFF1E88E5)) else Pill("Public", Color.Gray)
                Spacer(Modifier.weight(1f))
                Text("${r.participantIds.size}${if (r.maxRiders > 0) "/${r.maxRiders}" else ""} riders", fontSize = 12.sp)
                IconButton(onClick = { Intents.shareText(context, r.title, rideShareText(r)) }) { Icon(Icons.Default.Share, "Share ride") }
            }
        }
    }
}

private fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            cal.set(y, m, d, h, min, 0); onPicked(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
        datePicker.minDate = System.currentTimeMillis() - 1000
    }.show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRideScreen(me: RiderProfile, presetGroupId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by remember(me.uid) { GroupRepository.observeMyGroups(me.uid) }.collectAsState(emptyList())
    var title by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var dest by rememberSaveable { mutableStateOf("") }
    var meet by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    var maxRiders by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf("Moderate") }
    var groupId by rememberSaveable { mutableStateOf(presetGroupId) }
    var startAt by rememberSaveable {
        mutableLongStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0) }.timeInMillis)
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Plan a ride") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                error?.let { Text(it, color = SosRed, modifier = Modifier.padding(bottom = 8.dp)) }
                PrimaryButton("Publish ride", loading = busy, onClick = {
                    error = when {
                        title.isBlank() -> "Give your ride a name"
                        start.isBlank() || dest.isBlank() -> "Enter start and destination"
                        startAt < System.currentTimeMillis() -> "Start time must be in the future"
                        else -> null
                    }
                    if (error != null) return@PrimaryButton
                    busy = true
                    val group = groups.firstOrNull { it.id == groupId }
                    scope.launch {
                        runCatching {
                            RideRepository.create(
                                SharedRide(
                                    title = title, description = desc, startPoint = start, destination = dest, meetingPoint = meet,
                                    startTime = Timestamp(Date(startAt)), distanceKm = distance.toDoubleOrNull() ?: 0.0,
                                    difficulty = difficulty, maxRiders = maxRiders.toIntOrNull() ?: 0,
                                    visibility = if (group != null) RideVisibility.GROUP else RideVisibility.PUBLIC,
                                    groupId = group?.id.orEmpty(), groupName = group?.name.orEmpty(),
                                ),
                                me,
                            )
                        }.onSuccess { context.toast("Ride published"); onDone() }
                            .onFailure { error = it.localizedMessage }
                        busy = false
                    }
                })
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(title, { title = it }, "Ride name", placeholder = "Bangalore → Coorg weekend")
            AppTextField(start, { start = it }, "Start point", leading = Icons.Default.TripOrigin)
            AppTextField(dest, { dest = it }, "Destination", leading = Icons.Default.Flag)
            AppTextField(meet, { meet = it }, "Meeting point (optional)", leading = Icons.Default.Place, placeholder = "Shell pump, NICE road toll")
            OutlinedButton(onClick = { pickDateTime(context, startAt) { startAt = it } }, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.CalendarMonth, null); Text("  Starts ${Formats.dateTime(startAt)}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(distance, { distance = it.filter { c -> c.isDigit() || c == '.' } }, "Distance (km)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                AppTextField(maxRiders, { maxRiders = it.filter(Char::isDigit).take(3) }, "Max riders", Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            Text("Difficulty", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DIFFICULTIES.forEach { d -> FilterChip(difficulty == d, { difficulty = d }, label = { Text(d) }) }
            }
            DropdownField(
                "Who can see this ride", groups.firstOrNull { it.id == groupId }?.name ?: "Everyone (public)",
                listOf("Everyone (public)") + groups.map { it.name },
                { sel -> groupId = groups.firstOrNull { it.name == sel }?.id.orEmpty() },
            )
            AppTextField(desc, { desc = it }, "Plan, stops & notes", singleLine = false, minLines = 4,
                placeholder = "Breakfast at Kamat, fuel at Hunsur, riding gear mandatory…")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideDetailScreen(rideId: String, me: RiderProfile, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ride by remember(rideId) { RideRepository.observeRide(rideId) }.collectAsState(null)
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ride?.title ?: "Ride", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    ride?.let { r ->
                        IconButton(onClick = { Intents.shareText(context, r.title, rideShareText(r)) }) { Icon(Icons.Default.Share, "Share") }
                        if (r.createdBy == me.uid) {
                            IconButton(onClick = {
                                scope.launch { runCatching { RideRepository.delete(r.id) }.onSuccess { onBack() } }
                            }) { Icon(Icons.Default.Delete, "Delete ride", tint = SosRed) }
                        }
                    }
                },
            )
        },
        bottomBar = {
            val r = ride ?: return@Scaffold
            val joined = me.uid in r.participantIds
            val full = r.maxRiders > 0 && r.participantIds.size >= r.maxRiders
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                if (r.createdBy != me.uid) {
                    PrimaryButton(
                        text = if (joined) "Leave ride" else if (full) "Ride is full" else "Join this ride",
                        enabled = joined || !full, loading = busy,
                        color = if (joined) SosRed else MaterialTheme.colorScheme.primary,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching { if (joined) RideRepository.leave(r.id, me) else RideRepository.join(r.id, me) }
                                    .onFailure { context.toast(it.localizedMessage ?: "Failed") }
                                busy = false
                            }
                        },
                    )
                }
            }
        },
    ) { pad ->
        val r = ride
        if (r == null) {
            LoadingScreen(); return@Scaffold
        }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard(title = "Ride plan") {
                InfoRow("Starts", Formats.dateTime(r.startTime))
                InfoRow("From", r.startPoint)
                InfoRow("To", r.destination)
                InfoRow("Meeting point", r.meetingPoint)
                InfoRow("Distance", if (r.distanceKm > 0) "${"%.0f".format(r.distanceKm)} km" else "")
                InfoRow("Difficulty", r.difficulty)
                InfoRow("Group", r.groupName.ifBlank { "Public ride" })
                InfoRow("Organiser", r.creatorName)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    Intents.searchNearby(context, r.meetingPoint.ifBlank { r.startPoint })
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Map, null); Text("  Open meeting point in maps") }
            }
            if (r.description.isNotBlank()) {
                SectionCard(title = "Notes") { Text(r.description) }
            }
            SectionCard(title = "Riders (${r.participantIds.size}${if (r.maxRiders > 0) "/${r.maxRiders}" else ""})") {
                r.participantIds.forEach { uid ->
                    val name = r.participantNames[uid].orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Avatar(name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }, 36.dp, riderColor(uid))
                        Spacer(Modifier.width(10.dp))
                        Text(name + if (uid == r.createdBy) "  • organiser" else "", modifier = Modifier.weight(1f))
                    }
                }
            }
            TextButton(onClick = { Intents.shareText(context, r.title, rideShareText(r)) }) {
                Icon(Icons.Default.Share, null); Text("  Share ride on WhatsApp / social")
            }
        }
    }
}
