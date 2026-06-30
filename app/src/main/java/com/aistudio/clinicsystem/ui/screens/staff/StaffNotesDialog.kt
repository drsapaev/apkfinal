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
import com.aistudio.clinicsystem.R

/**
 * P-02 completion: extracted from StaffScreen.kt — add/edit notes dialog
 * for appointment.
 *
 * State is hoisted via parameters.
 */
@Composable
fun StaffNotesDialog(
    visible: Boolean,
    notesInput: String,
    onNotesInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_notes)) },
        text = {
            OutlinedTextField(
                value = notesInput,
                onValueChange = onNotesInputChange,
                label = { Text(stringResource(R.string.dlg_notes)) },
                placeholder = { Text(stringResource(R.string.dlg_notes_placeholder)) },
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
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(stringResource(R.string.ui_sohranit), color = MaterialTheme.colorScheme.surface)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_otmena))
            }
        }
    )
}
