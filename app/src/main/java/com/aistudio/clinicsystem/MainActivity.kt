package com.aistudio.clinicsystem

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aistudio.clinicsystem.data.session.SessionState
import com.aistudio.clinicsystem.ui.screens.SyncConsoleView
import com.aistudio.clinicsystem.ui.theme.MyApplicationTheme
import com.aistudio.clinicsystem.ui.viewmodel.ClinicViewModel
import com.aistudio.clinicsystem.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Stage 2.8: MainActivity is now @AndroidEntryPoint (required for Hilt
 * ViewModel injection) and the navigation decision is driven by
 * [SessionRepository.sessionState] (the SSOT), not by ClinicViewModel's
 * local `_currentUser` flow.
 *
 * Closes audit finding H-6 (navigation depending on per-ViewModel state).
 *
 * The `viewModel()` factory call is replaced with `by viewModels()` (KTX
 * property delegate) — Hilt's `@HiltViewModel` annotation handles
 * construction.
 *
 * NetworkMonitor.startMonitoring() is moved to Application.onCreate
 * (Stage 2.9); MainActivity no longer touches it.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ClinicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Stage 7.3 (UI-18 fix): install splash screen BEFORE super.onCreate
        installSplashScreen()
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannels(this)

        // Stage 2.5: NetworkMonitor is started from Application.onCreate
        // (singleton). SyncWorkScheduler still needs a Context — kept here
        // for now, will move to Application in Stage 2.9.
        com.aistudio.clinicsystem.utils.SyncWorkScheduler.schedulePeriodicSync(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            if (permissionStatus != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101,
                )
            }
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val systemTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemTheme
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                // Stage 2.8: single source of truth for navigation.
                val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (val state = sessionState) {
                                    is SessionState.Loading -> {
                                        // Splash / spinner while SessionRepository
                                        // verifies cached tokens against the backend.
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }

                                    is SessionState.Unauthenticated -> {
                                        com.aistudio.clinicsystem.ui.navigation.ClinicNavGraph(
                                            navController = androidx.navigation.compose.rememberNavController(),
                                            viewModel = viewModel,
                                            startDestination = "auth",
                                        )
                                    }

                                    is SessionState.RequiresTwoFactor -> {
                                        // Stage 6: full 2FA UI. For now, route to AuthScreen
                                        // with the 2FA challenge token in savedStateHandle.
                                        com.aistudio.clinicsystem.ui.navigation.ClinicNavGraph(
                                            navController = androidx.navigation.compose.rememberNavController(),
                                            viewModel = viewModel,
                                            startDestination = "auth",
                                        )
                                    }

                                    is SessionState.Authenticated -> {
                                        // P0-5 audit fix: use UserRole.fromBackend(...).isStaff
                                        // instead of comparing against the literal "STAFF" string.
                                        //
                                        // The backend returns one of these roles (case-sensitive,
                                        // see backend app/core/security.py):
                                        //   "Patient", "Doctor", "Registrar", "Receptionist",
                                        //   "Lab", "Cashier", "cardio", "derma", "dentist", "Admin"
                                        //
                                        // There is NO "STAFF" role on the backend. The previous
                                        // comparison `state.user?.role == "STAFF"` was ALWAYS false
                                        // — every user (including doctors, registrars, admins)
                                        // was routed to the PatientScreen.
                                        //
                                        // UserRole.fromBackend() handles null/blank safely (defaults
                                        // to PATIENT) and treats any non-PATIENT role as staff.
                                        val role = com.aistudio.clinicsystem.domain.model.UserRole
                                            .fromBackend(state.user?.role)
                                        val startDest = if (role.isStaff) "staff" else "patient"
                                        com.aistudio.clinicsystem.ui.navigation.ClinicNavGraph(
                                            navController = androidx.navigation.compose.rememberNavController(),
                                            viewModel = viewModel,
                                            startDestination = startDest,
                                        )
                                    }

                                    is SessionState.SessionExpired -> {
                                        // Stage 6 (UI-6 fix): show "Session expired" dialog.
                                        // Medium-5 audit fix: i18n strings.
                                        AlertDialog(
                                            onDismissRequest = { viewModel.acknowledgeSessionExpired() },
                                            title = { Text(stringResource(R.string.auth_session_expired_title)) },
                                            text = {
                                                Text(
                                                    stringResource(R.string.auth_session_expired_text),
                                                )
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { viewModel.acknowledgeSessionExpired() }) {
                                                    Text(stringResource(R.string.auth_login_again))
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            // SyncConsoleView is DEBUG-only — gated by BuildConfig.DEBUG.
                            // Stage 6 will move it to src/debug/java/.
                            if (BuildConfig.DEBUG) {
                                SyncConsoleView(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
