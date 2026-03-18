package com.example.toxicchat.androidapp.domain.analyzer

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toxicchat.androidapp.data.local.*
import com.example.toxicchat.androidapp.data.remote.MODEL_VERSION
import com.example.toxicchat.androidapp.data.remote.ScoreItemDto
import com.example.toxicchat.androidapp.data.remote.ToxicityApi
import com.example.toxicchat.androidapp.data.remote.buildScoreRequest
import com.example.toxicchat.androidapp.domain.model.AnalysisRangePreset
import com.example.toxicchat.androidapp.domain.model.AnalysisStatus
import com.example.toxicchat.androidapp.domain.model.MessageLite
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.time.Instant

@HiltWorker
class ToxicityWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val analysisDao: AnalysisDao,
    private val conversationDao: ConversationDao,
    private val toxicityApi: ToxicityApi
) : CoroutineWorker(appContext, workerParams) {

    private var lastMetadataUpdate = 0L
    private var lastPauseCheck = 0L

    override suspend fun doWork(): Result {
        val convId = inputData.getString(KEY_CONVERSATION_ID) ?: return Result.failure()

        val conv = conversationDao.getConversationById(convId) ?: return Result.failure()

        var start = conv.analysisRangeStartMillis
        var end = conv.analysisRangeEndMillis
        var preset = conv.analysisRangePreset
        
        if (start == null || end == null || preset == null) {
            val min = analysisDao.getMinTimestamp(convId) ?: return Result.failure()
            val max = analysisDao.getMaxTimestamp(convId) ?: return Result.failure()
            start = min
            end = max
            preset = AnalysisRangePreset.ALL_TIME
            analysisDao.setAnalysisRange(convId, preset, start, end)
        }

        val totalInRange = analysisDao.countTotalMessagesInRange(convId, start, end)
        val alreadyAnalyzed = analysisDao.countAnalyzedMessagesInRange(convId, start, end)

        var analyzedSoFar = alreadyAnalyzed
        var lastModelVersion: String = MODEL_VERSION

        try {
            analysisDao.updateStatus(convId, AnalysisStatus.IN_CORSO)

            updateMetadataThrottled(convId, analyzedSoFar, totalInRange, lastModelVersion, start, end, preset, force = true)

            while (true) {
                val now = System.currentTimeMillis()
                if (now - lastPauseCheck >= PAUSE_CHECK_MIN_INTERVAL_MS) {
                    lastPauseCheck = now
                    val current = conversationDao.getConversationById(convId) ?: break
                    if (current.analysisStatus == AnalysisStatus.PAUSA) return Result.success()
                }

                if (isStopped) return Result.failure()

                val batch = analysisDao.getUnanalyzedChunkInRange(
                    id = convId,
                    startMillis = start,
                    endMillis = end,
                    limit = NET_BATCH_SIZE
                )
                if (batch.isEmpty()) break

                val (skipped, toAnalyze) = batch.partition { msg ->
                    msg.textOriginal.isBlank() || isOmittedMedia(msg.textOriginal)
                }

                val updates = mutableListOf<AnalysisDao.ToxicityUpdate>()

                skipped.forEach { msg ->
                    updates.add(AnalysisDao.ToxicityUpdate(msg.id, 0.0, false, "LOCAL_SKIPPED"))
                }

                if (toAnalyze.isNotEmpty()) {
                    val req = buildScoreRequest(
                        conversationId = convId,
                        items = toAnalyze.map { ScoreItemDto(it.id, it.textOriginal) }
                    )

                    val resp = toxicityApi.score(req)
                    lastModelVersion = resp.modelVersion
                    val scoreById = resp.results.associate { it.messageLocalId to it.score }

                    toAnalyze.forEach { msg ->
                        scoreById[msg.id]?.let { score ->
                            updates.add(AnalysisDao.ToxicityUpdate(msg.id, score, score >= TOX_THRESHOLD, lastModelVersion))
                        }
                    }
                }

                if (updates.isNotEmpty()) {
                    analysisDao.updateToxicityBatch(updates)
                    analyzedSoFar += updates.size
                    updateMetadataThrottled(convId, analyzedSoFar, totalInRange, lastModelVersion, start, end, preset)
                }
            }

            // --- AGGREGATION PHASE ---
            val lite = analysisDao.getMessagesLiteInRange(convId, start, end).map {
                MessageLite(it.id, it.timestampEpochMillis, it.speakerRaw, it.toxScore, it.isToxic)
            }

            if (lite.isNotEmpty()) {
                // Calcolo dinamico isGroup basato sui partecipanti reali della conversazione intera
                // (Non solo dell'intervallo, per coerenza con la natura della chat)
                val distinctSpeakersCount = analysisDao.countDistinctSpeakers(convId)
                val isGroup = distinctSpeakersCount > 2

                // Aggiorniamo il flag nel database se diverso (persistenza metadato)
                if (conv.isGroup != isGroup) {
                    conversationDao.updateConversation(conv.copy(isGroup = isGroup))
                }

                val weekly = StatisticsEngine.computeWeeklySeries(convId, lite)
                val heatmap = StatisticsEngine.computeHeatmap(convId, lite)
                val response = StatisticsEngine.computeResponseStats(convId, lite, isGroup)
                val speakerStats = StatisticsEngine.computeSpeakerStats(convId, lite, isGroup)

                val weeklyEntities = weekly.map {
                    WeeklyPointEntity(
                        conversationId = convId,
                        weekId = it.weekId,
                        totalMessages = it.totalMessages,
                        toxicMessages = it.toxicMessages,
                        toxicRate = it.toxicRate,
                        maxToxScore = it.maxToxScore
                    )
                }

                val heatmapEntities = heatmap.map {
                    HeatmapCellEntity(
                        conversationId = convId,
                        dayOfWeek = it.dayOfWeek,
                        hour = it.hour,
                        totalCount = it.totalCount,
                        toxicCount = it.toxicCount,
                        toxicRate = it.toxicRate
                    )
                }

                val speakerEntities = speakerStats.map {
                    SpeakerStatEntity(
                        conversationId = convId,
                        speakerKey = it.speakerKey,
                        speakerLabel = it.speakerLabel,
                        totalCount = it.totalCount,
                        toxicCount = it.toxicCount,
                        toxicRate = it.toxicRate
                    )
                }

                val responseEntity = response?.let {
                    ResponseStatsEntity(
                        conversationId = convId,
                        medianSelfToOtherMin = it.medianSelfToOtherMin,
                        medianOtherToSelfMin = it.medianOtherToSelfMin,
                        meanSelfToOtherMin = it.meanSelfToOtherMin,
                        meanOtherToSelfMin = it.meanOtherToSelfMin
                    )
                }

                analysisDao.replaceAllAggregates(
                    conversationId = convId,
                    weeklyPoints = weeklyEntities,
                    heatmapCells = heatmapEntities,
                    response = responseEntity,
                    speakerStats = speakerEntities
                )
            }

            updateMetadataThrottled(convId, analyzedSoFar, totalInRange, lastModelVersion, start, end, preset, force = true)
            analysisDao.updateStatus(convId, AnalysisStatus.ANALIZZATA)
            return Result.success()

        } catch (e: IOException) {
            analysisDao.updateStatus(convId, AnalysisStatus.ERRORE)
            return Result.retry()
        } catch (e: Exception) {
            analysisDao.updateStatus(convId, AnalysisStatus.ERRORE)
            return Result.failure()
        }
    }

    private suspend fun updateMetadataThrottled(
        conversationId: String, analyzedCount: Int, totalCount: Int, modelVersion: String,
        start: Long, end: Long, preset: AnalysisRangePreset, force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (force || now - lastMetadataUpdate >= META_UPDATE_MIN_INTERVAL_MS) {
            lastMetadataUpdate = now
            analysisDao.updateMetadata(
                conversationId, analyzedCount, totalCount, Instant.now().toString(),
                modelVersion, start, end, preset
            )
        }
    }

    private fun isOmittedMedia(text: String): Boolean {
        val t = text.trim().lowercase()
        val exactMatches = setOf(
            "audio omesso", "video omesso", "foto omessa", "sticker omesso", "documento omesso",
            "audio omitted", "video omitted", "image omitted", "sticker omitted", "document omitted",
            "messaggio eliminato", "message deleted", "chiamata persa", "missed call"
        )
        if (exactMatches.contains(t)) return true
        return t.startsWith("<") && t.endsWith(">") && (t.contains("omess") || t.contains("omitted"))
    }

    companion object {
        const val KEY_CONVERSATION_ID = "CONVERSATION_ID"
        const val KEY_MODEL_VERSION = "MODEL_VERSION"
        const val NET_BATCH_SIZE = 16
        const val TOX_THRESHOLD = 0.5
        const val META_UPDATE_MIN_INTERVAL_MS = 1000L
        const val PAUSE_CHECK_MIN_INTERVAL_MS = 800L
    }
}
