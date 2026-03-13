package com.example.toxicchat.androidapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toxicchat.androidapp.data.importer.ReadResult
import com.example.toxicchat.androidapp.data.importer.WhatsAppExportReader
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import com.example.toxicchat.androidapp.data.parser.WhatsAppTxtParser
import com.example.toxicchat.androidapp.domain.model.DateOrderUsed
import com.example.toxicchat.androidapp.domain.model.ImportMetadata
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class ImportState {
    object Idle : ImportState()
    data class NeedsDateOrderChoice(val uri: Uri, val fileName: String) : ImportState()
    object Importing : ImportState()
    data class Imported(val conversationId: String, val metadata: ImportMetadata) : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChatRepository,
    private val parser: WhatsAppTxtParser
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportState>(ImportState.Idle)
    val uiState: StateFlow<ImportState> = _uiState.asStateFlow()

    fun startImport(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = ImportState.Importing
            
            val readResult = WhatsAppExportReader.readLinesFromExport(context, uri)
            
            when (readResult) {
                is ReadResult.Error -> {
                    _uiState.value = ImportState.Error(readResult.message)
                }
                is ReadResult.Success -> {
                    val lines = readResult.lines
                    if (lines.isEmpty()) {
                        _uiState.value = ImportState.Error("Il file di chat è vuoto.")
                        return@launch
                    }

                    val detected = parser.detectDateOrder(lines.asSequence())
                    if (detected == null) {
                        _uiState.value = ImportState.NeedsDateOrderChoice(uri, fileName)
                    } else {
                        performFullImport(lines, fileName, detected)
                    }
                }
            }
        }
    }

    fun onDateOrderSelected(uri: Uri, fileName: String, choice: DateOrderUsed) {
        viewModelScope.launch {
            _uiState.value = ImportState.Importing
            val readResult = WhatsAppExportReader.readLinesFromExport(context, uri)
            
            if (readResult is ReadResult.Success) {
                performFullImport(readResult.lines, fileName, choice)
            } else if (readResult is ReadResult.Error) {
                _uiState.value = ImportState.Error(readResult.message)
            }
        }
    }

    private suspend fun performFullImport(lines: List<String>, fileName: String, dateOrder: DateOrderUsed) {
        val conversationId = UUID.randomUUID().toString()

        var meta: ImportMetadata? = null
        val messages: List<MessageRecord> = parser
            .parseStreaming(
                lines = lines.asSequence(),
                dateOrder = dateOrder,
                conversationId = conversationId,
                onMetadataComplete = { meta = it }
            )
            .toList()

        val metadata = meta
        if (metadata == null) {
            _uiState.value = ImportState.Error("Import fallito: metadata mancante")
            return
        }

        if (messages.isEmpty()) {
            _uiState.value = ImportState.Error("Nessun messaggio parsabile trovato.")
            return
        }

        val conversation = ConversationEntity(
            id = conversationId,
            title = fileName,
            createdAt = System.currentTimeMillis(),
            deviceTimezoneId = metadata.deviceTimezoneId,
            dateOrderUsed = metadata.dateOrderUsed,
            parsedMessagesCount = metadata.parsedMessagesCount,
            systemMessagesCount = metadata.systemMessagesCount,
            multilineAppendsCount = metadata.multilineAppendsCount,
            skippedLinesCount = metadata.skippedLinesCount,
            invalidDatesCount = metadata.invalidDatesCount,
            examplesSkippedLines = metadata.examplesSkippedLines
        )

        repository.insertConversation(conversation)
        repository.insertMessagesBatch(messages)

        _uiState.value = ImportState.Imported(conversationId, metadata)
    }

    fun reset() {
        _uiState.value = ImportState.Idle
    }
}
