package com.example

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
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PatientScreen
import com.example.ui.screens.StaffScreen
import com.example.ui.screens.SyncConsoleView
import com.example.ui.theme.MyApplicationTheme
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.viewmodel.ClinicViewModel
import com.example.utils.NotificationHelper

import androidx.core.app.ActivityCompat

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        NotificationHelper.createNotificationChannels(this)
        
        // Start background managers & connection callbacks
        com.example.utils.NetworkMonitor.startMonitoring(this)
        com.example.utils.SyncWorkScheduler.schedulePeriodicSync(this)

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
                                com.example.ui.navigation.ClinicNavGraph(
                                    navController = navController,
                                    viewModel = viewModel,
                                    startDestination = "auth"
                                )
                            }

                            // Role toggle bar (only visible if logged in, for demo/verification convenience)
                            if (currentUser != null) {
                                DemoSandboxToggleBar(viewModel = viewModel)
                            }

                            // Database and FastAPI HTTP synchronizer console (sticky at bottom)
                            SyncConsoleView(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoSandboxToggleBar(viewModel: ClinicViewModel) {
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()

    Surface(
        color = Color(0xFFE2E8F0), // Clean light slate spacer
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "САНДБОКС РОЛЕЙ:",
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color(0xFF475569)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentRole == "PATIENT",
                    onClick = { viewModel.switchRoleForDemo("PATIENT") },
                    label = { Text("Пациент (Личный Кабинет)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00897B),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = currentRole == "STAFF",
                    onClick = { viewModel.switchRoleForDemo("STAFF") },
                    label = { Text("Персонал (Панель)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1E88E5),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

