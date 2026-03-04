package com.example.toxicchat.androidapp.domain.analyzer

import com.example.toxicchat.androidapp.data.local.HeatmapCellEntity
import com.example.toxicchat.androidapp.data.local.ResponseStatsEntity
import com.example.toxicchat.androidapp.data.local.SpeakerStatEntity
import com.example.toxicchat.androidapp.data.local.WeeklyPointEntity
import com.example.toxicchat.androidapp.domain.model.MessageLite
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields

object StatisticsEngine {

    private const val MIN_MESSAGES_THRESHOLD = 30

    /**
     * Calcola la serie settimanale basandosi sui messaggi analizzati.
     * Esclude i messaggi di sistema o non ancora analizzati (toxScore == null).
     */
    fun computeWeeklySeries(conversationId: String, messages: List<MessageLite>): List<WeeklyPointEntity> {
        val validMessages = messages.filter { it.speakerRaw != null && it.toxScore != null }
        if (validMessages.isEmpty()) return emptyList()

        val weekFields = WeekFields.ISO

        return validMessages
            .groupBy { msg ->
                val dt = Instant.ofEpochMilli(msg.timestampEpochMillis).atZone(ZoneId.systemDefault())
                val year = dt.get(weekFields.weekBasedYear())
                val week = dt.get(weekFields.weekOfWeekBasedYear())
                "$year-W%02d".format(week)
            }
            .map { (weekId, msgs) ->
                val total = msgs.size
                val toxic = msgs.count { it.isToxic == true }
                WeeklyPointEntity(
                    conversationId = conversationId,
                    weekId = weekId,
                    totalMessages = total,
                    toxicMessages = toxic,
                    toxicRate = if (total > 0) toxic.toDouble() / total else 0.0
                )
            }
            .sortedBy { it.weekId }
    }

    /**
     * Calcola la heatmap giorno x ora basandosi sui messaggi analizzati.
     * Esclude i messaggi di sistema o non ancora analizzati (toxScore == null).
     */
    fun computeHeatmap(conversationId: String, messages: List<MessageLite>): List<HeatmapCellEntity> {
        val validMessages = messages.filter { it.speakerRaw != null && it.toxScore != null }
        val groups = validMessages.groupBy { msg ->
            val dt = Instant.ofEpochMilli(msg.timestampEpochMillis).atZone(ZoneId.systemDefault())
            dt.dayOfWeek.value to dt.hour
        }

        val allCells = mutableListOf<HeatmapCellEntity>()
        for (dow in 1..7) {
            for (hour in 0..23) {
                val msgs = groups[dow to hour] ?: emptyList()
                val total = msgs.size
                val toxic = msgs.count { it.isToxic == true }
                allCells.add(
                    HeatmapCellEntity(
                        conversationId = conversationId,
                        dayOfWeek = dow,
                        hour = hour,
                        totalCount = total,
                        toxicCount = toxic,
                        toxicRate = if (total > 0) toxic.toDouble() / total else 0.0
                    )
                )
            }
        }
        return allCells
    }

    /**
     * Calcola statistiche tempi di risposta (solo 1-a-1).
     * - Ordina sempre per timestamp (robusto)
     * - Calcola differenze in secondi (evita median=0 quando molte risposte < 60s)
     * - Converte in minuti (Double) con decimali nel risultato
     */
    fun computeResponseStats(
        conversationId: String,
        messages: List<MessageLite>,
        isGroupConversation: Boolean,
        selectedSelfName: String?,
        selfAliases: List<String>
    ): ResponseStatsEntity? {
        if (isGroupConversation) return null
        if (selectedSelfName.isNullOrBlank()) return null

        val selfSet = buildSet {
            add(normalizeName(selectedSelfName))
            selfAliases.forEach { add(normalizeName(it)) }
        }

        val sorted = messages
            .filter { it.speakerRaw != null }
            .sortedBy { it.timestampEpochMillis }

        if (sorted.size < 2) return null

        val selfToOtherSec = mutableListOf<Long>()
        val otherToSelfSec = mutableListOf<Long>()

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]

            val prevSpeaker = prev.speakerRaw ?: continue
            val currSpeaker = curr.speakerRaw ?: continue
            if (prevSpeaker == currSpeaker) continue

            val diffSec = (curr.timestampEpochMillis - prev.timestampEpochMillis) / 1000L
            if (diffSec < 0L || diffSec >= 86400L) continue // < 24h

            val prevIsSelf = selfSet.contains(normalizeName(prevSpeaker))
            val currIsSelf = selfSet.contains(normalizeName(currSpeaker))

