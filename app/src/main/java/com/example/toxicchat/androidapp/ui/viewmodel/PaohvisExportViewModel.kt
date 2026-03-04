package com.example.toxicchat.androidapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toxicchat.androidapp.domain.export.PaohvisExporter
import com.example.toxicchat.androidapp.domain.export.PaohvisGranularity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PaohvisExportViewModel @Inject constructor(
    private val exporter: PaohvisExporter
) : ViewModel() {

    sealed class ExportState {
        object Loading : ExportState()
        data class Success(val file: File) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    private val _exportState = MutableStateFlow<ExportState?>(null)
    val exportState: StateFlow<ExportState?> = _exportState

    fun startExport(
        conversationId: String,
        chatTitle: String,
        startMs: Long,
        endMs: Long,
        granularity: PaohvisGranularity
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val file = exporter.export(conversationId, chatTitle, startMs, endMs, granularity)
                _exportState.value = ExportState.Success(file)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Errore durante l'esportazione")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = null
    }
}
