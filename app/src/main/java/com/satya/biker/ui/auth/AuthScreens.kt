package com.satya.biker.ui.auth

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.satya.biker.R
import com.satya.biker.data.AuthRepository
import com.satya.biker.ui.components.AppTextField
import com.satya.biker.ui.components.PasswordField
import com.satya.biker.ui.components.PrimaryButton
import com.satya.biker.ui.theme.BrandOrange
import com.satya.biker.ui.theme.BrandOrangeDark
import com.satya.biker.ui.theme.SosRed
import com.satya.biker.util.toast
import kotlinx.coroutines.launch

fun Throwable.friendlyAuthMessage(): String = when (this) {
    is FirebaseAuthInvalidUserException -> "No account found for this email."
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
    is FirebaseAuthUserCollisionException -> "An account already exists with this email."
    is FirebaseAuthWeakPasswordException -> "Password is too weak – use at least 6 characters."
    is FirebaseNetworkException -> "No internet connection. Please try again."
    else -> localizedMessage ?: "Something went wrong"
}

private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

@Composable
fun AuthFlow() {
    var register by rememberSaveable { mutableStateOf(false) }
    if (register) RegisterScreen(onBack = { register = false })
    else LoginScreen(onRegister = { register = true })
}

@Composable
private fun Brand(subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(BrandOrange, BrandOrangeDark))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.TwoWheeler, null, tint = Color.White, modifier = Modifier.size(50.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("BikersThrill", style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LoginScreen(onRegister: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showReset by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Brand("Stay connected. Ride safe.")
        Spacer(Modifier.height(12.dp))

        // One sign-in for everyone: admins are recognised from their account and land in the admin console.
        Text("Welcome back", style = MaterialTheme.typography.titleLarge)
        AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email, leading = Icons.Default.Email)
        PasswordField(password, { password = it }, leading = Icons.Default.Lock)

        error?.let { Text(it, color = SosRed, fontSize = 14.sp) }

        PrimaryButton(
            text = "Sign in",
            loading = loading,
            onClick = {
                error = null
                if (!EMAIL.matches(email.trim()) || password.isEmpty()) {
                    error = "Enter a valid email and password"; return@PrimaryButton
                }
                loading = true
                scope.launch {
                    runCatching { AuthRepository.signIn(email, password) }
                        .onFailure { error = it.friendlyAuthMessage() }
                    loading = false
                }
            },
        )
        TextButton(onClick = { showReset = true }, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }

        OrDivider()
        GoogleButton(loading = googleLoading) {
            error = null
            googleLoading = true
            scope.launch {
                runCatching { AuthRepository.signInWithGoogle(context) }
                    .onFailure { error = it.friendlyAuthMessage() }
                googleLoading = false
            }
        }

        run {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("New rider?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRegister) { Text("Create an account", fontWeight = FontWeight.Bold) }
            }
            Text(
                "Every rider is verified with ID card and driving licence before they can ride with the community.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showReset) {
        var resetEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset password") },
            text = {
                Column {
                    Text("We'll email you a link to reset your password.")
                    Spacer(Modifier.height(12.dp))
                    AppTextField(resetEmail, { resetEmail = it }, "Email", keyboardType = KeyboardType.Email)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { AuthRepository.sendPasswordReset(resetEmail) }
                            .onSuccess { context.toast("Reset link sent to $resetEmail"); showReset = false }
                            .onFailure { context.toast(it.friendlyAuthMessage()) }
                    }
                }) { Text("Send link") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RegisterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var googleLoading by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Brand("Create your rider account")
        Spacer(Modifier.height(4.dp))
        AppTextField(name, { name = it }, "Full name (as on licence)", leading = Icons.Default.Person)
        AppTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Mobile number",
            keyboardType = KeyboardType.Phone, leading = Icons.Default.Phone)
        AppTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email, leading = Icons.Default.Email)
        PasswordField(password, { password = it }, leading = Icons.Default.Lock)
        PasswordField(confirm, { confirm = it }, label = "Confirm password", leading = Icons.Default.Lock)
        error?.let { Text(it, color = SosRed, fontSize = 14.sp) }
        PrimaryButton(
            text = "Create account",
            loading = loading,
            onClick = {
                error = when {
                    name.trim().length < 3 -> "Enter your full name"
                    phone.filter { it.isDigit() }.length < 10 -> "Enter a valid mobile number"
                    !EMAIL.matches(email.trim()) -> "Enter a valid email"
                    password.length < 6 -> "Password must be at least 6 characters"
                    password != confirm -> "Passwords don't match"
                    else -> null
                }
                if (error != null) return@PrimaryButton
                loading = true
                scope.launch {
                    runCatching { AuthRepository.register(email, password, name, phone) }
                        .onFailure { error = it.friendlyAuthMessage() }
                    loading = false
                }
            },
        )
        OrDivider()
        GoogleButton(loading = googleLoading) {
            error = null
            googleLoading = true
            scope.launch {
                runCatching { AuthRepository.signInWithGoogle(context) }
                    .onFailure { error = it.friendlyAuthMessage() }
                googleLoading = false
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Already have an account? Sign in")
        }
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.weight(1f))
        Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun GoogleButton(loading: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        } else {
            Image(painterResource(R.drawable.ic_google), null, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
