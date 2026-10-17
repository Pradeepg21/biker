package com.satya.biker.ui.rider

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satya.biker.data.ChatMessage
import com.satya.biker.data.GroupMember
import com.satya.biker.data.GroupRepository
import com.satya.biker.data.GroupRoles
import com.satya.biker.data.LiveLocation
import com.satya.biker.data.LocationRepository
import com.satya.biker.data.MessageTypes
import com.satya.biker.data.QUICK_MESSAGES
import com.satya.biker.data.RideGroup
import com.satya.biker.data.RiderProfile
import com.satya.biker.data.SosRepository
import com.satya.biker.service.SosManager
import com.satya.biker.ui.components.AppTextField
import com.satya.biker.ui.components.Avatar
import com.satya.biker.ui.components.EmptyState
import com.satya.biker.ui.components.LoadingScreen
import com.satya.biker.ui.components.Pill
import com.satya.biker.ui.components.PrimaryButton
import com.satya.biker.ui.theme.BrandOrange
import com.satya.biker.ui.theme.SafeGreen
import com.satya.biker.ui.theme.SosRed
import com.satya.biker.util.Formats
import com.satya.biker.util.Intents
import com.satya.biker.util.LocationUtils
import com.satya.biker.util.toast
import kotlinx.coroutines.launch

private val RIDER_COLORS = listOf(
    Color(0xFFFF6D00), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFF8E24AA), Color(0xFFE53935),
    Color(0xFF00ACC1), Color(0xFFFDD835), Color(0xFF6D4C41), Color(0xFFD81B60), Color(0xFF3949AB),
)

fun riderColor(uid: String) = RIDER_COLORS[(uid.hashCode() and 0x7fffffff) % RIDER_COLORS.size]

