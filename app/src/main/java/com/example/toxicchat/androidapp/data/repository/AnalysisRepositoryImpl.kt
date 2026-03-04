package com.example.toxicchat.androidapp.data.repository

import android.util.Log
import com.example.toxicchat.androidapp.data.local.AnalysisDao
import com.example.toxicchat.androidapp.data.local.ConversationDao
import com.example.toxicchat.androidapp.domain.model.AnalysisMetadata
import com.example.toxicchat.androidapp.domain.model.AnalysisRangePreset
import com.example.toxicchat.androidapp.domain.model.AnalysisResult
import com.example.toxicchat.androidapp.domain.model.AnalysisStatus
import com.example.toxicchat.androidapp.domain.model.HeatmapCell
import com.example.toxicchat.androidapp.domain.model.ResponseTimeStats
import com.example.toxicchat.androidapp.domain.model.SpeakerToxicityStat
import com.example.toxicchat.androidapp.domain.model.WeeklyPoint
import com.example.toxicchat.androidapp.domain.repository.AnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalysisRepositoryImpl @Inject constructor(
    private val analysisDao: AnalysisDao,
    private val conversationDao: ConversationDao,
) : AnalysisRepository {

    override fun getAnalysisResult(conversationId: String): Flow<AnalysisResult> {
        val metaFlow = analysisDao.getAnalysisMetadataFlow(conversationId)

        val weeklyFlow = analysisDao.getWeeklyPointsFlow(conversationId).map { entities ->
            entities.map { WeeklyPoint(it.weekId, it.totalMessages, it.toxicMessages, it.toxicRate) }
        }

        val heatmapFlow = analysisDao.getHeatmapCellsFlow(conversationId)
            .map { entities ->
                entities.map { e ->
                    HeatmapCell(
                        e.dayOfWeek,
                        e.hour,
                        e.totalCount,
                        e.toxicCount,
                        e.toxicRate
                    )
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
                        speakerLabel = it.speakerLabel,
                        totalCount = it.totalCount,
                        toxicCount = it.toxicCount,
                        toxicRate = it.toxicRate
                    )
                }
            }

        return combine(
            metaFlow,
            weeklyFlow,
            heatmapFlow,
            responseFlow,
            speakerFlow
        ) { metaFields, weekly, heatmap, response, speakerStats ->

            if (metaFields == null) {
                return@combine AnalysisResult(
                    status = AnalysisStatus.ERRORE,
                    metadata = AnalysisMetadata(
                        analyzedCount = 0,
                        totalCount = 0,
                        rangePreset = null,
                        rangeStartMillis = null,
                        rangeEndMillis = null,
                        lastAnalyzedAtIso = null,
                        modelVersion = null
                    ),
                    weeklySeries = emptyList(),
                    heatmap = emptyList(),
                    responseStats = null,
                    speakerStats = emptyList()
                )
            }

            val metadata = AnalysisMetadata(
                analyzedCount = metaFields.analysisAnalyzedCount,
                totalCount = metaFields.analysisTotalCount,
                rangePreset = metaFields.analysisRangePreset,
                rangeStartMillis = metaFields.analysisRangeStartMillis,
                rangeEndMillis = metaFields.analysisRangeEndMillis,
                lastAnalyzedAtIso = metaFields.analysisLastAnalyzedAtIso,
                modelVersion = metaFields.analysisModelVersion
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
        response: ResponseTimeStats?
    ) {
        val weeklyEntities = weekly.map {
            com.example.toxicchat.androidapp.data.local.WeeklyPointEntity(
                conversationId = conversationId,
                weekId = it.weekId,
                totalMessages = it.totalMessages,
                toxicMessages = it.toxicMessages,
                toxicRate = it.toxicRate
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
            speakerStats = emptyList()
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
}
