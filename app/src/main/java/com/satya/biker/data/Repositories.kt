package com.satya.biker.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Timestamp
import com.google.firebase.auth.GoogleAuthProvider
import com.satya.biker.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

private fun DocumentSnapshot.toProfile(): RiderProfile? = toObject(RiderProfile::class.java)?.apply { uid = id }

/** Emits the result of [map] for every snapshot of [query]; errors close the flow quietly with an empty list. */
private fun <T> Query.observe(map: (com.google.firebase.firestore.QuerySnapshot) -> List<T>): Flow<List<T>> =
    callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList()); return@addSnapshotListener
            }
            if (snap != null) trySend(map(snap))
        }
        awaitClose { reg.remove() }
    }

object AuthRepository {
    val currentUid: String? get() = auth.currentUser?.uid
    val currentEmail: String? get() = auth.currentUser?.email

    fun authState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String, fullName: String, phone: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Registration failed")
        val profile = mapOf(
            "uid" to user.uid,
            "email" to user.email.orEmpty(),
            "fullName" to fullName.trim(),
            "phone" to phone.trim(),
            "role" to Roles.RIDER,
            "status" to VerificationStatus.INCOMPLETE.name,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        db.collection("users").document(user.uid).set(profile, SetOptions.merge()).await()
    }

