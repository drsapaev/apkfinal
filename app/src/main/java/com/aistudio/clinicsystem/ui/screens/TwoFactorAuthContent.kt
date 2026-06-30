package com.aistudio.clinicsystem.ui.screens

import androidx.compose.ui.res.stringResource
import com.aistudio.clinicsystem.ui.theme.Spacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.clinicsystem.ui.theme.Radius
import com.aistudio.clinicsystem.ui.viewmodel.AuthViewModel

/**
 * P-16 fix: 2FA (TOTP) verification UI for AuthScreen.
 *
 * Rendered when `viewModel.pending2FAChallenge != null` — i.e. the backend
 * returned `LoginOutcome.TwoFactorRequired` after the user entered valid
 * username + password.
 *
 * Flow:
 * 1. User enters 6-digit TOTP code from authenticator app
 * 2. Optionally checks "Доверять этому устройству" (rememberDevice)
 * 3. Taps stringResource(R.string.ui_verify) → viewModel.verify2FA(totpCode, rememberDevice)
 *    - Success → onLoginSuccess callback (navigates to main screen)
 *    - Failure → authError StateFlow shows "Неверный код 2FA"
 * 4. Alternative: "Использовать recovery-код" → opens recovery flow dialog
 * 5. stringResource(R.string.ui_cancel) → viewModel.cancel2FAChallenge() (returns to login form)
 *
 * The AuthViewModel already implements the full 2FA API (verify2FA,
 * request2FARecovery, verify2FARecovery, cancel2FAChallenge) since M1/E3.4.
 * This Composable wires it up to the UI — previously dead code.
 */
@Composable
fun TwoFactorAuthContent(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    var totpCode by remember { mutableStateOf("") }
    var rememberDevice by remember { mutableStateOf(false) }
    var showRecoveryFlow by remember { mutableStateOf(false) }
    var recoveryMethod by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }

    val tealPrimary = MaterialTheme.colorScheme.primary
    val tealLight = MaterialTheme.colorScheme.surfaceVariant
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundBrush)
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .width(androidx.compose.ui.unit.Dp.Infinity.times(0.92f))
        ) {
            // Lock icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(tealLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Двухфакторная аутентификация",
                    tint = tealPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.l))

            Text(
                text = "Двухфакторная аутентификация",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Введите 6-значный код из приложения-аутентификатора",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!showRecoveryFlow) {
                // === TOTP code entry ===
                TwoFactorTotpCard(
                    totpCode = totpCode,
                    onTotpCodeChange = { newValue ->
                        // Only digits, max 6
                        if (newValue.all { it.isDigit() } && newValue.length <= 6) {
                            totpCode = newValue
                        }
                    },
                    rememberDevice = rememberDevice,
                    onRememberDeviceChange = { rememberDevice = it },
                    isSyncing = isSyncing,
                    authError = authError,
                    onVerify = { viewModel.verify2FA(totpCode, rememberDevice) },
                    onShowRecovery = { showRecoveryFlow = true },
                    onCancel = { viewModel.cancel2FAChallenge() }
                )
            } else {
                // === Recovery code flow ===
                TwoFactorRecoveryCard(
                    recoveryMethod = recoveryMethod,
                    onRecoveryMethodChange = { recoveryMethod = it },
                    recoveryCode = recoveryCode,
                    onRecoveryCodeChange = { recoveryCode = it },
                    isSyncing = isSyncing,
                    authError = authError,
                    onRequestRecovery = { viewModel.request2FARecovery(recoveryMethod) },
                    onVerifyRecovery = { viewModel.verify2FARecovery(recoveryCode) },
                    onBackToTotp = {
                        showRecoveryFlow = false
                        recoveryMethod = ""
                        recoveryCode = ""
                    }
                )
            }
        }
    }
}

@Composable
private fun TwoFactorTotpCard(
    totpCode: String,
    onTotpCodeChange: (String) -> Unit,
    rememberDevice: Boolean,
    onRememberDeviceChange: (Boolean) -> Unit,
    isSyncing: Boolean,
    authError: String?,
    onVerify: () -> Unit,
    onShowRecovery: () -> Unit,
    onCancel: () -> Unit
) {
    val tealPrimary = MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(Radius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Введите код подтверждения",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.l))

            OutlinedTextField(
                value = totpCode,
                onValueChange = onTotpCodeChange,
                label = { Text("6-значный код") },
                placeholder = { Text("123456") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    textAlign = TextAlign.Center,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = tealPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = tealPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            authError?.let { error ->
                Spacer(modifier = Modifier.height(Spacing.m))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(Spacing.l))

            // Remember device checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = rememberDevice,
                    onCheckedChange = onRememberDeviceChange
                )
                Text(
                    text = "Доверять этому устройству",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.l))

            if (isSyncing) {
                CircularProgressIndicator(color = tealPrimary)
            } else {
                Button(
                    onClick = onVerify,
                    enabled = totpCode.length == 6,
                    colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                    shape = RoundedCornerShape(Radius.medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_verify),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.surface
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            TextButton(onClick = onShowRecovery) {
                Text(
                    "Использовать recovery-код",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s))

            TextButton(onClick = onCancel) {
                Text(
                    stringResource(R.string.ui_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TwoFactorRecoveryCard(
    recoveryMethod: String,
    onRecoveryMethodChange: (String) -> Unit,
    recoveryCode: String,
    onRecoveryCodeChange: (String) -> Unit,
    isSyncing: Boolean,
    authError: String?,
    onRequestRecovery: () -> Unit,
    onVerifyRecovery: () -> Unit,
    onBackToTotp: () -> Unit
) {
    val tealPrimary = MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(Radius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Восстановление доступа",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.s))

            Text(
                text = "Укажите email или телефон — мы отправим recovery-код",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = recoveryMethod,
                onValueChange = onRecoveryMethodChange,
                label = { Text("Email или телефон") },
                placeholder = { Text("user@example.com или +7...") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = tealPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = tealPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.m))

            Button(
                onClick = onRequestRecovery,
                enabled = recoveryMethod.isNotBlank() && !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                shape = RoundedCornerShape(Radius.medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Отправить recovery-код",
                    color = MaterialTheme.colorScheme.surface,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.l))

            Text(
                text = "После получения кода введите его ниже:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.s))

            OutlinedTextField(
                value = recoveryCode,
                onValueChange = onRecoveryCodeChange,
                label = { Text("Recovery-код") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = tealPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = tealPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            authError?.let { error ->
                Spacer(modifier = Modifier.height(Spacing.m))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(Spacing.l))

            if (isSyncing) {
                CircularProgressIndicator(color = tealPrimary)
            } else {
                Button(
                    onClick = onVerifyRecovery,
                    enabled = recoveryCode.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                    shape = RoundedCornerShape(Radius.medium),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Подтвердить recovery-код",
                        color = MaterialTheme.colorScheme.surface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))

            TextButton(onClick = onBackToTotp) {
                Text(
                    "← Назад к TOTP",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
