package com.aistudio.clinicsystem.ui.screens.patient

import com.aistudio.clinicsystem.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.data.db.UserEntity

/**
 * P-03 refactor: Profile tab content for PatientScreen Bottom Navigation.
 *
 * Shows: profile cabinet (edit name, biometric, secure screen) +
 * Telegram bot card.
 */
@Composable
fun PatientProfileTab(
    currentUser: UserEntity?,
    onEditClick: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onLinkTelegram: (String) -> Unit,
    onUnlinkTelegram: () -> Unit,
    onTestTelegram: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.l)
    ) {
        ProfileCabinetCard(
            user = currentUser,
            onEditClick = onEditClick,
            onBiometricToggle = onBiometricToggle
        )

        TelegramBotCard(
            user = currentUser,
            onLinkClick = onLinkTelegram,
            onUnlinkClick = onUnlinkTelegram,
            onTestClick = onTestTelegram
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}