    /**
     * Shows the Google account picker, signs in to Firebase with the chosen account and creates the
     * rider profile on first sign-in. [activityContext] must be an Activity. Returns false if the
     * user dismissed the picker.
     */
    suspend fun signInWithGoogle(activityContext: Context): Boolean {
        val option = GetSignInWithGoogleOption.Builder(activityContext.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (_: GetCredentialCancellationException) {
            return false
        } catch (_: NoCredentialException) {
            error("No Google account found on this phone. Add one in Settings → Accounts.")
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Unsupported sign-in response")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await().user
            ?: error("Google sign-in failed")

        val ref = db.collection("users").document(user.uid)
        if (!ref.get().await().exists()) {
            ref.set(
                mapOf(
                    "uid" to user.uid,
                    "email" to user.email.orEmpty(),
                    "fullName" to (google.displayName ?: user.displayName).orEmpty(),
                    "phone" to user.phoneNumber.orEmpty(),
                    "role" to Roles.RIDER,
                    "status" to VerificationStatus.INCOMPLETE.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
        }
        return true
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        currentUid?.let { LocationRepository.stopSharingAsync(it) }
        auth.signOut()
    }

    /** Firebase only deletes a sign-in that was used in the last few minutes. */
    fun signedInRecently(): Boolean {
        val last = auth.currentUser?.metadata?.lastSignInTimestamp ?: return false
        return System.currentTimeMillis() - last < 4 * 60_000
    }
}

object UserRepository {
    private val users get() = db.collection("users")
    private val kyc get() = db.collection("kyc")

    fun observeProfile(uid: String): Flow<RiderProfile?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(null); return@addSnapshotListener
            }
            trySend(snap?.takeIf { it.exists() }?.toProfile())
        }
        awaitClose { reg.remove() }
    }

    suspend fun getProfile(uid: String): RiderProfile? = users.document(uid).get().await().toProfile()

    /** Saves the rider-editable fields. Status and role are never touched here. */
    suspend fun saveDetails(p: RiderProfile) {
        val data = mapOf(
            "fullName" to p.fullName.trim(),
            "phone" to p.phone.trim(),
            "dateOfBirth" to p.dateOfBirth.trim(),
            "gender" to p.gender,
            "bloodGroup" to p.bloodGroup,
            "address" to p.address.trim(),
            "city" to p.city.trim(),
            "state" to p.state.trim(),
            "pincode" to p.pincode.trim(),
            "idType" to p.idType,
            "idNumber" to p.idNumber.trim(),
            "licenseNumber" to p.licenseNumber.trim().uppercase(),
            "licenseExpiry" to p.licenseExpiry.trim(),
            "bikeMake" to p.bikeMake.trim(),
            "bikeModel" to p.bikeModel.trim(),
            "bikeRegNumber" to p.bikeRegNumber.trim().uppercase(),
            "emergencyContacts" to p.emergencyContacts.map {
                mapOf("name" to it.name.trim(), "relation" to it.relation.trim(), "phone" to it.phone.trim())
            },
            "medicalNotes" to p.medicalNotes.trim(),
            "ridingExperienceYears" to p.ridingExperienceYears.trim(),
        )
        users.document(p.uid).set(data, SetOptions.merge()).await()
    }

    suspend fun uploadKycDoc(uid: String, doc: KycDoc, jpeg: ByteArray) {
        kyc.document(uid).set(
            mapOf(doc.field to Blob.fromBytes(jpeg), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    fun observeKyc(uid: String): Flow<Map<KycDoc, ByteArray>> = callbackFlow {
        val reg = kyc.document(uid).addSnapshotListener { snap, _ ->
            val result = KycDoc.entries.mapNotNull { d ->
                (snap?.get(d.field) as? Blob)?.let { d to it.toBytes() }
            }.toMap()
            trySend(result)
        }
        awaitClose { reg.remove() }
    }

    suspend fun submitForVerification(uid: String) {
        users.document(uid).update(
            mapOf(
                "status" to VerificationStatus.PENDING.name,
                "rejectionReason" to "",
                "submittedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** Gives the configured super admin account admin rights on sign-in (creates the profile if missing). */
    suspend fun ensureSuperAdmin(uid: String, email: String, existing: RiderProfile?) {
        if (!Roles.isSuperAdminEmail(email)) return
        if (existing != null && existing.role == Roles.ADMIN && existing.verificationStatus == VerificationStatus.APPROVED) return
        users.document(uid).set(
            buildMap {
                put("uid", uid)
                put("email", email)
                put("role", Roles.ADMIN)
                put("status", VerificationStatus.APPROVED.name)
                if (existing == null) {
                    put("fullName", "Super Admin")
                    put("createdAt", FieldValue.serverTimestamp())
                }
            },
            SetOptions.merge(),
        ).await()
    }

    /**
     * Deletes everything the rider owns (Play account-deletion requirement), then the sign-in itself.
     * Order matters: Firestore writes need the account, so the Auth user goes last.
     */
    suspend fun deleteAccount(me: RiderProfile) {
        val uid = me.uid
        require(!me.isSuperAdmin) { "The super admin account can't be deleted from the app." }
        // Groups, rides and SOS are only readable once approved, so only approved riders can have any.
        if (me.verificationStatus == VerificationStatus.APPROVED) {
            val groups = db.collection("groups").whereArrayContains("memberIds", uid).get().await()
            for (g in groups.documents) {
                val members = (g.get("memberIds") as? List<*>).orEmpty()
                if (g.getString("ownerId") == uid && members.size <= 1) g.reference.delete().await()
                else GroupRepository.leaveGroup(g.id, me)
            }
            val rides = db.collection("rides")
            rides.whereEqualTo("createdBy", uid).get().await().documents.forEach { it.reference.delete().await() }
            rides.whereArrayContains("participantIds", uid).get().await().documents.forEach {
                it.reference.update(mapOf("participantIds" to FieldValue.arrayRemove(uid), "participantNames.$uid" to FieldValue.delete())).await()
            }
            db.collection("sos").whereEqualTo("uid", uid).get().await().documents.forEach { it.reference.delete().await() }
        }
        db.collection("locations").document(uid).delete().await()
        kyc.document(uid).delete().await()
        users.document(uid).delete().await()
        auth.currentUser?.delete()?.await()
    }

    fun updateFcmToken(uid: String, token: String) {
        users.document(uid).update("fcmToken", token)
    }
}

object AdminRepository {
    private val users get() = db.collection("users")

    /** Every account, admins included, newest first. */
    fun observeAllUsers(): Flow<List<RiderProfile>> = users.observe { snap ->
        snap.documents.mapNotNull { it.toProfile() }
            .sortedByDescending { (it.createdAt ?: it.submittedAt)?.seconds ?: 0 }
    }

    /** Grants or removes admin access. Any admin may do this, except on the super admin (enforced by rules). */
    suspend fun setAdmin(uid: String, makeAdmin: Boolean) {
        val data = if (makeAdmin) {
            mapOf("role" to Roles.ADMIN, "status" to VerificationStatus.APPROVED.name)
        } else {
            mapOf("role" to Roles.RIDER)
        }
        users.document(uid).update(
            data + mapOf(
                "reviewedAt" to FieldValue.serverTimestamp(),
                "reviewedBy" to (AuthRepository.currentEmail ?: AuthRepository.currentUid.orEmpty()),
            ),
        ).await()
    }

    fun observeUserGroups(uid: String): Flow<List<RideGroup>> =
        db.collection("groups").whereArrayContains("memberIds", uid).observe { snap ->
            snap.documents.mapNotNull { d -> d.toObject(RideGroup::class.java)?.apply { id = d.id } }
        }

    fun observeUserSos(uid: String): Flow<List<SosAlert>> =
        db.collection("sos").whereEqualTo("uid", uid).observe { snap ->
            snap.documents.mapNotNull { d -> d.toObject(SosAlert::class.java)?.apply { id = d.id } }
                .sortedByDescending { it.createdAt?.seconds ?: 0 }
        }

    fun observeByStatus(status: VerificationStatus?): Flow<List<RiderProfile>> {
        val q: Query = if (status == null) users else users.whereEqualTo("status", status.name)
        return q.observe { snap ->
            snap.documents.mapNotNull { it.toProfile() }
                .filter { !it.isAdmin }
                .sortedByDescending { (it.submittedAt ?: it.createdAt)?.seconds ?: 0 }
        }
    }

    private suspend fun review(uid: String, status: VerificationStatus, reason: String = "") {
        users.document(uid).update(
            mapOf(
                "status" to status.name,
                "rejectionReason" to reason,
                "reviewedAt" to FieldValue.serverTimestamp(),
                "reviewedBy" to (AuthRepository.currentEmail ?: AuthRepository.currentUid.orEmpty()),
            ),
        ).await()
    }

    suspend fun approve(uid: String) = review(uid, VerificationStatus.APPROVED)
    suspend fun reject(uid: String, reason: String) = review(uid, VerificationStatus.REJECTED, reason)
    suspend fun suspendRider(uid: String, reason: String) = review(uid, VerificationStatus.SUSPENDED, reason)

    fun observeActiveSos(): Flow<List<SosAlert>> =
        db.collection("sos").whereEqualTo("active", true).observe { snap ->
            snap.documents.mapNotNull { d -> d.toObject(SosAlert::class.java)?.apply { id = d.id } }
                .sortedByDescending { it.createdAt?.seconds ?: 0 }
        }
}

object GroupRepository {
    private val groups get() = db.collection("groups")

    private fun DocumentSnapshot.toGroup() = toObject(RideGroup::class.java)?.apply { id = this@toGroup.id }

    fun observeMyGroups(uid: String): Flow<List<RideGroup>> =
        groups.whereArrayContains("memberIds", uid).observe { snap ->
            snap.documents.mapNotNull { it.toGroup() }
                .sortedByDescending { (it.lastMessageAt ?: it.createdAt)?.seconds ?: 0 }
        }

    fun observeGroup(groupId: String): Flow<RideGroup?> = callbackFlow {
        val reg = groups.document(groupId).addSnapshotListener { snap, _ ->
            trySend(snap?.takeIf { it.exists() }?.toGroup())
        }
        awaitClose { reg.remove() }
    }

    private fun memberOf(p: RiderProfile, role: String) = mapOf(
        "uid" to p.uid,
        "name" to p.fullName,
        "phone" to p.phone,
        "bike" to p.bikeLabel,
        "bloodGroup" to p.bloodGroup,
        "role" to role,
    )

    private suspend fun uniqueCode(): String {
        repeat(10) {
            val code = Random.nextInt(100000, 999999).toString()
            if (groups.whereEqualTo("code", code).limit(1).get().await().isEmpty) return code
        }
        error("Could not generate a group code, try again")
    }

    suspend fun createGroup(name: String, description: String, me: RiderProfile): String {
        val ref = groups.document()
        val data = mapOf(
            "name" to name.trim(),
            "description" to description.trim(),
            "code" to uniqueCode(),
            "ownerId" to me.uid,
            "memberIds" to listOf(me.uid),
            "members" to mapOf(me.uid to memberOf(me, GroupRoles.CAPTAIN)),
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessage" to "Group created",
            "lastMessageAt" to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
        postSystem(ref.id, "${me.fullName} created the group")
        return ref.id
    }

    suspend fun joinByCode(code: String, me: RiderProfile): String {
        val doc = groups.whereEqualTo("code", code.trim()).limit(1).get().await().documents.firstOrNull()
            ?: error("No group found with code $code")
        val group = doc.toGroup() ?: error("Invalid group")
        if (me.uid in group.memberIds) return doc.id
        if (group.memberIds.size >= 50) error("This group is full (50 riders)")
        doc.reference.update(
            mapOf(
                "memberIds" to FieldValue.arrayUnion(me.uid),
                "members.${me.uid}" to memberOf(me, GroupRoles.MEMBER),
            ),
        ).await()
        postSystem(doc.id, "${me.fullName} joined the group")
        return doc.id
    }

    suspend fun leaveGroup(groupId: String, me: RiderProfile) {
        // Post first: once removed, the rider may no longer write to the group's chat.
        postSystem(groupId, "${me.fullName} left the group")
        groups.document(groupId).update(
            mapOf(
                "memberIds" to FieldValue.arrayRemove(me.uid),
                "members.${me.uid}" to FieldValue.delete(),
            ),
        ).await()
    }

    suspend fun removeMember(groupId: String, member: GroupMember) {
        groups.document(groupId).update(
            mapOf(
                "memberIds" to FieldValue.arrayRemove(member.uid),
                "members.${member.uid}" to FieldValue.delete(),
            ),
        ).await()
        postSystem(groupId, "${member.name} was removed by the captain")
    }

    suspend fun setRole(groupId: String, memberUid: String, role: String) {
        groups.document(groupId).update("members.$memberUid.role", role).await()
    }

    suspend fun deleteGroup(groupId: String) {
        groups.document(groupId).delete().await()
    }

    fun observeMessages(groupId: String): Flow<List<ChatMessage>> =
        groups.document(groupId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(300)
            .observe { snap ->
                snap.documents.mapNotNull { d -> d.toObject(ChatMessage::class.java)?.apply { id = d.id } }
            }

    suspend fun sendMessage(
        groupId: String,
        me: RiderProfile,
        text: String,
        type: String = MessageTypes.TEXT,
        lat: Double? = null,
        lng: Double? = null,
    ) {
        val msg = buildMap {
            put("senderId", me.uid)
            put("senderName", me.fullName)
            put("text", text.trim())
            put("type", type)
            put("createdAt", FieldValue.serverTimestamp())
            if (lat != null && lng != null) {
                put("lat", lat); put("lng", lng)
            }
        }
        groups.document(groupId).collection("messages").add(msg).await()
        val preview = if (type == MessageTypes.TEXT) "${me.fullName.substringBefore(' ')}: $text" else text
        groups.document(groupId).update(
            mapOf("lastMessage" to preview.take(120), "lastMessageAt" to FieldValue.serverTimestamp()),
        )
    }

    private suspend fun postSystem(groupId: String, text: String) {
        val uid = AuthRepository.currentUid ?: return
        groups.document(groupId).collection("messages").add(
            mapOf(
                "senderId" to uid,
                "senderName" to "",
                "text" to text,
                "type" to MessageTypes.SYSTEM,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }
}

object LocationRepository {
    private val locations get() = db.collection("locations")

    fun publish(loc: LiveLocation) {
        locations.document(loc.uid).set(
            mapOf(
                "uid" to loc.uid,
                "name" to loc.name,
                "bike" to loc.bike,
                "lat" to loc.lat,
                "lng" to loc.lng,
                "speedKmh" to loc.speedKmh,
                "battery" to loc.battery,
                "sharing" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    fun stopSharingAsync(uid: String) {
        locations.document(uid).set(mapOf("sharing" to false), SetOptions.merge())
    }

    /** Live positions of up to 30 riders (Firestore `in` query limit). */
    fun observe(uids: List<String>): Flow<List<LiveLocation>> {
        if (uids.isEmpty()) return flowOf(emptyList())
        return locations.whereIn("uid", uids.take(30)).observe { snap ->
            snap.documents.mapNotNull { it.toObject(LiveLocation::class.java) }
        }
    }
}

object SosRepository {
    private val sos get() = db.collection("sos")

    suspend fun raise(me: RiderProfile, lat: Double, lng: Double, reason: String, groupIds: List<String>): String {
        val ref = sos.document()
        ref.set(
            mapOf(
                "uid" to me.uid,
                "name" to me.fullName,
                "phone" to me.phone,
                "bloodGroup" to me.bloodGroup,
                "bikeRegNumber" to me.bikeRegNumber,
                "lat" to lat,
                "lng" to lng,
                "reason" to reason,
                "groupIds" to groupIds,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        groupIds.forEach { gid ->
            runCatching {
                GroupRepository.sendMessage(
                    gid, me, "🚨 SOS from ${me.fullName}: $reason", MessageTypes.SOS, lat, lng,
                )
            }
        }
        return ref.id
    }

    suspend fun resolve(alert: SosAlert, me: RiderProfile) {
        sos.document(alert.id).update(
            mapOf("active" to false, "resolvedAt" to FieldValue.serverTimestamp()),
        ).await()
        alert.groupIds.forEach { gid ->
            runCatching {
                GroupRepository.sendMessage(gid, me, "✅ ${alert.name} is safe – SOS cancelled", MessageTypes.SYSTEM)
            }
        }
    }

    /** Active SOS alerts in any of the given groups (up to 30 groups). */
    fun observeForGroups(groupIds: List<String>): Flow<List<SosAlert>> {
        if (groupIds.isEmpty()) return flowOf(emptyList())
        return sos.whereArrayContainsAny("groupIds", groupIds.take(30)).observe { snap ->
            snap.documents.mapNotNull { d -> d.toObject(SosAlert::class.java)?.apply { id = d.id } }
                .filter { it.active }
                .sortedByDescending { it.createdAt?.seconds ?: 0 }
        }
    }

    fun observeMine(uid: String): Flow<List<SosAlert>> =
        sos.whereEqualTo("uid", uid).observe { snap ->
            snap.documents.mapNotNull { d -> d.toObject(SosAlert::class.java)?.apply { id = d.id } }
                .filter { it.active }
        }
}

object RideRepository {
    private val rides get() = db.collection("rides")

    private fun DocumentSnapshot.toRide() = toObject(SharedRide::class.java)?.apply { id = this@toRide.id }

    fun observePublic(): Flow<List<SharedRide>> =
        rides.whereEqualTo("visibility", RideVisibility.PUBLIC).observe { snap ->
            snap.documents.mapNotNull { it.toRide() }
        }

    fun observeForGroups(groupIds: List<String>): Flow<List<SharedRide>> {
        if (groupIds.isEmpty()) return flowOf(emptyList())
        return rides.whereIn("groupId", groupIds.take(30)).observe { snap ->
            snap.documents.mapNotNull { it.toRide() }
        }
    }

    fun observeRide(id: String): Flow<SharedRide?> = callbackFlow {
        val reg = rides.document(id).addSnapshotListener { snap, _ ->
            trySend(snap?.takeIf { it.exists() }?.toRide())
        }
        awaitClose { reg.remove() }
    }

    suspend fun create(ride: SharedRide, me: RiderProfile): String {
        val ref = rides.document()
        ref.set(
            mapOf(
                "title" to ride.title.trim(),
                "description" to ride.description.trim(),
                "startPoint" to ride.startPoint.trim(),
                "destination" to ride.destination.trim(),
                "meetingPoint" to ride.meetingPoint.trim(),
                "startTime" to ride.startTime,
                "distanceKm" to ride.distanceKm,
                "difficulty" to ride.difficulty,
                "maxRiders" to ride.maxRiders,
                "visibility" to ride.visibility,
                "groupId" to ride.groupId,
                "groupName" to ride.groupName,
                "createdBy" to me.uid,
                "creatorName" to me.fullName,
                "participantIds" to listOf(me.uid),
                "participantNames" to mapOf(me.uid to me.fullName),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        if (ride.groupId.isNotBlank()) {
            runCatching {
                GroupRepository.sendMessage(
                    ride.groupId, me, "🏍️ New ride planned: ${ride.title} (${ride.startPoint} → ${ride.destination})",
                    MessageTypes.SYSTEM,
                )
            }
        }
        return ref.id
    }

    suspend fun join(rideId: String, me: RiderProfile) {
        rides.document(rideId).update(
            mapOf(
                "participantIds" to FieldValue.arrayUnion(me.uid),
                "participantNames.${me.uid}" to me.fullName,
            ),
        ).await()
    }

    suspend fun leave(rideId: String, me: RiderProfile) {
        rides.document(rideId).update(
            mapOf(
                "participantIds" to FieldValue.arrayRemove(me.uid),
                "participantNames.${me.uid}" to FieldValue.delete(),
            ),
        ).await()
    }

    suspend fun delete(rideId: String) {
        rides.document(rideId).delete().await()
    }
}

fun Timestamp?.millis(): Long = this?.toDate()?.time ?: 0L
