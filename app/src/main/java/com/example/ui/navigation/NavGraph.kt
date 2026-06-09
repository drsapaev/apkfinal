package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PatientScreen
import com.example.ui.screens.StaffScreen
import com.example.ui.viewmodel.ClinicViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.AuthViewModel
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
        composable("auth") {
            val authViewModel: AuthViewModel = viewModel()
            authViewModel.onLoginSuccess = {
                viewModel.refreshSession()
            }
            AuthScreen(viewModel = authViewModel)
        }
        composable("patient") {
            val patientViewModel: PatientViewModel = viewModel()
            patientViewModel.onLogoutSuccess = {
                viewModel.refreshSession() // or set current role logic
            }
            PatientScreen(viewModel = patientViewModel)
        }
        composable("staff") {
            val staffViewModel: StaffViewModel = viewModel()
            staffViewModel.onLogoutSuccess = {
                viewModel.refreshSession()
            }
            StaffScreen(viewModel = staffViewModel)
        }
    }
}

