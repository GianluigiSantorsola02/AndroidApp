package com.example.toxicchat.androidapp.domain.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.toxicchat.androidapp.data.local.AnalysisDao
import com.example.toxicchat.androidapp.domain.model.MessageEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class PaohvisGranularity { AUTO, WEEK, DAY }

@Singleton
class PaohvisExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analysisDao: AnalysisDao
) {

    suspend fun export(
        conversationId: String,
        chatTitle: String,
        startMs: Long,
        endMs: Long,
        granularity: PaohvisGranularity
    ): Uri? = withContext(Dispatchers.IO) {
        val messagesRaw = analysisDao.getMessagesLiteInRange(conversationId, startMs, endMs)
        
        // Mappatura da database projection a domain model MessageEvent per coerenza con saveCsvToDownloads
        val messages = messagesRaw.map {
            MessageEvent(
                id = it.id,
                timestampEpochMillis = it.timestampEpochMillis,
                speakerRaw = it.speakerRaw ?: "Sconosciuto",
                content = it.content ?: "",
                toxScore = it.toxScore,
                isToxic = it.isToxic ?: false
            )
        }

        val actualGranularity = when (granularity) {
            PaohvisGranularity.DAY -> PaohvisGranularity.DAY
            PaohvisGranularity.WEEK -> PaohvisGranularity.WEEK
            PaohvisGranularity.AUTO -> {
                val days = ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate(),
                    Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault()).toLocalDate()
                )
                if (days > 45) PaohvisGranularity.WEEK else PaohvisGranularity.DAY
            }
        }

        val weekFields = WeekFields.ISO
        val dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        fun getTimeSlot(ts: Long): String {
            val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
            return if (actualGranularity == PaohvisGranularity.DAY) {
                date.format(dayFormatter)
            } else {
                val weekNumber = date.get(weekFields.weekOfWeekBasedYear())
                val weekYear = date.get(weekFields.weekBasedYear())
                "%d-W%02d".format(weekYear, weekNumber)
            }
        }

        val messagesBySlotAndSpeaker = messages.groupBy { getTimeSlot(it.timestampEpochMillis) }
            .mapValues { (_, slotMsgs) -> slotMsgs.groupBy { it.speakerRaw } }

        val allParticipants = messages.map { it.speakerRaw }.distinct()
        val totalMessagesChat = messages.size
        
        val startDate = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val totalSlotsInRange = if (actualGranularity == PaohvisGranularity.DAY) {
            ChronoUnit.DAYS.between(startDate, endDate) + 1
        } else {
            ChronoUnit.WEEKS.between(
                startDate.with(weekFields.dayOfWeek(), 1),
                endDate.with(weekFields.dayOfWeek(), 1)
            ) + 1
        }.toInt().coerceAtLeast(1)

        val speakerMetrics = allParticipants.associateWith { speaker ->
            val speakerMsgs = messages.filter { it.speakerRaw == speaker }
            val activeSlotsCount = speakerMsgs.map { getTimeSlot(it.timestampEpochMillis) }.distinct().size
            val presence = activeSlotsCount.toDouble() / totalSlotsInRange
            val volumeShare = speakerMsgs.size.toDouble() / totalMessagesChat
            val score = 0.7 * presence + 0.3 * volumeShare
            Triple(presence, volumeShare, score)
        }

        val top20PercentScore = if (speakerMetrics.isNotEmpty()) {
            speakerMetrics.values.map { it.third }.sortedDescending()
                .let { it[(it.size * 0.2).toInt().coerceAtMost(it.size - 1)] }
        } else 0.0

        val groupNames = speakerMetrics.mapValues { (speaker, metrics) ->
            val (presence, _, score) = metrics
            if (presence >= 0.30 || score >= top20PercentScore) "CORE" else "OCCASIONAL"
        }

        // --- FIXED: NOME FILE CON DATA E GRANULARITÀ ---
        val datePart = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val fileName = "paohvis_${chatTitle.replace(" ", "_")}_${actualGranularity}_$datePart.csv"
        
        // --- SALVATAGGIO IN DOWNLOAD ---
        saveCsvToDownloads(context, fileName, messagesBySlotAndSpeaker, conversationId, chatTitle, groupNames)
    }

    private fun saveCsvToDownloads(
        context: Context,
        fileName: String,
        messagesBySlotAndSpeaker: Map<String, Map<String, List<MessageEvent>>>,
        conversationId: String,
        chatTitle: String,
        groupNames: Map<String, String>
    ): Uri? {
        val resolver = context.contentResolver
        val uri: Uri?
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            uri = Uri.fromFile(file)
        }

        try {
            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                resolver.openOutputStream(uri)
            } else {
                FileOutputStream(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName))
            }

            outputStream?.use { os ->
                OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                    writer.write("EDGE_ID,NODE_NAME,TIME_SLOT,EDGE_NAME_DESCRIPTION,GROUP_NAME,ROLE\n")

                    messagesBySlotAndSpeaker.forEach { (slot, speakersInSlot) ->
                        val edgeId = "CHAT_${conversationId}__TS_$slot"
                        val nPeopleSlot = speakersInSlot.size
                        val nMsgSlot = speakersInSlot.values.sumOf { it.size }
                        val description = csvEscape("$chatTitle • $nMsgSlot msg • $nPeopleSlot ppl")
                        
                        val maxMsgsInSlot = speakersInSlot.values.maxOfOrNull { it.size } ?: 0

                        speakersInSlot.forEach { (speaker, msgs) ->
                            val role = if (msgs.size == maxMsgsInSlot) "TOP_SPEAKER" else "ACTIVE"
                            val group = groupNames[speaker] ?: "OCCASIONAL"
                            
                            writer.write("$edgeId,${csvEscape(speaker)},$slot,$description,$group,$role\n")
                        }
                    }
                }
            }
            return uri
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun csvEscape(value: String): String {
        if (value.contains(",") || value.contains(";") || value.contains("\n") || value.contains("\"")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
