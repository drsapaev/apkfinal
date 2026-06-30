package com.aistudio.clinicsystem.ui.screens.patient

import com.aistudio.clinicsystem.ui.theme.Spacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.clinicsystem.R

/**
 * P-07 refactor: extracted from PatientScreen.kt (was 758 LOC).
 *
 * Modal dialog to edit the patient's full name (ФИО).
 *
 * State is hoisted via parameters:
 * - [editNameInput] / [onEditNameInputChange]: controlled text field
 * - [onSave]: called when user taps "Save" — caller is responsible for
 *   invoking viewModel.updateProfileName() and closing the dialog
 * - [onDismiss]: called when user taps "Cancel" or outside the dialog
 */
@Composable
fun EditProfileDialog(
    editNameInput: String,
    onEditNameInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    tealPrimary: Color
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = tealPrimary)
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(stringResource(R.string.pat_edit_name), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Пожалуйста, введите ваше настоящее ФИО для корректного ведения электронной медицинской карты.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = onEditNameInputChange,
                    label = { Text(stringResource(R.string.pat_full_name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tealPrimary,
                        focusedLabelColor = tealPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = tealPrimary),
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text(stringResource(R.string.ui_sohranit), color = MaterialTheme.colorScheme.surface)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_otmena), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
