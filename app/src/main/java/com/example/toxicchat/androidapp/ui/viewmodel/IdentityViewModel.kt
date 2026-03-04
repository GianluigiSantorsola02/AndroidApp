package com.example.toxicchat.androidapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentityUiState(
    val conversation: ConversationEntity? = null,
    val participants: List<String> = emptyList(),
    val selectedSelfName: String? = null,
    val extraAliases: Set<String> = emptySet(),
    val previewMessages: List<MessageRecord> = emptyList(),
    val showAliasOption: Boolean = false,
    val isSaved: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdentityUiState())
    val uiState: StateFlow<IdentityUiState> = _uiState.asStateFlow()

    fun loadData(conversationId: String, suggestedName: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            delay(300)

            val conversation = repository.getConversation(conversationId)
            
            var participants = repository.getDistinctParticipants(conversationId)
            
            if (participants.isEmpty()) {
                delay(1000)
                participants = repository.getDistinctParticipants(conversationId)
            }

            val latestMessages = repository.getLatestMessages(conversationId, 10)

            val initialSelection = suggestedName?.let { name ->
                participants.find { it.equals(name, ignoreCase = true) }
            } ?: conversation?.selectedSelfName

            _uiState.value = _uiState.value.copy(
                conversation = conversation,
                participants = participants,
                selectedSelfName = initialSelection,
                previewMessages = latestMessages,
                isLoading = false
            )
            
            initialSelection?.let { checkIfAliasNeeded(it, latestMessages) }
        }
    }

    fun selectParticipant(name: String) {
        _uiState.value = _uiState.value.copy(selectedSelfName = name)
        checkIfAliasNeeded(name, _uiState.value.previewMessages)
    }

    private fun checkIfAliasNeeded(selectedName: String, messages: List<MessageRecord>) {
        val normalizedSelected = selectedName.trim().lowercase()
        val selfIndicators = setOf("you", "io", "tu", "me")
        
        val needsAlias = messages.any { msg ->
            if (msg.isSystem || msg.speakerRaw == null) return@any false
            val speaker = msg.speakerRaw.trim().lowercase()
            speaker != normalizedSelected && (selfIndicators.contains(speaker) || speaker.contains("you"))
        }
        
        _uiState.value = _uiState.value.copy(showAliasOption = needsAlias)
    }

    fun saveIdentity() {
        val state = _uiState.value
        val conv = state.conversation ?: return
        val selfName = state.selectedSelfName ?: return
        
        val isGroup = state.participants.filter { it != selfName }.size > 1

        viewModelScope.launch {
            repository.updateConversation(conv.copy(
                selectedSelfName = selfName,
                selfAliases = state.extraAliases.toList(),
                isGroup = isGroup
            ))
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
