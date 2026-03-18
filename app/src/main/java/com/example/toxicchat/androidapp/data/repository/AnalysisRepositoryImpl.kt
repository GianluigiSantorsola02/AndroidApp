package com.example.toxicchat.androidapp.data.repository

import com.example.toxicchat.androidapp.data.local.AnalysisDao
import com.example.toxicchat.androidapp.data.local.ConversationDao
import com.example.toxicchat.androidapp.data.local.WeeklyPointEntity
import com.example.toxicchat.androidapp.data.local.AnalysisMetadataFields
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import com.example.toxicchat.androidapp.domain.model.*
import com.example.toxicchat.androidapp.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisRepositoryImpl @Inject constructor(
    private val analysisDao: AnalysisDao,
    private val conversationDao: ConversationDao,
) : AnalysisRepository {

    override fun getAnalysisResult(conversationId: String): Flow<AnalysisResult> {
        val metaFlow = analysisDao.getAnalysisMetadataFlow(conversationId)
        val convFlow = conversationDao.getConversationByIdFlow(conversationId)

        // Combine meta and conv first to stay within 5-parameter limit of combine
        val metaAndConvFlow = metaFlow.combine(convFlow) { meta, conv -> 
            meta to conv 
        }

        val weeklyFlow = analysisDao.getWeeklyPointsFlow(conversationId).map { entities ->
            entities.map {
                val (start, end) = parseWeekId(it.weekId)
                WeeklyPoint(
                    weekId = it.weekId, 
                    totalMessages = it.totalMessages, 
                    toxicMessages = it.toxicMessages, 
                    toxicRate = it.toxicRate, 
                    startMillis = start, 
                    endMillisExclusive = end,
                    maxToxScore = it.maxToxScore
                ) 
            }
        }

        val heatmapFlow = analysisDao.getHeatmapCellsFlow(conversationId)
            .map { entities ->
                entities.map { e ->
                    HeatmapCell(e.dayOfWeek, e.hour, e.totalCount, e.toxicCount, e.toxicRate)
                }
            }

        val responseFlow = analysisDao.getResponseStatsFlow(conversationId)
            .map { entity ->
                entity?.let {
                    ResponseTimeStats(
                        medianSelfToOtherMin = it.medianSelfToOtherMin,
                        medianOtherToSelfMin = it.medianOtherToSelfMin,
                        meanSelfToOtherMin = it.meanSelfToOtherMin,
                        meanOtherToSelfMin = it.meanOtherToSelfMin
                    )
                }
            }

        val speakerFlow = analysisDao.getSpeakerStatsFlow(conversationId)
            .map { entities ->
                entities.map {
                    SpeakerToxicityStat(
                        speakerKey = it.speakerKey,
                        speakerLabel = it.speakerLabel,
                        totalCount = it.totalCount,
                        toxicCount = it.toxicCount,
                        toxicRate = it.toxicRate
                    )
                }
            }

        return combine(
            metaAndConvFlow,
            weeklyFlow,
            heatmapFlow,
            responseFlow,
            speakerFlow
        ) { metaAndConv, weekly, heatmap, response, speakerStats ->
            val metaFields = metaAndConv.first
            val convEntity = metaAndConv.second

            if (metaFields == null) {
                AnalysisResult(
                    status = AnalysisStatus.ERRORE,
                    metadata = AnalysisMetadata(),
                    weeklySeries = emptyList(),
                    heatmap = emptyList(),
                    responseStats = null,
                    speakerStats = emptyList()
                )
            } else {
                val metadata = AnalysisMetadata(
                    analyzedCount = metaFields.analysisAnalyzedCount,
                    totalCount = metaFields.analysisTotalCount,
                    rangePreset = metaFields.analysisRangePreset,
                    rangeStartMillis = metaFields.analysisRangeStartMillis,
                    rangeEndMillis = metaFields.analysisRangeEndMillis,
                    lastAnalyzedAtIso = metaFields.analysisLastAnalyzedAtIso,
                    modelVersion = metaFields.analysisModelVersion,
                    isGroup = convEntity?.isGroup ?: false
                )

                AnalysisResult(
                    status = metaFields.analysisStatus,
                    metadata = metadata,
                    weeklySeries = weekly,
                    heatmap = heatmap,
                    responseStats = response,
                    speakerStats = speakerStats
                )
            }
        }
    }

    private fun parseWeekId(weekId: String): Pair<Long, Long> {
        return try {
            val parts = weekId.split("-W")
            val year = parts[0].toInt()
            val week = parts[1].toInt()
            val date = LocalDate.now()
                .withYear(year)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
                .with(java.time.DayOfWeek.MONDAY)
            val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = date.plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            start to end
        } catch (e: Exception) {
            0L to 0L
        }
    }

    override suspend fun setStatus(conversationId: String, status: AnalysisStatus) {
        analysisDao.updateStatus(conversationId, status)
    }

    override suspend fun getMinTimestamp(conversationId: String): Long? {
        return analysisDao.getMinTimestamp(conversationId)
    }

    override suspend fun getMaxTimestamp(conversationId: String): Long? {
        return analysisDao.getMaxTimestamp(conversationId)
    }

    override suspend fun clearAggregates(conversationId: String) {
        analysisDao.replaceAllAggregates(
            conversationId = conversationId,
            weeklyPoints = emptyList(),
            heatmapCells = emptyList(),
            response = null,
            speakerStats = emptyList()
        )
    }

    override suspend fun setAnalysisRange(
        conversationId: String,
        preset: AnalysisRangePreset,
        startMillis: Long,
        endMillis: Long
    ) {
        analysisDao.setAnalysisRange(conversationId, preset, startMillis, endMillis)
    }

    override suspend fun saveMetadata(conversationId: String, metadata: AnalysisMetadata) {
        analysisDao.updateMetadata(
            conversationId = conversationId,
            analyzedCount = metadata.analyzedCount,
            totalCount = metadata.totalCount,
            lastAtIso = metadata.lastAnalyzedAtIso,
            modelVersion = metadata.modelVersion,
            rangeStartMillis = metadata.rangeStartMillis,
            rangeEndMillis = metadata.rangeEndMillis,
            rangePreset = metadata.rangePreset
        )
    }

    override suspend fun saveAggregates(
        conversationId: String,
        weekly: List<WeeklyPoint>,
        heatmap: List<HeatmapCell>,
        response: ResponseTimeStats?,
        speakerStats: List<SpeakerToxicityStat>
    ) {

        val weeklyEntities = weekly.map {
            WeeklyPointEntity(
                conversationId = conversationId,
                weekId = it.weekId,
                totalMessages = it.totalMessages,
                toxicMessages = it.toxicMessages,
                toxicRate = it.toxicRate,
                maxToxScore = it.maxToxScore ?: 0.0
            )
        }

        val heatmapEntities = heatmap.map {
            com.example.toxicchat.androidapp.data.local.HeatmapCellEntity(
                conversationId = conversationId,
                dayOfWeek = it.dayOfWeek,
                hour = it.hour,
                totalCount = it.totalCount,
                toxicCount = it.toxicCount,
                toxicRate = it.toxicRate
            )
        }

        val speakerEntities = speakerStats.map {
            com.example.toxicchat.androidapp.data.local.SpeakerStatEntity(
                conversationId = conversationId,
                speakerKey = it.speakerKey,
                speakerLabel = it.speakerLabel,
                totalCount = it.totalCount,
                toxicCount = it.toxicCount,
                toxicRate = it.toxicRate
            )
        }

        val responseEntity = response?.let {
            com.example.toxicchat.androidapp.data.local.ResponseStatsEntity(
                conversationId = conversationId,
                medianSelfToOtherMin = it.medianSelfToOtherMin,
                medianOtherToSelfMin = it.medianOtherToSelfMin,
                meanSelfToOtherMin = it.meanSelfToOtherMin,
                meanOtherToSelfMin = it.meanOtherToSelfMin
            )
        }

        analysisDao.replaceAllAggregates(
            conversationId = conversationId,
            weeklyPoints = weeklyEntities,
            heatmapCells = heatmapEntities,
            response = responseEntity,
            speakerStats = speakerEntities
        )
    }

    override suspend fun getCurrentRange(conversationId: String): Triple<AnalysisRangePreset, Long?, Long?>? {
        val fields = analysisDao.getRangeFieldsOnce(conversationId) ?: return null
        val preset = fields.preset ?: return null
        return Triple(preset, fields.startMillis, fields.endMillis)
    }

    override suspend fun countTotalMessagesInRange(conversationId: String, startMillis: Long, endMillis: Long): Int {
        return analysisDao.countTotalMessagesInRange(conversationId, startMillis, endMillis)
    }

    override suspend fun countAnalyzedMessagesInRange(conversationId: String, startMillis: Long, endMillis: Long): Int {
        return analysisDao.countAnalyzedMessagesInRange(conversationId, startMillis, endMillis)
    }

    override fun getMessageEventsInRangeFlow(
        conversationId: String,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<MessageEvent>> {
        return analysisDao.getMessagesLiteInRangeFlow(conversationId, startMillis, endMillis)
            .map { rows ->
                rows.map { row ->
                    MessageEvent(
                        id = row.id,
                        timestampEpochMillis = row.timestampEpochMillis,
                        speakerRaw = row.speakerRaw ?: "Sconosciuto",
                        content = row.content ?: "",
                        toxScore = row.toxScore,
                        isToxic = row.isToxic ?: false
                    )
                }
            }
    }

    override suspend fun getMessageEventsInRange(
        conversationId: String,
        startMillis: Long,
        endMillis: Long
    ): List<MessageEvent> {
        return analysisDao.getMessagesLiteInRange(conversationId, startMillis, endMillis)
            .map { row ->
                MessageEvent(
                    id = row.id,
                    timestampEpochMillis = row.timestampEpochMillis,
                    speakerRaw = row.speakerRaw ?: "Sconosciuto",
                    content = row.content ?: "",
                    toxScore = row.toxScore,
                    isToxic = row.isToxic ?: false
                )
            }
    }

    override fun getMessagesByPatternFlow(
        conversationId: String,
        startMillis: Long,
        endMillis: Long,
        dayOfWeek: Int,
        hour: Int
    ): Flow<List<MessageEvent>> {
        val dayOfWeekSql = if (dayOfWeek == 7) 0 else dayOfWeek
        return analysisDao.getMessagesByPatternFlow(conversationId, startMillis, endMillis, dayOfWeekSql, hour)
            .map { rows ->
                rows.map { row ->
                    MessageEvent(
                        id = row.id,
                        timestampEpochMillis = row.timestampEpochMillis,
                        speakerRaw = row.speakerRaw ?: "Sconosciuto",
                        content = row.content ?: "",
                        toxScore = row.toxScore,
                        isToxic = row.isToxic ?: false
                    )
                }
            }
    }
}
