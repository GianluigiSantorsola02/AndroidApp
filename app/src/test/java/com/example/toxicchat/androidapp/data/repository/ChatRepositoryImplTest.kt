package com.example.toxicchat.androidapp.data.repository

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toxicchat.androidapp.data.local.AppDatabase
import com.example.toxicchat.androidapp.data.local.ChatRepositoryImpl
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.model.Source
import com.example.toxicchat.androidapp.domain.model.Speaker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatRepositoryImplTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepositoryImpl(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `saveImport inserts conversation and messages correctly`() = runTest {
        val conversationId = "test-conv"
        val conversation = ConversationEntity(
            id = conversationId,
            title = "Test Chat",
            createdAt = 123456789L,
            deviceTimezoneId = "UTC",
            dateOrderUsed = "DMY",
            parsedMessagesCount = 1,
            systemMessagesCount = 0,
            multilineAppendsCount = 0,
            skippedLinesCount = 0,
            invalidDatesCount = 0,
            examplesSkippedLines = emptyList()
        )
        val messages = listOf(
            MessageRecord(
                conversationId = conversationId,
                messageId = 1,
                timestampIso8601 = "2024-01-01T10:00:00Z",
                timestampEpochMillis = 1704103200000L,
                speaker = Speaker.OTHER,
                speakerRaw = "User",
                textOriginal = "Hello",
                source = Source.WHATSAPP_TXT,
                isSystem = false,
                toxScore = null,
                isToxic = false
            )
        )

        repository.saveImport(conversation, messages)

        val savedConv = repository.getConversation(conversationId)
        assertEquals("Test Chat", savedConv?.title)

        val conversations = repository.getConversations().first()
        assertEquals(1, conversations.size)
        assertEquals(conversationId, conversations[0].id)
    }
}
