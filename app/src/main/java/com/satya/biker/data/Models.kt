package com.satya.biker.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

/** Lifecycle of a rider account. Only [APPROVED] riders can use the app. */
enum class VerificationStatus { INCOMPLETE, PENDING, APPROVED, REJECTED, SUSPENDED }

object Roles {
    const val RIDER = "rider"
    const val ADMIN = "admin"

    /** Super admins are always admins and their admin access can never be removed. Keep in sync with firestore.rules. */
    val SUPER_ADMIN_EMAILS = setOf("deep.pkg@gmail.com")

    fun isSuperAdminEmail(email: String?) = email != null && email.trim().lowercase() in SUPER_ADMIN_EMAILS
}

data class EmergencyContact(
    var name: String = "",
    var relation: String = "",
    var phone: String = "",
)

/** Document stored at `users/{uid}`. */
data class RiderProfile(
    var uid: String = "",
    var email: String = "",
    var role: String = Roles.RIDER,
    var status: String = VerificationStatus.INCOMPLETE.name,
    var rejectionReason: String = "",

    // Basic details
    var fullName: String = "",
    var phone: String = "",
    var dateOfBirth: String = "",
    var gender: String = "",
    var bloodGroup: String = "",
    var address: String = "",
    var city: String = "",
    var state: String = "",
    var pincode: String = "",

    // Identity and licence
    var idType: String = "",
    var idNumber: String = "",
    var licenseNumber: String = "",
    var licenseExpiry: String = "",

    // Bike
    var bikeMake: String = "",
    var bikeModel: String = "",
    var bikeRegNumber: String = "",

    // Safety
    var emergencyContacts: List<EmergencyContact> = emptyList(),
    var medicalNotes: String = "",
    var ridingExperienceYears: String = "",

    var fcmToken: String = "",
    var createdAt: Timestamp? = null,
    var submittedAt: Timestamp? = null,
    var reviewedAt: Timestamp? = null,
    var reviewedBy: String = "",
) {
    @get:Exclude
    val verificationStatus: VerificationStatus
        get() = runCatching { VerificationStatus.valueOf(status) }.getOrDefault(VerificationStatus.INCOMPLETE)
    @get:Exclude
    val isSuperAdmin: Boolean get() = Roles.isSuperAdminEmail(email)
    @get:Exclude
    val isAdmin: Boolean get() = role == Roles.ADMIN || isSuperAdmin
    @get:Exclude
    val bikeLabel: String get() = listOf(bikeMake, bikeModel).filter { it.isNotBlank() }.joinToString(" ")
    @get:Exclude
    val initials: String
        get() = fullName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
            .ifBlank { email.take(1).uppercase() }
}

/** The KYC images kept at `kyc/{uid}`; each is a compressed JPEG. */
enum class KycDoc(val field: String, val label: String, val required: Boolean) {
    ID_FRONT("idFront", "ID card – front", true),
    ID_BACK("idBack", "ID card – back", false),
    LICENSE_FRONT("licenseFront", "Driving licence – front", true),
    LICENSE_BACK("licenseBack", "Driving licence – back", false),
    SELFIE("selfie", "Selfie (face clearly visible)", true),
}

object GroupRoles {
    const val CAPTAIN = "Captain"
    const val SWEEP = "Sweep"
    const val MEMBER = "Member"
}

data class GroupMember(
    var uid: String = "",
    var name: String = "",
    var phone: String = "",
    var bike: String = "",
    var bloodGroup: String = "",
    var role: String = GroupRoles.MEMBER,
)

/** Document stored at `groups/{groupId}`. */
data class RideGroup(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var code: String = "",
    var ownerId: String = "",
    var memberIds: List<String> = emptyList(),
    var members: Map<String, GroupMember> = emptyMap(),
    var createdAt: Timestamp? = null,
    var lastMessage: String = "",
    var lastMessageAt: Timestamp? = null,
)

object MessageTypes {
    const val TEXT = "TEXT"
    const val QUICK = "QUICK"
    const val SOS = "SOS"
    const val LOCATION = "LOCATION"
    const val SYSTEM = "SYSTEM"
}

/** Document stored at `groups/{groupId}/messages/{id}`. */
data class ChatMessage(
    var id: String = "",
    var senderId: String = "",
    var senderName: String = "",
    var text: String = "",
    var type: String = MessageTypes.TEXT,
    var lat: Double? = null,
    var lng: Double? = null,
    var createdAt: Timestamp? = null,
)

/** Document stored at `locations/{uid}` – the rider's last known position. */
data class LiveLocation(
    var uid: String = "",
    var name: String = "",
    var bike: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var speedKmh: Double = 0.0,
    var battery: Int = -1,
    var sharing: Boolean = false,
    var updatedAt: Timestamp? = null,
)

/** Document stored at `sos/{id}`. */
data class SosAlert(
    var id: String = "",
    var uid: String = "",
    var name: String = "",
    var phone: String = "",
    var bloodGroup: String = "",
    var bikeRegNumber: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var reason: String = "",
    var groupIds: List<String> = emptyList(),
    var active: Boolean = true,
    var createdAt: Timestamp? = null,
    var resolvedAt: Timestamp? = null,
)

object RideVisibility {
    const val PUBLIC = "PUBLIC"
    const val GROUP = "GROUP"
}

/** Document stored at `rides/{id}` – a planned ride riders can share and join. */
data class SharedRide(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var startPoint: String = "",
    var destination: String = "",
    var meetingPoint: String = "",
    var startTime: Timestamp? = null,
    var distanceKm: Double = 0.0,
    var difficulty: String = "Moderate",
    var maxRiders: Int = 0,
    var visibility: String = RideVisibility.PUBLIC,
    var groupId: String = "",
    var groupName: String = "",
    var createdBy: String = "",
    var creatorName: String = "",
    var participantIds: List<String> = emptyList(),
    var participantNames: Map<String, String> = emptyMap(),
    var createdAt: Timestamp? = null,
)

val QUICK_MESSAGES = listOf(
    "Fuel stop", "Puncture", "Regroup", "Police ahead",
    "Road hazard", "Slow down", "Break needed", "All good 👍",
)

val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
val ID_TYPES = listOf("Aadhaar", "PAN", "Passport", "Voter ID")
val GENDERS = listOf("Male", "Female", "Other")