@Composable
fun GroupsTab(me: RiderProfile, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by remember(me.uid) { GroupRepository.observeMyGroups(me.uid) }.collectAsState(null)
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { dialog = "create" }, containerColor = BrandOrange, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("New group") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().fillMaxSize()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ride groups", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { dialog = "join" }) { Icon(Icons.Default.Login, null); Text(" Join") }
            }
            val list = groups
            when {
                list == null -> LoadingScreen()
                list.isEmpty() -> EmptyState(Icons.Default.Groups, "No groups yet",
                    "Create a group for your next ride or join one with a 6-digit code from your captain.")
                else -> LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { g -> GroupRow(g, me) { onOpen(g.id) } }
                }
            }
        }
    }

    when (dialog) {
        "create" -> {
            var name by remember { mutableStateOf("") }
            var desc by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Create ride group") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(name, { name = it }, "Group name", placeholder = "Sunday Nandi Hills run")
                        AppTextField(desc, { desc = it }, "Description (optional)", singleLine = false, minLines = 2)
                        Text("You'll be the Captain. Share the 6-digit code to invite riders.", fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch {
                            runCatching { GroupRepository.createGroup(name, desc, me) }
                                .onSuccess { dialog = null; onOpen(it) }
                                .onFailure { context.toast(it.localizedMessage ?: "Failed") }
                            busy = false
                        }
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        "join" -> {
            var code by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Join a group") },
                text = {
                    OutlinedTextField(
                        code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit group code") },
                        singleLine = true, textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    )
                },
                confirmButton = {
                    TextButton(enabled = code.length == 6 && !busy, onClick = {
                        busy = true
                        scope.launch {
                            runCatching { GroupRepository.joinByCode(code, me) }
                                .onSuccess { dialog = null; onOpen(it) }
                                .onFailure { context.toast(it.localizedMessage ?: "Failed") }
                            busy = false
                        }
                    }) { Text("Join") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun GroupRow(g: RideGroup, me: RiderProfile, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(g.name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }, 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    g.members[me.uid]?.role?.takeIf { it != GroupRoles.MEMBER }?.let { Spacer(Modifier.width(6.dp)); Pill(it, BrandOrange) }
                }
                Text(g.lastMessage, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${g.memberIds.size} riders • ${Formats.ago(g.lastMessageAt)}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private enum class GroupTab(val label: String) { CHAT("Chat"), MAP("Live map"), MEMBERS("Riders"), RIDES("Rides") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(groupId: String, me: RiderProfile, onBack: () -> Unit, onCreateRide: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val group by remember(groupId) { GroupRepository.observeGroup(groupId) }.collectAsState(null)
    var tab by rememberSaveable { mutableStateOf(GroupTab.CHAT) }
    var menu by remember { mutableStateOf(false) }
    var confirmSos by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    val g = group
    val isCaptain = g?.members?.get(me.uid)?.role == GroupRoles.CAPTAIN || g?.ownerId == me.uid

    fun shareInvite(g: RideGroup) = Intents.shareText(
        context, "Join ${g.name} on BikersThrill",
        "Join my ride group \"${g.name}\" on BikersThrill 🏍️\nGroup code: ${g.code}\nOpen the app → Groups → Join and enter the code.",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(g?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        if (g != null) Text("${g.memberIds.size} riders • code ${g.code}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { confirmSos = true }) { Icon(Icons.Default.Warning, "Group SOS", tint = SosRed) }
                    IconButton(onClick = { g?.let(::shareInvite) }) { Icon(Icons.Default.Share, "Invite") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Copy group code") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = { menu = false; g?.let { clipboard.setText(AnnotatedString(it.code)); context.toast("Code copied") } })
                        DropdownMenuItem(text = { Text("Plan a ride for this group") }, onClick = { menu = false; onCreateRide(groupId) })
                        DropdownMenuItem(
                            text = { Text(if (g?.ownerId == me.uid) "Delete group" else "Leave group", color = SosRed) },
                            onClick = { menu = false; confirmLeave = true },
                        )
                    }
                },
            )
        },
    ) { pad ->
        if (g == null) {
            LoadingScreen(); return@Scaffold
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                GroupTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) }) }
            }
            when (tab) {
                GroupTab.CHAT -> ChatPane(g, me)
                GroupTab.MAP -> MapPane(g, me)
                GroupTab.MEMBERS -> MembersPane(g, me, isCaptain)
                GroupTab.RIDES -> GroupRidesPane(g, me, onCreateRide)
            }
        }
    }

    if (confirmSos) {
        AlertDialog(
            onDismissRequest = { confirmSos = false },
            title = { Text("Send SOS?") },
            text = { Text("All riders in your groups get your location now, and your SMS app opens with a message for your emergency contacts.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSos = false
                    scope.launch {
                        val r = SosManager.trigger(context, me, "Rider pressed SOS")
                        context.toast("SOS sent to ${r.groupsAlerted} group(s)")
                        SosManager.textContacts(context, me, r)
                    }
                }) { Text("SEND SOS", color = SosRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmSos = false }) { Text("Cancel") } },
        )
    }
    if (confirmLeave && g != null) {
        val delete = g.ownerId == me.uid
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(if (delete) "Delete group?" else "Leave group?") },
            text = { Text(if (delete) "The group and its chat will be removed for all riders." else "You'll stop seeing this group's chat and map.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    scope.launch {
                        runCatching { if (delete) GroupRepository.deleteGroup(g.id) else GroupRepository.leaveGroup(g.id, me) }
                            .onSuccess { onBack() }.onFailure { context.toast(it.localizedMessage ?: "Failed") }
                    }
                }) { Text(if (delete) "Delete" else "Leave", color = SosRed) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatPane(g: RideGroup, me: RiderProfile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by remember(g.id) { GroupRepository.observeMessages(g.id) }.collectAsState(emptyList())
    var text by rememberSaveable { mutableStateOf("") }

    fun send(t: String, type: String = MessageTypes.TEXT, lat: Double? = null, lng: Double? = null) {
        scope.launch {
            runCatching { GroupRepository.sendMessage(g.id, me, t, type, lat, lng) }
                .onFailure { context.toast(it.localizedMessage ?: "Not sent") }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), reverseLayout = true,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { m -> MessageBubble(m, m.senderId == me.uid) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(QUICK_MESSAGES) { q ->
                AssistChip(onClick = { send(q, MessageTypes.QUICK) }, label = { Text(q) })
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                scope.launch {
                    val loc = LocationUtils.current(context)
                    if (loc == null) context.toast("Location unavailable")
                    else send("📍 Shared location", MessageTypes.LOCATION, loc.latitude, loc.longitude)
                }
            }) { Icon(Icons.Default.MyLocation, "Share location", tint = BrandOrange) }
            OutlinedTextField(
                text, { text = it }, placeholder = { Text("Message") }, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp), maxLines = 4,
            )
            IconButton(enabled = text.isNotBlank(), onClick = { send(text); text = "" }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = BrandOrange)
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessage, mine: Boolean) {
    val context = LocalContext.current
    if (m.type == MessageTypes.SYSTEM) {
        Text(m.text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        return
    }
    val bg = when {
        m.type == MessageTypes.SOS -> SosRed
        m.type == MessageTypes.QUICK -> Color(0xFF37474F)
        mine -> BrandOrange
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (mine || m.type == MessageTypes.SOS || m.type == MessageTypes.QUICK) Color.White else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 300.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!mine) Text(m.senderName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (bg == BrandOrange) fg else riderColor(m.senderId))
                Text(
                    if (m.type == MessageTypes.QUICK) "⚡ ${m.text}" else m.text, color = fg,
                    fontWeight = if (m.type == MessageTypes.TEXT) FontWeight.Normal else FontWeight.Bold,
                )
                if (m.lat != null && m.lng != null) {
                    TextButton(onClick = { Intents.navigateTo(context, m.lat!!, m.lng!!) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Open in maps →", color = fg, fontWeight = FontWeight.Bold)
                    }
                }
                Text(Formats.time(m.createdAt), fontSize = 10.sp, color = fg.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun MapPane(g: RideGroup, me: RiderProfile) {
    val context = LocalContext.current
    val locations by remember(g.memberIds) { LocationRepository.observe(g.memberIds) }.collectAsState(emptyList())
    val alerts by remember(g.id) { SosRepository.observeForGroups(listOf(g.id)) }.collectAsState(emptyList())
    val fresh = locations.filter { it.sharing && System.currentTimeMillis() - (it.updatedAt?.toDate()?.time ?: 0) < 6 * 3600_000 }
    val captainUid = g.members.values.firstOrNull { it.role == GroupRoles.CAPTAIN }?.uid ?: g.ownerId
    val captain = fresh.firstOrNull { it.uid == captainUid }

    val pins = fresh.map { l ->
        val sos = alerts.any { it.uid == l.uid }
        MapPin(
            l.uid, l.lat, l.lng, l.name + if (l.uid == me.uid) " (you)" else "",
            "${"%.0f".format(l.speedKmh)} km/h • battery ${if (l.battery >= 0) "${l.battery}%" else "—"} • ${Formats.ago(l.updatedAt)}",
            l.name.split(" ").take(2).joinToString("") { it.take(1).uppercase() },
            (if (sos) SosRed else riderColor(l.uid)).toArgb(),
        )
    } + alerts.filter { a -> fresh.none { it.uid == a.uid } && a.lat != 0.0 }.map {
        MapPin("sos-${it.id}", it.lat, it.lng, "SOS: ${it.name}", it.reason, "!", SosRed.toArgb())
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            OsmMap(pins, Modifier.fillMaxSize())
            if (fresh.isEmpty()) {
                Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp) {
                    Text("No one is sharing live location. Riders appear here when they tap Start ride.",
                        Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
        }
        LazyColumn(Modifier.height(220.dp), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fresh, key = { it.uid }) { l -> RiderStatusRow(l, captain, g.members[l.uid]?.role, alerts.any { it.uid == l.uid }) {
                Intents.navigateTo(context, l.lat, l.lng)
            } }
        }
    }
}

@Composable
private fun RiderStatusRow(l: LiveLocation, captain: LiveLocation?, role: String?, sos: Boolean, onNavigate: () -> Unit) {
    val gapKm = captain?.takeIf { it.uid != l.uid }?.let {
        val r = FloatArray(1); Location.distanceBetween(it.lat, it.lng, l.lat, l.lng, r); r[0] / 1000f
    }
    val stale = System.currentTimeMillis() - (l.updatedAt?.toDate()?.time ?: 0) > 5 * 60_000
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(if (sos) SosRed else riderColor(l.uid), RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(l.name + (role?.takeIf { it != GroupRoles.MEMBER }?.let { " • $it" } ?: ""), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                buildString {
                    append("${"%.0f".format(l.speedKmh)} km/h")
                    gapKm?.let { append(" • ${"%.1f".format(it)} km from captain") }
                    if (l.battery >= 0) append(" • 🔋${l.battery}%")
                    append(" • ${Formats.ago(l.updatedAt)}")
                },
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            sos -> Pill("SOS", SosRed)
            gapKm != null && gapKm > 3f -> Pill("Behind", Color(0xFFF9A825))
            stale -> Pill("Stopped?", Color.Gray)
        }
        IconButton(onClick = onNavigate) { Icon(Icons.Default.MyLocation, "Navigate") }
    }
}

@Composable
private fun MembersPane(g: RideGroup, me: RiderProfile, isCaptain: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val members = g.members.values.sortedBy { listOf(GroupRoles.CAPTAIN, GroupRoles.SWEEP, GroupRoles.MEMBER).indexOf(it.role) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BrandOrange.copy(alpha = 0.12f))) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Invite code", fontSize = 12.sp)
                    Text(g.code, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = BrandOrange)
                    if (g.description.isNotBlank()) Text(g.description, textAlign = TextAlign.Center, fontSize = 14.sp)
                }
            }
        }
        items(members, key = { it.uid }) { m ->
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }, 44.dp, riderColor(m.uid))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name + if (m.uid == me.uid) " (you)" else "", fontWeight = FontWeight.SemiBold)
                        if (m.role != GroupRoles.MEMBER) { Spacer(Modifier.width(6.dp)); Pill(m.role, BrandOrange) }
                    }
                    Text(listOf(m.bike, m.bloodGroup.takeIf { it.isNotBlank() }?.let { "Blood $it" }).filterNotNull()
                        .filter { it.isNotBlank() }.joinToString(" • "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.uid != me.uid) {
                    IconButton(onClick = { Intents.dial(context, m.phone) }) { Icon(Icons.Default.Call, "Call", tint = SafeGreen) }
                }
                if (isCaptain && m.uid != me.uid) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Manage") }
                        DropdownMenu(menu, { menu = false }) {
                            listOf(GroupRoles.CAPTAIN, GroupRoles.SWEEP, GroupRoles.MEMBER).filter { it != m.role }.forEach { role ->
                                DropdownMenuItem(text = { Text("Make $role") }, onClick = {
                                    menu = false; scope.launch { runCatching { GroupRepository.setRole(g.id, m.uid, role) } }
                                })
                            }
                            DropdownMenuItem(text = { Text("Remove from group", color = SosRed) }, onClick = {
                                menu = false; scope.launch { runCatching { GroupRepository.removeMember(g.id, m) } }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRidesPane(g: RideGroup, me: RiderProfile, onCreateRide: (String) -> Unit) {
    val rides by remember(g.id) { com.satya.biker.data.RideRepository.observeForGroups(listOf(g.id)) }.collectAsState(emptyList())
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Plan a ride for this group", { onCreateRide(g.id) }, icon = Icons.Default.Add)
        if (rides.isEmpty()) EmptyState(Icons.Default.Groups, "No rides planned", "Plan a ride and everyone in the group gets the details.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rides.sortedBy { it.startTime?.seconds ?: 0 }, key = { it.id }) { r -> RideRow(r, me, null) }
        }
    }
}
