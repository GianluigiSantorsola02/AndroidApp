package com.example.toxicchat.androidapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.model.Speaker
import com.example.toxicchat.androidapp.domain.model.Source
import com.example.toxicchat.androidapp.domain.model.isSelf
import com.example.toxicchat.androidapp.ui.util.ToxicityLevel
import com.example.toxicchat.androidapp.ui.util.WhatsAppColorUtils
import com.example.toxicchat.androidapp.ui.util.getToxicityLevel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MessageBubble(
    message: MessageRecord,
    selectedSelfName: String?,
    selfAliases: List<String>,
    isGroupChat: Boolean
) {
    if (message.isSystem) {
        SystemMessage(message.textOriginal)
        return
    }

    val isSelf = message.isSelf(selectedSelfName, selfAliases)
    val alignment = if (isSelf) Alignment.CenterEnd else Alignment.CenterStart
    
    // WhatsApp Colors: Self #E7FFDB, Other White. SFONDO SEMPRE STANDARD.
    val bubbleColor = if (isSelf) Color(0xFFE7FFDB) else Color.White
    
    val toxLevel = getToxicityLevel(message.toxScore)
    val toxStroke = getToxicityStroke(toxLevel)

    var showDetails by remember { mutableStateOf(false) }

    val shape = if (isSelf) {
        RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            shadowElevation = 0.5.dp,
            modifier = Modifier
                .semantics {
                    if (toxLevel != ToxicityLevel.NONE) {
                        contentDescription = "Messaggio segnalato: livello ${if(toxLevel == ToxicityLevel.HIGH) "alto" else "medio"}"
                    }
                }
                .then(
                    if (toxLevel != ToxicityLevel.NONE) {
                        Modifier.clickable { showDetails = true }
                    } else Modifier
                )
                .drawToxicityBorder(toxLevel, toxStroke, shape)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .widthIn(max = 280.dp)
            ) {
                // Show name only if OTHER and it's a group chat
                if (!isSelf && isGroupChat && message.speakerRaw != null) {
                    Text(
                        text = message.speakerRaw,
                        style = MaterialTheme.typography.labelLarge,
                        color = WhatsAppColorUtils.getSenderColor(message.speakerRaw),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message.textOriginal,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = formatTime(message.timestampIso8601),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }

    if (showDetails) {
        ToxicityDetailsDialog(
            level = toxLevel,
            score = message.toxScore,
            onDismiss = { showDetails = false }
        )
    }
}

/**
 * Custom Modifier per disegnare il bordo (pieno o tratteggiato) seguendo lo shape della bubble.
 */
@Composable
private fun Modifier.drawToxicityBorder(
    level: ToxicityLevel,
    strokeInfo: ToxicityStroke?,
    shape: Shape
): Modifier {
    if (level == ToxicityLevel.NONE || strokeInfo == null) return this
    
    val density = LocalDensity.current
    return this.drawWithContent {
        drawContent()
        
        val strokeWidthPx = with(density) { strokeInfo.width.toPx() }
        val dashEffect = if (level == ToxicityLevel.MEDIUM) {
            val dashOn = with(density) { 8.dp.toPx() }
            val dashOff = with(density) { 6.dp.toPx() }
            PathEffect.dashPathEffect(floatArrayOf(dashOn, dashOff), 0f)
        } else null

        val outline = shape.createOutline(size, layoutDirection, density)
        drawOutline(
            outline = outline,
            color = strokeInfo.color,
            style = Stroke(
                width = strokeWidthPx,
                pathEffect = dashEffect
            )
        )
    }
}

private data class ToxicityStroke(
    val color: Color,
    val width: Dp
)

@Composable
private fun getToxicityStroke(level: ToxicityLevel): ToxicityStroke? {
    return when (level) {
        ToxicityLevel.MEDIUM -> ToxicityStroke(
            color = Color(0xFFFFD600).copy(alpha = 0.7f), // Giallo warning con alpha
            width = 1.5.dp
        )
        ToxicityLevel.HIGH -> ToxicityStroke(
            color = Color(0xFFF44336).copy(alpha = 0.8f), // Rosso danger con alpha
            width = 2.dp
        )
        ToxicityLevel.NONE -> null
    }
}

@Composable
fun ToxicityDetailsDialog(
    level: ToxicityLevel,
    score: Double?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Segnalazione") },
        text = {
            Column {
                DetailRow("Livello:", when(level) {
                    ToxicityLevel.MEDIUM -> "Medio"
                    ToxicityLevel.HIGH -> "Alto"
                    else -> "Nessuno"
                })
                DetailRow("Motivo:", "Rilevato linguaggio potenzialmente non appropriato")
                if (score != null) {
                    DetailRow("Confidenza:", "%.0f%%".format(score * 100))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
        Text(value)
    }
}

@Composable
fun SystemMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFFE1F5FE).copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Normal,
                color = Color(0xFF455A64),
                lineHeight = 14.sp
            )
        }
    }
}

private fun formatTime(isoTimestamp: String): String {
    return try {
        val dt = OffsetDateTime.parse(isoTimestamp)
        dt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        ""
    }
}

// --- Previews ---

@Preview(showBackground = true, backgroundColor = 0xFFECE5DD)
@Composable
fun PreviewMessageBubbleNone() {
    val msg = MessageRecord(
        conversationId = "1", messageId = 1, timestampIso8601 = "2023-10-10T10:00:00Z",
        timestampEpochMillis = 0, speaker = Speaker.OTHER, speakerRaw = "Luca",
        textOriginal = "Ciao, come stai?", source = Source.WHATSAPP_TXT,
        isSystem = false, toxScore = 0.05
    )
    MessageBubble(msg, null, emptyList(), false)
}

@Preview(showBackground = true, backgroundColor = 0xFFECE5DD)
@Composable
fun PreviewMessageBubbleMedium() {
    val msg = MessageRecord(
        conversationId = "1", messageId = 2, timestampIso8601 = "2023-10-10T10:01:00Z",
        timestampEpochMillis = 0, speaker = Speaker.OTHER, speakerRaw = "Marco",
        textOriginal = "Sei un idiota!", source = Source.WHATSAPP_TXT,
        isSystem = false, toxScore = 0.65
    )
    MessageBubble(msg, null, emptyList(), false)
}

@Preview(showBackground = true, backgroundColor = 0xFFECE5DD)
@Composable
fun PreviewMessageBubbleHigh() {
    val msg = MessageRecord(
        conversationId = "1", messageId = 3, timestampIso8601 = "2023-10-10T10:02:00Z",
        timestampEpochMillis = 0, speaker = Speaker.SELF, speakerRaw = "Io",
        textOriginal = "Ti odio a morte!", source = Source.WHATSAPP_TXT,
        isSystem = false, toxScore = 0.95
    )
    MessageBubble(msg, "Io", listOf("Io"), false)
}
