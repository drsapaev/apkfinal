package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PatientScreen
import com.example.ui.screens.StaffScreen
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ClinicViewModel
import com.example.ui.viewmodel.PatientViewModel
import com.example.ui.viewmodel.StaffViewModel

@Composable
fun ClinicNavGraph(
    navController: NavHostController,
    viewModel: ClinicViewModel,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("auth") { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel(backStackEntry)
            authViewModel.onLoginSuccess = {
                viewModel.refreshSession()
            }
            AuthScreen(viewModel = authViewModel)
        }
        composable("patient") { backStackEntry ->
            val patientViewModel: PatientViewModel = viewModel(backStackEntry)
            patientViewModel.onLogoutSuccess = {
                viewModel.refreshSession()
            }
            PatientScreen(viewModel = patientViewModel)
        }
        composable("staff") { backStackEntry ->
            val staffViewModel: StaffViewModel = viewModel(backStackEntry)
            staffViewModel.onLogoutSuccess = {
                viewModel.refreshSession()
            }
            StaffScreen(viewModel = staffViewModel)
        }
    }
}

