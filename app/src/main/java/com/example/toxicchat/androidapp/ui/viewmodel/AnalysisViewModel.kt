package com.example.toxicchat.androidapp.ui.viewmodel

import android.content.Context
import android.net.Uri
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    private val _selectedWeek = MutableStateFlow<WeeklyPoint?>(null)
    val selectedWeek: StateFlow<WeeklyPoint?> = _selectedWeek.asStateFlow()

    private val _selectedDayStartMillis = MutableStateFlow<Long?>(null)
    val selectedDayStartMillis: StateFlow<Long?> = _selectedDayStartMillis.asStateFlow()

    private val _heatmapDetailFilter = MutableStateFlow<HeatmapCell?>(null)
    val heatmapDetailFilter: StateFlow<HeatmapCell?> = _heatmapDetailFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<AnalysisResultUiState> =
        _conversationId
            .filterNotNull()
            .flatMapLatest { id ->
                repository.getAnalysisResult(id)
                    .map { result -> AnalysisResultUiState.Success(result) as AnalysisResultUiState }
                    .onEach { state ->
                        if (state is AnalysisResultUiState.Success && _selectedWeek.value == null) {
                            state.result.weeklySeries.lastOrNull()?.let { lastWeek ->
                                _selectedWeek.value = lastWeek
                            }
                        }
                    }
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
    val selectedWeekEvents: StateFlow<List<MessageEvent>> =
        combine(_conversationId.filterNotNull(), _selectedWeek.filterNotNull()) { id, week ->
            id to week
        }.flatMapLatest { (id, week) ->
            repository.getMessageEventsInRangeFlow(id, week.startMillis, week.endMillisExclusive)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val selectedWeekDayStats: StateFlow<List<DayStat>> =
        selectedWeekEvents.map { events ->
            events.groupBy { event ->
                val date = Instant.ofEpochMilli(event.timestampEpochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.map { (dayStart, dayEvents) ->
                DayStat(
                    dayStartMillis = dayStart,
                    totalMessages = dayEvents.size,
                    toxicMessages = dayEvents.count { it.isToxic },
                    peakToxicity = dayEvents.maxOfOrNull { it.toxScore ?: 0.0 } ?: 0.0
                )
            }.sortedBy { it.dayStartMillis }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val heatmapDetailMessages: StateFlow<List<MessageEvent>> =
        combine(_conversationId.filterNotNull(), _heatmapDetailFilter.filterNotNull()) { id, filter ->
            id to filter
        }.flatMapLatest { (id, filter) ->
            val result = (uiState.value as? AnalysisResultUiState.Success)?.result
            val start = result?.metadata?.rangeStartMillis ?: 0L
            val end = result?.metadata?.rangeEndMillis ?: System.currentTimeMillis()
            repository.getMessagesByPatternFlow(id, start, end, filter.dayOfWeek, filter.hour)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val allMessagesInRange: StateFlow<List<MessageEvent>> =
        _conversationId.filterNotNull().flatMapLatest { id ->
            uiState.flatMapLatest { state ->
                if (state is AnalysisResultUiState.Success) {
                    val meta = state.result.metadata
                    repository.getMessageEventsInRangeFlow(
                        id,
                        meta.rangeStartMillis ?: 0L,
                        meta.rangeEndMillis ?: System.currentTimeMillis()
                    )
                } else {
                    flowOf(emptyList())
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
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
        _selectedWeek.value = null
        _selectedDayStartMillis.value = null
        _heatmapDetailFilter.value = null
    }

    fun selectWeek(week: WeeklyPoint) {
        _selectedWeek.value = week
        _selectedDayStartMillis.value = null
    }

    fun selectDay(millis: Long?) {
        _selectedDayStartMillis.value = millis
    }

    fun setHeatmapFilter(cell: HeatmapCell?) {
        _heatmapDetailFilter.value = cell
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
            val messages = repository.getMessageEventsInRange(
                conv.id,
                result.metadata.rangeStartMillis ?: 0L,
                result.metadata.rangeEndMillis ?: System.currentTimeMillis()
            )
            val reportData = mapToReportData(conv, result, messages)
            val generator = PdfReportGenerator()
            val uri = generator.generate(appContext, reportData, privacyMode)
            _exportUri.value = uri
        }
    }

    fun clearExportUri() {
        _exportUri.value = null
    }

    private fun mapToReportData(conv: ConversationEntity, result: AnalysisResult, messages: List<MessageEvent>): ReportData {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val startDate = result.metadata.rangeStartMillis?.let { sdf.format(Date(it)) } ?: "Inizio"
        val endDate = result.metadata.rangeEndMillis?.let { sdf.format(Date(it)) } ?: "Fine"

        val maxToxPerParticipant = messages.groupBy { it.speakerRaw }
            .mapValues { (_, msgs) -> msgs.maxOfOrNull { it.toxScore ?: 0.0 }?.toFloat() ?: 0f }

        val participantStats = result.speakerStats.map { stat ->
            ParticipantStats(
                name = stat.speakerLabel,
                totalMessages = stat.totalCount,
                toxicMessages = stat.toxicCount,
                maxTox = maxToxPerParticipant[stat.speakerLabel] ?: 0f
            )
        }

        // --- AGGREGAZIONE TEMPORALE (Settimanale o Mensile) ---
        val isMonthly = result.weeklySeries.size > 20
        val trendPoints: List<WeeklyTrendPoint>

        if (!isMonthly) {
            val sdfShort = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
            trendPoints = result.weeklySeries.map { point ->
                val label = try {
                    val s = sdfShort.format(Date(point.startMillis))
                    val e = sdfShort.format(Date(point.endMillisExclusive - 1000))
                    "$s - $e"
                } catch (e: Exception) { point.weekId }
                
                val rate = if (point.toxicRate <= 1.0 && point.toxicMessages > 0) point.toxicRate * 100.0 else point.toxicRate
                WeeklyTrendPoint(label, point.totalMessages, rate.toFloat(), point.toxicMessages)
            }
        } else {
            // Aggregazione mensile
            val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ITALY)
            trendPoints = result.weeklySeries.groupBy {
                val dt = Instant.ofEpochMilli(it.startMillis).atZone(ZoneId.systemDefault())
                "${dt.year}-${dt.monthValue}"
            }.map { (_, weeks) ->
                val start = weeks.minOf { it.startMillis }
                val label = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).format(monthFormatter)
                    .replaceFirstChar { it.titlecase(Locale.ITALY) }
                
                val total = weeks.sumOf { it.totalMessages }
                val toxic = weeks.sumOf { it.toxicMessages }
                val rate = if (total > 0) (toxic.toDouble() / total) * 100.0 else 0.0
                
                WeeklyTrendPoint(label, total, rate.toFloat(), toxic)
            }.sortedBy { 
                // Parsing temporaneo per ordinamento corretto
                val parts = it.weekLabel.split(" ")
                if (parts.size == 2) {
                    val monthName = parts[0].lowercase()
                    val year = parts[1].toInt()
                    val month = when(monthName) {
                        "gen" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "mag" -> 5; "giu" -> 6
                        "lug" -> 7; "ago" -> 8; "set" -> 9; "ott" -> 10; "nov" -> 11; "dic" -> 12
                        else -> 1
                    }
                    year * 100 + month
                } else 0
            }
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

        // --- Insight basati su aggregazione ---
        val weekWithMostCritical = trendPoints.maxByOrNull { it.toxicCount }?.weekLabel ?: "N/D"
        
        val maxCell = result.heatmap.maxByOrNull { it.toxicCount }
        val peakCriticityDayTime = maxCell?.let { 
            "${dayNames.getOrNull(it.dayOfWeek) ?: ""}, ore ${it.hour}:00"
        } ?: "N/D"

        val weekWithMostMessages = trendPoints.maxByOrNull { it.totalMessages }?.weekLabel ?: "N/D"
        val weekWithHighestToxicRate = trendPoints.maxByOrNull { it.toxicPercentage }?.weekLabel ?: "N/D"

        val mostCriticalDay = dailyDistribution.maxByOrNull { it.toxicMessages }?.dayName ?: "N/D"
        
        val hourlyToxic = IntArray(24)
        result.heatmap.forEach { if (it.hour in 0..23) hourlyToxic[it.hour] += it.toxicCount }
        val mostCriticalHour = hourlyToxic.indices.maxByOrNull { hourlyToxic[it] }?.let { "$it:00" } ?: "N/D"

        val maxToxicityScore = messages.maxOfOrNull { it.toxScore ?: 0.0 }?.toFloat() ?: 0f
        
        val topCriticalWeeks = trendPoints.sortedByDescending { it.toxicCount }

        return ReportData(
            fileName = conv.title,
            startDate = startDate,
            endDate = endDate,
            totalMessages = result.metadata.totalCount,
            toxicMessages = result.speakerStats.sumOf { it.toxicCount },
            participants = participantStats,
            weeklyTrend = trendPoints,
            dailyDistribution = dailyDistribution,
            heatmap = heatmapMatrix,
            weekWithMostCritical = weekWithMostCritical,
            peakCriticityDayTime = peakCriticityDayTime,
            weekWithMostMessages = weekWithMostMessages,
            weekWithHighestToxicRate = weekWithHighestToxicRate,
            mostCriticalDay = mostCriticalDay,
            mostCriticalHour = mostCriticalHour,
            maxToxicityScore = maxToxicityScore,
            responseStats = result.responseStats,
            topCriticalWeeks = topCriticalWeeks,
            isMonthly = isMonthly
        )
    }

    companion object {
        private const val DEFAULT_MODEL_VERSION = "REMOTE_STUB_V1"
    }
}