            if (prevIsSelf && !currIsSelf) selfToOtherSec.add(diffSec)
            else if (!prevIsSelf && currIsSelf) otherToSelfSec.add(diffSec)
        }

        if (selfToOtherSec.isEmpty() && otherToSelfSec.isEmpty()) return null

        return ResponseStatsEntity(
            conversationId = conversationId,
            medianSelfToOtherMin = median(selfToOtherSec) / 60.0,
            medianOtherToSelfMin = median(otherToSelfSec) / 60.0,
            meanSelfToOtherMin = mean(selfToOtherSec) / 60.0,
            meanOtherToSelfMin = mean(otherToSelfSec) / 60.0
        )
    }

    /**
     * Calcola distribuzione “quota sopra soglia” per partecipante.
     * - Esclude messaggi non analizzati / system: toxScore == null
     * - Esclude speakerRaw null
     * - 1-a-1: bucket IO / ALTRO (selfSet)
     * - Gruppo: bucket per speakerKey normalizzato, label = raw più frequente
     * - Anti-distorsione: in gruppo aggrega speaker con total < MIN_MESSAGES_THRESHOLD in "Altri"
     */
    fun computeSpeakerStats(
        conversationId: String,
        messages: List<MessageLite>,
        isGroupConversation: Boolean,
        selectedSelfName: String?,
        selfAliases: List<String>
    ): List<SpeakerStatEntity> {

        val valid = messages.filter { it.toxScore != null && it.speakerRaw != null }
        if (valid.isEmpty()) return emptyList()

        val selfSet = buildSet {
            if (!selectedSelfName.isNullOrBlank()) add(normalizeName(selectedSelfName))
            selfAliases.forEach { add(normalizeName(it)) }
        }

        data class Acc(
            var total: Int = 0,
            var toxic: Int = 0,
            val labelCounts: MutableMap<String, Int> = mutableMapOf()
        )

        val map = mutableMapOf<String, Acc>()

        fun addMsg(key: String, label: String, isToxic: Boolean) {
            val acc = map.getOrPut(key) { Acc() }
            acc.total += 1
            if (isToxic) acc.toxic += 1
            acc.labelCounts[label] = (acc.labelCounts[label] ?: 0) + 1
        }

        for (m in valid) {
            val raw = m.speakerRaw!!
            val norm = normalizeName(raw)
            val isTox = (m.isToxic == true)

            if (!isGroupConversation) {
                val isSelf = selfSet.contains(norm)
                val key = if (isSelf) "self" else "other"
                val label = if (isSelf) "IO" else "ALTRO"
                addMsg(key, label, isTox)
            } else {
                // key normalizzato, label raw (ma scegliamo poi la più frequente)
                addMsg(norm, raw.trim(), isTox)
            }
        }

        fun buildEntity(key: String, acc: Acc, forcedLabel: String? = null): SpeakerStatEntity {
            val label = forcedLabel ?: (acc.labelCounts.maxByOrNull { it.value }?.key ?: key)
            val rate = if (acc.total > 0) acc.toxic.toDouble() / acc.total else 0.0
            return SpeakerStatEntity(
                conversationId = conversationId,
                speakerKey = key,
                speakerLabel = label,
                totalCount = acc.total,
                toxicCount = acc.toxic,
                toxicRate = rate
            )
        }

        val base = map.map { (k, acc) -> buildEntity(k, acc) }

        val sorter = compareByDescending<SpeakerStatEntity> { it.toxicRate }
            .thenByDescending { it.totalCount }

        return if (isGroupConversation) {
            val (main, small) = base.partition { it.totalCount >= MIN_MESSAGES_THRESHOLD }
            val out = main.toMutableList()

            if (small.isNotEmpty()) {
                val sumTotal = small.sumOf { it.totalCount }
                val sumToxic = small.sumOf { it.toxicCount }
                out.add(
                    SpeakerStatEntity(
                        conversationId = conversationId,
                        speakerKey = "others_lt_$MIN_MESSAGES_THRESHOLD",
                        speakerLabel = "Altri (<$MIN_MESSAGES_THRESHOLD msg)",
                        totalCount = sumTotal,
                        toxicCount = sumToxic,
                        toxicRate = if (sumTotal > 0) sumToxic.toDouble() / sumTotal else 0.0
                    )
                )
            }

            out.sortedWith(sorter)
        } else {
            base.sortedWith(sorter)
        }
    }

    private fun normalizeName(s: String): String =
        s.trim().lowercase()

    // median e mean lavorano su secondi (Long) e ritornano Double
    private fun median(l: List<Long>): Double {
        if (l.isEmpty()) return 0.0
        val s = l.sorted()
        return if (s.size % 2 == 0) {
            (s[s.size / 2] + s[s.size / 2 - 1]) / 2.0
        } else {
            s[s.size / 2].toDouble()
        }
    }

    private fun mean(l: List<Long>): Double {
        if (l.isEmpty()) return 0.0
        return l.average()
    }
}