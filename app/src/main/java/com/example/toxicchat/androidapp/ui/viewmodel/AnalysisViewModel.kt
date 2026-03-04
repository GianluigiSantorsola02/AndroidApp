package com.example.toxicchat.androidapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import com.example.toxicchat.androidapp.domain.analyzer.ToxicityWorker
import com.example.toxicchat.androidapp.domain.model.*
import com.example.toxicchat.androidapp.domain.repository.AnalysisRepository
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import com.example.toxicchat.androidapp.ui.screens.results.AnalysisResultUiState
import com.example.toxicchat.androidapp.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val repository: AnalysisRepository,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val workManager: WorkManager = WorkManager.getInstance(appContext)
    private val _conversationId = MutableStateFlow<String?>(null)
    private var analysisObserverJob: Job? = null

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AnalysisResultUiState> =
        _conversationId
            .filterNotNull()
            .flatMapLatest { id ->
                repository.getAnalysisResult(id)
                    .map { result -> AnalysisResultUiState.Success(result) as AnalysisResultUiState }
                    .catch { e ->
                        emit(AnalysisResultUiState.Error(e.message ?: "Errore caricamento"))
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AnalysisResultUiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversation: StateFlow<ConversationEntity?> =
        _conversationId
            .filterNotNull()
            .flatMapLatest { id ->
                chatRepository.getConversationFlow(id)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    fun loadResult(id: String) {
        _conversationId.value = id
    }

    fun startAnalysis() {
        analysisObserverJob?.cancel()
        val id = _conversationId.value ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ToxicityWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    ToxicityWorker.KEY_CONVERSATION_ID to (id as Any?),
                    ToxicityWorker.KEY_MODEL_VERSION to (DEFAULT_MODEL_VERSION as Any?)
                )
            )
            .addTag("analysis_$id")
            .build()

        workManager.enqueueUniqueWork("analysis_$id", ExistingWorkPolicy.REPLACE, request)

        analysisObserverJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { workInfo ->
                if (workInfo == null) return@collect
                if (workInfo.state == WorkInfo.State.FAILED || workInfo.state == WorkInfo.State.CANCELLED) {
                    val currentStatus = (uiState.value as? AnalysisResultUiState.Success)?.result?.status
                    if (currentStatus == AnalysisStatus.IN_CORSO) {
                        repository.setStatus(id, AnalysisStatus.ERRORE)
                    }
                }
            }
        }
    }

    fun pauseAnalysis() {
        val id = _conversationId.value ?: return
        viewModelScope.launch { repository.setStatus(id, AnalysisStatus.PAUSA) }
    }

    fun retryAnalysis() {
        startAnalysis()
    }

    fun setRangePreset(preset: AnalysisRangePreset) {
        val id = _conversationId.value ?: return
        viewModelScope.launch {
            val min = repository.getMinTimestamp(id) ?: return@launch
            val max = repository.getMaxTimestamp(id) ?: return@launch
            val (start, end) = RangeUtils.computeRange(preset, min, max)
            repository.clearAggregates(id)
            repository.setAnalysisRange(id, preset, start, end)
            repository.saveMetadata(id, AnalysisMetadata(
                analyzedCount = 0, 
                totalCount = 0, 
                rangeStartMillis = start, 
                rangeEndMillis = end,
                rangePreset = preset
            ))
        }
    }

    fun exportPdfReport(privacyMode: ReportPrivacyMode) {
        val state = uiState.value as? AnalysisResultUiState.Success ?: return
        val conv = conversation.value ?: return
        val result = state.result

        viewModelScope.launch {
            val reportData = mapToReportData(conv, result)
            val generator = PdfReportGenerator()
            val uri = generator.generate(appContext, reportData, privacyMode)
            _exportUri.value = uri
        }
    }

    fun clearExportUri() {
        _exportUri.value = null
    }

    private fun mapToReportData(conv: ConversationEntity, result: AnalysisResult): ReportData {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = result.metadata.rangeStartMillis?.let { sdf.format(Date(it)) } ?: "Inizio"
        val endDate = result.metadata.rangeEndMillis?.let { sdf.format(Date(it)) } ?: "Fine"

        val participantStats = result.speakerStats.map { stat ->
            val realName = when (stat.speakerLabel) {
                "IO" -> conv.selectedSelfName ?: "Io"
                "ALTRO" -> if (!conv.isGroup) conv.title else "Altro"
                else -> stat.speakerLabel
            }
            ParticipantStats(realName, stat.totalCount, stat.toxicCount)
        }

        val weeklyTrend = result.weeklySeries.map { point ->
            val label = try {
                val parts = point.weekId.split("-W")
                if (parts.size == 2) {
                    val year = parts[0].toInt()
                    val week = parts[1].toInt()
                    val date = LocalDate.now()
                        .withYear(year)
                        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
                        .with(java.time.DayOfWeek.MONDAY)
                    date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                } else point.weekId
            } catch (e: Exception) {
                point.weekId
            }
            
            val rate = if (point.toxicRate <= 1.0 && point.toxicMessages > 0) point.toxicRate * 100.0 else point.toxicRate
            WeeklyTrendPoint(label, point.totalMessages, rate.toFloat())
        }

        val dailyMap = mutableMapOf<Int, Pair<Int, Int>>()
        result.heatmap.forEach { cell ->
            val current = dailyMap.getOrDefault(cell.dayOfWeek, Pair(0, 0))
            dailyMap[cell.dayOfWeek] = Pair(current.first + cell.totalCount, current.second + cell.toxicCount)
        }
        val dayNames = listOf("", "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
        val dailyDistribution = (1..7).map { dayIndex ->
            val stats = dailyMap.getOrDefault(dayIndex, Pair(0, 0))
            DayStats(dayNames[dayIndex], stats.first, stats.second)
        }

        val heatmapMatrix = Array(7) { FloatArray(24) }
        result.heatmap.forEach { cell ->
            if (cell.dayOfWeek in 1..7 && cell.hour in 0..23) {
                val rate = if (cell.toxicRate <= 1.0 && cell.toxicCount > 0) cell.toxicRate * 100.0 else cell.toxicRate
                heatmapMatrix[cell.dayOfWeek - 1][cell.hour] = rate.toFloat()
            }
        }

        return ReportData(
            fileName = conv.title,
            startDate = startDate,
            endDate = endDate,
            totalMessages = result.metadata.totalCount,
            toxicMessages = result.speakerStats.sumOf { it.toxicCount },
            participants = participantStats,
            weeklyTrend = weeklyTrend,
            dailyDistribution = dailyDistribution,
            heatmap = heatmapMatrix
        )
    }

    companion object {
        private const val TAG = "AnalysisViewModel"
        private const val DEFAULT_MODEL_VERSION = "REMOTE_STUB_V1"
    }
}
