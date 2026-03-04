package com.example.toxicchat.androidapp.domain.repository

import androidx.paging.PagingData
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getConversations(): Flow<List<ConversationEntity>>
    fun getMessages(conversationId: String, onlyToxic: Boolean): Flow<PagingData<MessageRecord>>
    suspend fun insertConversation(conversation: ConversationEntity)
    suspend fun updateConversation(conversation: ConversationEntity)
    suspend fun insertMessagesBatch(messages: List<MessageRecord>)
    suspend fun getConversation(id: String): ConversationEntity?
    fun getConversationFlow(id: String): Flow<ConversationEntity?>
    fun getToxicCount(conversationId: String): Flow<Int>
    suspend fun getDistinctParticipants(conversationId: String): List<String>
    suspend fun getLatestMessages(conversationId: String, limit: Int): List<MessageRecord>

    // Deprecato
    suspend fun saveConversation(conversation: ConversationEntity)
    suspend fun saveImport(conversation: ConversationEntity, messages: List<MessageRecord>)
}
