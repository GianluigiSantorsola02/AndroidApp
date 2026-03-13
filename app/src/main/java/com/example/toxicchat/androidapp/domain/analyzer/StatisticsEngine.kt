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
                val maxTox = msgs.maxOfOrNull { it.toxScore ?: 0.0 } ?: 0.0
                WeeklyPointEntity(
                    conversationId = conversationId,
                    weekId = weekId,
                    totalMessages = total,
                    toxicMessages = toxic,
                    toxicRate = if (total > 0) toxic.toDouble() / total else 0.0,
                    maxToxScore = maxTox
                )
            }
            .sortedBy { it.weekId }
    }

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

    fun computeResponseStats(
        conversationId: String,
        messages: List<MessageLite>,
        isGroupConversation: Boolean
    ): ResponseStatsEntity? {
        if (isGroupConversation) return null

        val sorted = messages
            .filter { it.speakerRaw != null }
            .sortedBy { it.timestampEpochMillis }

        if (sorted.size < 2) return null

        val speakerAtoB = mutableListOf<Long>()
        val speakerBtoA = mutableListOf<Long>()

        // In a 1-to-1, we identify the two speakers
        val distinctSpeakers = sorted.mapNotNull { it.speakerRaw }.distinct()
        if (distinctSpeakers.size < 2) return null
        
        val s1 = distinctSpeakers[0]
        val s2 = distinctSpeakers[1]

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]

            val prevSpeaker = prev.speakerRaw ?: continue
            val currSpeaker = curr.speakerRaw ?: continue
            if (prevSpeaker == currSpeaker) continue

            val diffSec = (curr.timestampEpochMillis - prev.timestampEpochMillis) / 1000L
            if (diffSec < 0L || diffSec >= 86400L) continue // < 24h

            if (prevSpeaker == s1 && currSpeaker == s2) speakerAtoB.add(diffSec)
            else if (prevSpeaker == s2 && currSpeaker == s1) speakerBtoA.add(diffSec)
        }

        if (speakerAtoB.isEmpty() && speakerBtoA.isEmpty()) return null

        return ResponseStatsEntity(
            conversationId = conversationId,
            medianSelfToOtherMin = median(speakerAtoB) / 60.0,
            medianOtherToSelfMin = median(speakerBtoA) / 60.0,
            meanSelfToOtherMin = mean(speakerAtoB) / 60.0,
            meanOtherToSelfMin = mean(speakerBtoA) / 60.0
        )
    }

    fun computeSpeakerStats(
        conversationId: String,
        messages: List<MessageLite>,
        isGroupConversation: Boolean
    ): List<SpeakerStatEntity> {

        val valid = messages.filter { it.toxScore != null && it.speakerRaw != null }
        if (valid.isEmpty()) return emptyList()

        data class Acc(
            var total: Int = 0,
            var toxic: Int = 0,
            val labelCounts: MutableMap<String, Int> = mutableMapOf()
        )

        val map = mutableMapOf<String, Acc>()

        for (m in valid) {
            val raw = m.speakerRaw!!
            val norm = normalizeName(raw)
            val isTox = (m.isToxic == true)

            val acc = map.getOrPut(norm) { Acc() }
            acc.total += 1
            if (isTox) acc.toxic += 1
            acc.labelCounts[raw.trim()] = (acc.labelCounts[raw.trim()] ?: 0) + 1
        }

        val base = map.map { (key, acc) ->
            val label = acc.labelCounts.maxByOrNull { it.value }?.key ?: key
            val rate = if (acc.total > 0) acc.toxic.toDouble() / acc.total else 0.0
            
            SpeakerStatEntity(
                conversationId = conversationId,
                speakerKey = key,
                speakerLabel = label,
                totalCount = acc.total,
                toxicCount = acc.toxic,
                toxicRate = rate
            )
        }

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

    private fun normalizeName(s: String): String = s.trim().lowercase()

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
