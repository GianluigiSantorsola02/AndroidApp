package com.example.toxicchat.androidapp.data.local

import androidx.paging.PagingSource
import androidx.room.*
import com.example.toxicchat.androidapp.domain.model.AnalysisStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("UPDATE conversations SET analysisStatus = :status WHERE id = :id")
    suspend fun updateAnalysisStatus(id: String, status: AnalysisStatus)

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getConversationByIdFlow(id: String): Flow<ConversationEntity?>
}

@Dao
interface MessageDao {
    // Ordiniamo DESC (più recenti per primi) per supportare reverseLayout in UI
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY messageId DESC")
    fun getMessagesForConversation(conversationId: String): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isToxic = 1 ORDER BY messageId DESC")
    fun getToxicMessagesForConversation(conversationId: String): PagingSource<Int, MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND isToxic = 1")
    fun getToxicCount(conversationId: String): Flow<Int>

    @Query("SELECT DISTINCT speakerRaw FROM messages WHERE conversationId = :conversationId AND speakerRaw IS NOT NULL AND speakerRaw != ''")
    suspend fun getDistinctParticipants(conversationId: String): List<String>
    
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY messageId DESC LIMIT :limit")
    suspend fun getLatestMessages(conversationId: String, limit: Int): List<MessageEntity>
}
