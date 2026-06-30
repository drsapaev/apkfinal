package com.aistudio.clinicsystem.ui.screens.staff

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.R
import com.aistudio.clinicsystem.ui.theme.Radius

/**
 * P-02 completion: extracted from StaffScreen.kt — cancel appointment dialog
 * with reason input.
 *
 * State is hoisted via parameters.
 */
@Composable
fun StaffCancelReasonDialog(
    visible: Boolean,
    cancelReasonInput: String,
    onReasonInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_cancel_reason)) },
        text = {
            OutlinedTextField(
                value = cancelReasonInput,
                onValueChange = onReasonInputChange,
                label = { Text("Причина отмены") },
                placeholder = { Text("Врач заболел или время занято") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    focusedLabelColor = accentColor
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Отменить запись", color = MaterialTheme.colorScheme.surface)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_nazad))
            }
        }
    )
}
