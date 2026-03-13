package com.example.toxicchat.androidapp.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toxicchat.androidapp.domain.export.PaohvisExporter
import com.example.toxicchat.androidapp.domain.export.PaohvisGranularity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaohvisExportViewModel @Inject constructor(
    private val exporter: PaohvisExporter
) : ViewModel() {

    sealed class ExportState {
        object Loading : ExportState()
        // Aggiornato per usare Uri invece di File per coerenza con il salvataggio in Download
        data class Success(val uri: Uri) : ExportState()
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
                val uri = exporter.export(conversationId, chatTitle, startMs, endMs, granularity)
                if (uri != null) {
                    _exportState.value = ExportState.Success(uri)
                } else {
                    _exportState.value = ExportState.Error("Impossibile creare il file")
                }
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Errore durante l'esportazione")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = null
    }
}
