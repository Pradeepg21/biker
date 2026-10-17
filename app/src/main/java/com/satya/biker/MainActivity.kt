package com.satya.biker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import com.satya.biker.data.AuthRepository
import com.satya.biker.data.RiderProfile
import com.satya.biker.data.UserRepository
import com.satya.biker.data.VerificationStatus
import com.satya.biker.ui.admin.AdminApp
import com.satya.biker.ui.auth.AuthFlow
import com.satya.biker.ui.components.LoadingScreen
import com.satya.biker.ui.rider.RiderApp
import com.satya.biker.ui.theme.BikerTheme
import com.satya.biker.ui.verify.BlockedScreen
import com.satya.biker.ui.verify.PendingScreen
import com.satya.biker.ui.verify.VerificationWizard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class Missing(val uid: String) : Session
    data class Ready(val profile: RiderProfile) : Session
}

class SessionViewModel : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<Session> = AuthRepository.authState()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(Session.SignedOut)
            else UserRepository.observeProfile(uid).map { p -> if (p == null) Session.Missing(uid) else Session.Ready(p) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Session.Loading)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sosId = intent?.getStringExtra(EXTRA_SOS_ID)
        setContent {
            BikerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(openSosId = sosId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_SOS_ID = "sos_id"
    }
}

@Composable
private fun Root(openSosId: String?, vm: SessionViewModel = viewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()

    LaunchedEffect(session) {
        val s = session
        // The super admin account is promoted automatically on first sign-in.
        val email = AuthRepository.currentEmail
        when (s) {
            is Session.Missing -> runCatching { UserRepository.ensureSuperAdmin(s.uid, email.orEmpty(), null) }
            is Session.Ready -> runCatching { UserRepository.ensureSuperAdmin(s.profile.uid, email.orEmpty(), s.profile) }
            else -> Unit
        }
        if (s is Session.Ready && s.profile.verificationStatus == VerificationStatus.APPROVED) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token != s.profile.fcmToken) UserRepository.updateFcmToken(s.profile.uid, token)
            }
        }
    }

    Crossfade(targetState = screenKey(session), label = "root") { key ->
        when (val s = session) {
            Session.Loading -> LoadingScreen()
            Session.SignedOut -> AuthFlow()
            is Session.Missing -> VerificationWizard(
                RiderProfile(uid = s.uid, email = AuthRepository.currentEmail.orEmpty()),
            )
            is Session.Ready -> {
                val p = s.profile
                when {
                    p.isAdmin -> AdminApp(p)
                    key == "verify" -> VerificationWizard(p)
                    key == "pending" -> PendingScreen(p)
                    key == "blocked" -> BlockedScreen(p)
                    else -> RiderApp(p, openSosId)
                }
            }
        }
    }
}

private fun screenKey(s: Session): String = when (s) {
    Session.Loading -> "loading"
    Session.SignedOut -> "auth"
    is Session.Missing -> "verify"
    is Session.Ready -> when {
        s.profile.isAdmin -> "admin"
        else -> when (s.profile.verificationStatus) {
            VerificationStatus.INCOMPLETE, VerificationStatus.REJECTED -> "verify"
            VerificationStatus.PENDING -> "pending"
            VerificationStatus.SUSPENDED -> "blocked"
            VerificationStatus.APPROVED -> "rider"
        }
    }
}
