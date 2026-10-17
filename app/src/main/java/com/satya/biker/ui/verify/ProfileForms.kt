package com.satya.biker.ui.verify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.satya.biker.data.BLOOD_GROUPS
import com.satya.biker.data.EmergencyContact
import com.satya.biker.data.GENDERS
import com.satya.biker.data.ID_TYPES
import com.satya.biker.data.RiderProfile
import com.satya.biker.ui.components.AppTextField
import com.satya.biker.ui.components.DropdownField
import com.satya.biker.ui.components.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val DMY = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, placeholder = { Text("dd/mm/yyyy") },
            trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.CalendarMonth, "Pick date") } },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        )
    }
    if (open) {
        val initial = runCatching { DMY.parse(value)?.time }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(DMY.format(Date(it))) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** Validation for each form section; returns an error message or null. */
object ProfileValidation {
    fun personal(p: RiderProfile): String? = when {
        p.fullName.trim().length < 3 -> "Enter your full name"
        p.phone.filter { it.isDigit() }.length < 10 -> "Enter a valid mobile number"
        p.dateOfBirth.isBlank() -> "Select your date of birth"
        p.gender.isBlank() -> "Select gender"
        p.bloodGroup.isBlank() -> "Select your blood group – it's shared in an SOS"
        p.address.isBlank() || p.city.isBlank() || p.state.isBlank() -> "Enter your full address"
        p.pincode.length != 6 -> "Enter a valid 6-digit PIN code"
        else -> null
    }

    fun identity(p: RiderProfile): String? = when {
        p.idType.isBlank() -> "Select an ID type"
        p.idNumber.trim().length < 4 -> "Enter your ID number"
        p.licenseNumber.trim().length < 8 -> "Enter a valid driving licence number"
        p.licenseExpiry.isBlank() -> "Select licence expiry date"
        else -> null
    }

    fun bikeAndSafety(p: RiderProfile): String? = when {
        p.bikeMake.isBlank() || p.bikeModel.isBlank() -> "Enter your bike make and model"
        p.bikeRegNumber.trim().length < 6 -> "Enter the bike registration number"
        p.emergencyContacts.none { it.name.isNotBlank() && it.phone.filter(Char::isDigit).length >= 10 } ->
            "Add at least one emergency contact with a valid phone number"
        else -> null
    }
}

@Composable
fun PersonalSection(p: RiderProfile, onChange: (RiderProfile) -> Unit) {
    SectionCard(title = "Basic details") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(p.fullName, { onChange(p.copy(fullName = it)) }, "Full name (as on licence)", leading = Icons.Default.Person)
            AppTextField(p.phone, { onChange(p.copy(phone = it.filter { c -> c.isDigit() || c == '+' })) }, "Mobile number",
                keyboardType = KeyboardType.Phone, leading = Icons.Default.Phone)
            DateField("Date of birth", p.dateOfBirth, { onChange(p.copy(dateOfBirth = it)) })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownField("Gender", p.gender, GENDERS, { onChange(p.copy(gender = it)) }, Modifier.weight(1f))
                DropdownField("Blood group", p.bloodGroup, BLOOD_GROUPS, { onChange(p.copy(bloodGroup = it)) }, Modifier.weight(1f))
            }
            AppTextField(p.address, { onChange(p.copy(address = it)) }, "Address", leading = Icons.Default.Home, singleLine = false, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(p.city, { onChange(p.copy(city = it)) }, "City", Modifier.weight(1f))
                AppTextField(p.state, { onChange(p.copy(state = it)) }, "State", Modifier.weight(1f))
            }
            AppTextField(p.pincode, { onChange(p.copy(pincode = it.filter(Char::isDigit).take(6))) }, "PIN code", keyboardType = KeyboardType.Number)
        }
    }
}

@Composable
fun IdentitySection(p: RiderProfile, onChange: (RiderProfile) -> Unit) {
    SectionCard(title = "ID & driving licence") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField("Government ID type", p.idType, ID_TYPES, { onChange(p.copy(idType = it)) })
            AppTextField(p.idNumber, { onChange(p.copy(idNumber = it)) }, "ID number", leading = Icons.Default.Badge)
            AppTextField(p.licenseNumber, { onChange(p.copy(licenseNumber = it.uppercase())) }, "Driving licence number",
                placeholder = "e.g. MH12 20190012345")
            DateField("Licence valid till", p.licenseExpiry, { onChange(p.copy(licenseExpiry = it)) })
            AppTextField(p.ridingExperienceYears, { onChange(p.copy(ridingExperienceYears = it.filter(Char::isDigit).take(2))) },
                "Riding experience (years)", keyboardType = KeyboardType.Number)
        }
    }
}

@Composable
fun BikeSection(p: RiderProfile, onChange: (RiderProfile) -> Unit) {
    SectionCard(title = "Your bike") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(p.bikeMake, { onChange(p.copy(bikeMake = it)) }, "Make", Modifier.weight(1f), placeholder = "Royal Enfield")
                AppTextField(p.bikeModel, { onChange(p.copy(bikeModel = it)) }, "Model", Modifier.weight(1f), placeholder = "Himalayan 450")
            }
            AppTextField(p.bikeRegNumber, { onChange(p.copy(bikeRegNumber = it.uppercase())) }, "Registration number",
                leading = Icons.Default.TwoWheeler, placeholder = "KA01AB1234")
        }
    }
}

@Composable
fun EmergencySection(p: RiderProfile, onChange: (RiderProfile) -> Unit) {
    val contacts = p.emergencyContacts.ifEmpty { listOf(EmergencyContact()) }
    SectionCard(title = "Emergency contacts") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "After an SOS, your SMS app opens with your location ready to send to them. Group riders are alerted automatically.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            contacts.forEachIndexed { i, c ->
                fun update(n: EmergencyContact) = onChange(p.copy(emergencyContacts = contacts.toMutableList().also { it[i] = n }))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Contact ${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (contacts.size > 1) {
                            IconButton(onClick = { onChange(p.copy(emergencyContacts = contacts.filterIndexed { j, _ -> j != i })) }) {
                                Icon(Icons.Default.Delete, "Remove contact")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppTextField(c.name, { update(c.copy(name = it)) }, "Name", Modifier.weight(1.2f))
                        AppTextField(c.relation, { update(c.copy(relation = it)) }, "Relation", Modifier.weight(1f))
                    }
                    AppTextField(c.phone, { update(c.copy(phone = it.filter { ch -> ch.isDigit() || ch == '+' })) }, "Phone",
                        keyboardType = KeyboardType.Phone, leading = Icons.Default.Phone)
                }
            }
            if (contacts.size < 3) {
                OutlinedButton(onClick = { onChange(p.copy(emergencyContacts = contacts + EmergencyContact())) }) {
                    Icon(Icons.Default.Add, null); Text(" Add contact")
                }
            }
            AppTextField(p.medicalNotes, { onChange(p.copy(medicalNotes = it)) }, "Allergies / medical conditions (optional)",
                singleLine = false, minLines = 2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
