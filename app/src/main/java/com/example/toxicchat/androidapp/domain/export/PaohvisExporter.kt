package com.example.toxicchat.androidapp.domain.export

import android.content.Context
import com.example.toxicchat.androidapp.data.local.AnalysisDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
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
    ): File = withContext(Dispatchers.IO) {
        val messages = analysisDao.getMessagesLiteInRange(conversationId, startMs, endMs)
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

        // 1. Aggregations
        val messagesBySlotAndSpeaker = messages.groupBy { getTimeSlot(it.timestampEpochMillis) }
            .mapValues { (_, slotMsgs) -> slotMsgs.groupBy { it.speakerRaw ?: "Sconosciuto" } }

        val allParticipants = messages.mapNotNull { it.speakerRaw }.distinct()
        val totalMessagesChat = messages.size
        
        // Calculate total possible slots in range for Presence
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

        // 2. Calculate Group (CORE/OCCASIONAL) - Stable over range
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

        // 3. Prepare File
        val dir = File(context.getExternalFilesDir(null), "Documents/exports")
        if (!dir.exists()) dir.mkdirs()
        
        val fileName = "paohvis_${conversationId}_${actualGranularity}_${startMs}_${endMs}.csv"
        val file = File(dir, fileName)

        OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { writer ->
            // Header in CAPS
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
        file
    }

    private fun csvEscape(value: String): String {
        if (value.contains(",") || value.contains(";") || value.contains("\n") || value.contains("\"")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
