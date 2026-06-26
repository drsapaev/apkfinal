package com.aistudio.clinicsystem.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.clinicsystem.ui.screens.AuthScreen
import com.aistudio.clinicsystem.ui.screens.PatientScreen
import com.aistudio.clinicsystem.ui.screens.StaffScreen
import com.aistudio.clinicsystem.ui.viewmodel.AuthViewModel
import com.aistudio.clinicsystem.ui.viewmodel.ClinicViewModel
import com.aistudio.clinicsystem.ui.viewmodel.PatientViewModel
import com.aistudio.clinicsystem.ui.viewmodel.StaffViewModel

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

