package com.example.toxicchat.androidapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.toxicchat.androidapp.R

@Composable
fun ServerAnalysisConsentDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onReadMore: () -> Unit
) {
    var accepted by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.privacy_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Body text with manual formatting for bullet points and bold sections
                // This replaces the HTML parsing for better compatibility in Compose
                Text(
                    text = buildAnnotatedString {
                        append("Per identificare contenuti tossici, i messaggi verranno elaborati da un servizio di analisi remoto.\n\n")
                        
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("• Dati inviati: ")
                        }
                        append("Testo dei messaggi e orari.\n")
                        
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("• Finalità: ")
                        }
                        append("Sola valutazione della tossicità.\n")
                        
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("• Gestione dati: ")
                        }
                        append("I testi non vengono memorizzati permanentemente né usati per addestrare modelli AI.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Link to Privacy Policy
                Text(
                    text = stringResource(id = R.string.privacy_dialog_link),
                    color = Color(0xFF006064),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onReadMore() }
                        .padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Checkbox for consent
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { accepted = !accepted }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = accepted,
                        onCheckedChange = { accepted = it }
                    )
                    Text(
                        text = stringResource(id = R.string.privacy_dialog_checkbox),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                enabled = accepted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF006064),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                )
            ) {
                Text(stringResource(id = R.string.privacy_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.privacy_dialog_cancel))
            }
        }
    )
}
