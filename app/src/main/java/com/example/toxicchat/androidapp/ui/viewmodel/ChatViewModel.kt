package com.example.toxicchat.androidapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class ChatUiItem {
    data class MessageItem(val message: MessageRecord) : ChatUiItem()
    data class DateSeparatorItem(val date: LocalDate) : ChatUiItem() {
        val key: String = "date-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}"
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val conversationIdFlow = MutableStateFlow<String?>(null)
    
    private val _showOnlyToxic = MutableStateFlow(false)
    val showOnlyToxic: StateFlow<Boolean> = _showOnlyToxic.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversation: StateFlow<ConversationEntity?> = conversationIdFlow
        .filterNotNull()
        .flatMapLatest { id ->
            repository.getConversationFlow(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<ChatUiItem>> =
        combine(
            conversationIdFlow.filterNotNull().distinctUntilChanged(),
            _showOnlyToxic
        ) { id, onlyToxic ->
            id to onlyToxic
        }
        .flatMapLatest { (id, onlyToxic) ->
            repository.getMessages(conversationId = id, onlyToxic = onlyToxic)
        }
        .map { pagingData ->
            pagingData
                .map { ChatUiItem.MessageItem(it) }
                .insertSeparators { before, after ->
                    val beforeDate = before?.let {
                        Instant.ofEpochMilli(it.message.timestampEpochMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    val afterDate = after?.let {
                        Instant.ofEpochMilli(it.message.timestampEpochMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }

                    if (beforeDate != null && (afterDate == null || beforeDate != afterDate)) {
                        ChatUiItem.DateSeparatorItem(beforeDate)
                    } else {
                        null
                    }
                }
        }
        .cachedIn(viewModelScope)

    fun loadConversation(id: String) {
        if (conversationIdFlow.value == id) return
        conversationIdFlow.value = id
    }
    
    fun toggleToxicFilter(onlyToxic: Boolean) {
        _showOnlyToxic.value = onlyToxic
    }
}
