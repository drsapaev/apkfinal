package com.aistudio.clinicsystem.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.clinicsystem.ui.viewmodel.AuthViewModel
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.aistudio.clinicsystem.R

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    // E1.5: secure the auth screen — prevents screenshots/screen recording
    // of credentials input.
    com.aistudio.clinicsystem.ui.components.SecureScreen {
        AuthScreenContent(viewModel, modifier)
    }
}

@Composable
private fun AuthScreenContent(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val username by viewModel.phoneInput.collectAsStateWithLifecycle()
    val password by viewModel.otpInput.collectAsStateWithLifecycle()
    val isOtpSent by viewModel.isOtpSent.collectAsStateWithLifecycle()
    val timer by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()

    var showBiometricSelector by remember { mutableStateOf(false) }
    var selectedBioUserPhone by remember { mutableStateOf("") }
    var showVerificationDialog by remember { mutableStateOf(false) }

    // Dark slate teal color scheme matching an elegant clinical dashboard
    val tealPrimary = MaterialTheme.colorScheme.primary
    val tealLight = MaterialTheme.colorScheme.surfaceVariant
    val tealAccent = MaterialTheme.colorScheme.tertiary
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant)
    )

    LaunchedEffect(Unit) {
        com.aistudio.clinicsystem.utils.AnalyticsManager.trackScreen("AuthScreen")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(backgroundBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
        ) {
            // Heartbeat/Clinic Icon logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(tealLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = "Clinic Logo",
                    tint = tealPrimary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "MyClinic System",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Система управления клиникой и взаимодействия с пациентами",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.heightIn(min = 44.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Вход в систему",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Введите ваши учетные данные:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { viewModel.updateUsernameInput(it) },
                            label = { Text("Имя пользователя") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User icon",
                                    tint = tealPrimary
                                )
                            },
                            placeholder = { Text(stringResource(R.string.ui_login)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tealPrimary,
                                unfocusedBorderColor = Color.LightGray,
                                focusedLabelColor = tealPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { viewModel.updatePasswordInput(it) },
                            label = { Text(stringResource(R.string.ui_parol)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock icon",
                                    tint = tealPrimary
                                )
                            },
                            placeholder = { Text(stringResource(R.string.ui_parol)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tealPrimary,
                                unfocusedBorderColor = Color.LightGray,
                                focusedLabelColor = tealPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    authError?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isSyncing) {
                        CircularProgressIndicator(color = tealPrimary)
                    } else {
                        Button(
                            onClick = {
                                viewModel.login()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ui_voyti),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Biometric Auth Access Section
            val usersWithBio = allUsers.filter { it.biometricEnabled }

            if (usersWithBio.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (usersWithBio.size == 1) {
                                selectedBioUserPhone = usersWithBio.first().phone
                                showVerificationDialog = true
                            } else {
                                showBiometricSelector = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(tealPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Login",
                                tint = MaterialTheme.colorScheme.surface
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Войти по биометрии",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Быстрый отпечаток / Face ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF00838F)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForwardIos,
                            contentDescription = "Arrow right",
                            tint = tealPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                // Info text explaining how to set up bio log in
                Text(
                    text = "Подсказка: Войдите по логину и паролю\nи активируйте Биометрический вход в личном кабинете.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // E1.2: The "🚀 Тестовые аккаунты:" demo-login card with quick-login chips
            // for "patient" and "admin" accounts was removed in M0 for production safety.
            // Test users must be created on the backend and authenticated normally.
        }
    }

    // Biometric selector dialog
    if (showBiometricSelector) {
        AlertDialog(
            onDismissRequest = { showBiometricSelector = false },
            title = { Text("Выбор аккаунта для входа") },
            text = {
                Column {
                    allUsers.filter { it.biometricEnabled }.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBioUserPhone = user.phone
                                    showBiometricSelector = false
                                    showVerificationDialog = true
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (user.role == "STAFF") Icons.Default.MedicalServices else Icons.Default.Person,
                                contentDescription = null,
                                tint = tealPrimary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(text = user.fullName, fontWeight = FontWeight.Bold)
                                Text(text = "${user.role} • ${user.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBiometricSelector = false }) {
                    Text(stringResource(R.string.ui_zakryt))
                }
            }
        )
    }

    // Biometric Hardware Verification Prompt
    LaunchedEffect(showVerificationDialog) {
        if (showVerificationDialog) {
            val fragmentActivity = context as? FragmentActivity
            if (fragmentActivity != null) {
                val executor = ContextCompat.getMainExecutor(fragmentActivity)

                // Stage 4.4 (C-7 final fix): use TWO-ARG authenticate() with
                // a CryptoObject. The previous one-arg form was security
                // theater — biometric "success" simply unlocked use of an
                // already-stored JWT. Now biometric success unlocks the
                // Cipher that decrypts the refresh token.
                //
                // The CryptoObject is created from a keystore key with
                // setUserAuthenticationRequired(true) — without biometric,
                // the Cipher cannot be used.
                val cryptoObject = com.aistudio.clinicsystem.utils.BiometricCryptoHelper
                    .createDecryptionCryptoObjectForLogin(context)

                if (cryptoObject == null) {
                    // No biometric key enrolled, OR key was invalidated by
                    // new fingerprint enrollment. Fall back to password login.
                    Toast.makeText(
                        context,
                        "Биометрический ключ недоступен. Войдите по паролю.",
                        Toast.LENGTH_LONG,
                    ).show()
                    showVerificationDialog = false
                    return@LaunchedEffect
                }

                val biometricPrompt = BiometricPrompt(
                    fragmentActivity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            Toast.makeText(context, "Ошибка: $errString", Toast.LENGTH_SHORT).show()
                            showVerificationDialog = false
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            showVerificationDialog = false
                            // Stage 4.4: the CryptoObject's Cipher is now
                            // unlocked. The ViewModel will use it to decrypt
                            // the stored refresh token.
                            val cipher = result.cryptoObject?.cipher
                            viewModel.loginWithBiometrics(selectedBioUserPhone, cipher)
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            Toast.makeText(context, "Отпечаток не распознан", Toast.LENGTH_SHORT).show()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Вход по биометрии")
                    .setSubtitle("Приложите палец для подтверждения входа")
                    // Stage 4.4: BIOMETRIC_STRONG only — Class 3 biometrics
                    // required to unlock the keystore key.
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText(stringResource(R.string.ui_otmena))
                    .build()

                // Stage 4.4: two-arg authenticate with CryptoObject.
                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                Toast.makeText(context, "Ошибка: требуется FragmentActivity", Toast.LENGTH_SHORT).show()
                showVerificationDialog = false
            }
        }
    }
}
