package com.aistudio.clinicsystem

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.clinicsystem.ui.screens.AuthScreen
import com.aistudio.clinicsystem.ui.screens.PatientScreen
import com.aistudio.clinicsystem.ui.screens.StaffScreen
import com.aistudio.clinicsystem.ui.screens.SyncConsoleView
import com.aistudio.clinicsystem.ui.theme.MyApplicationTheme
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.aistudio.clinicsystem.ui.viewmodel.ClinicViewModel
import com.aistudio.clinicsystem.utils.NotificationHelper

import androidx.core.app.ActivityCompat

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        NotificationHelper.createNotificationChannels(this)
        
        // Start background managers & connection callbacks
        com.aistudio.clinicsystem.utils.NetworkMonitor.startMonitoring(this)
        com.aistudio.clinicsystem.utils.SyncWorkScheduler.schedulePeriodicSync(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            if (permissionStatus != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        enableEdgeToEdge()
        setContent {
            val viewModel: ClinicViewModel = viewModel(
                factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val systemTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemTheme
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
                val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
                
                val navController = androidx.navigation.compose.rememberNavController()
                
                // Observe Auth State to Route Automatically
                LaunchedEffect(currentUser, currentRole) {
                    if (currentUser == null) {
                        navController.navigate("auth") {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else if (currentRole == "PATIENT") {
                        navController.navigate("patient") {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        navController.navigate("staff") {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Main Screen Section via NavHost
                            Box(modifier = Modifier.weight(1f)) {
                                com.aistudio.clinicsystem.ui.navigation.ClinicNavGraph(
                                    navController = navController,
                                    viewModel = viewModel,
                                    startDestination = "auth"
                                )
                            }

                            // E1.3: DemoSandboxToggleBar was removed in M0.
                            // Role switching without re-authentication is a security ship-blocker.
                            // Real role-based routing is driven by the JWT claims + ClinicViewModel.currentUser.

                            // Database and FastAPI HTTP synchronizer console (sticky at bottom)
                            // E5.6 (later milestone) will gate this behind BuildConfig.DEBUG.
                            SyncConsoleView(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

