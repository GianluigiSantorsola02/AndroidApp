package com.example.toxicchat.androidapp.data.local

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.model.Source
import com.example.toxicchat.androidapp.domain.model.toEntity
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : ChatRepository {
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    override fun getConversations(): Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    override fun getMessages(conversationId: String, onlyToxic: Boolean): Flow<PagingData<MessageRecord>> {
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = {
                if (onlyToxic) {
                    messageDao.getToxicMessagesForConversation(conversationId)
                } else {
                    messageDao.getMessagesForConversation(conversationId)
                }
            }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    override suspend fun insertConversation(conversation: ConversationEntity) {
        conversationDao.insertConversation(conversation)
    }

    override suspend fun updateConversation(conversation: ConversationEntity) {
        conversationDao.updateConversation(conversation)
    }

    override suspend fun insertMessagesBatch(messages: List<MessageRecord>) {
        val entities = messages.map { it.toEntity() }
        messageDao.insertMessages(entities)
    }

    override suspend fun getConversation(id: String): ConversationEntity? = conversationDao.getConversationById(id)

    override fun getConversationFlow(id: String): Flow<ConversationEntity?> = conversationDao.getConversationByIdFlow(id)

    override fun getToxicCount(conversationId: String): Flow<Int> = messageDao.getToxicCount(conversationId)

    override suspend fun getDistinctParticipants(conversationId: String): List<String> {
        return messageDao.getDistinctParticipants(conversationId)
    }

    override suspend fun getLatestMessages(conversationId: String, limit: Int): List<MessageRecord> {
        return messageDao.getLatestMessages(conversationId, limit).map { it.toDomain() }
    }

    // Deprecato
    override suspend fun saveConversation(conversation: ConversationEntity) {
        conversationDao.insertConversation(conversation)
    }

    override suspend fun saveImport(conversation: ConversationEntity, messages: List<MessageRecord>) {
        db.withTransaction {
            conversationDao.insertConversation(conversation)
            val entities = messages.map { it.toEntity() }
            messageDao.insertMessages(entities)
        }
    }

    private fun MessageEntity.toDomain() = MessageRecord(
        conversationId = conversationId,
        messageId = messageId,
        timestampIso8601 = timestampIso8601,
        timestampEpochMillis = timestampEpochMillis,
        speaker = speaker,
        speakerRaw = speakerRaw,
        textOriginal = textOriginal,
        source = Source.WHATSAPP_TXT,
        isSystem = isSystem,
        isToxic = isToxic,
        toxScore = toxScore
    )
}
