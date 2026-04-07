package com.example.toxicchat.androidapp.ui

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.paging.PagingData
import com.example.toxicchat.androidapp.MainActivity
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import com.example.toxicchat.androidapp.data.parser.WhatsAppTxtParser
import com.example.toxicchat.androidapp.domain.model.DateOrderUsed
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import com.example.toxicchat.androidapp.ui.viewmodel.ImportState
import com.example.toxicchat.androidapp.ui.viewmodel.ImportViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImportFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun ambiguous_then_select_dmy_then_imported_state_and_saved() {
        val fakeRepo = FakeChatRepository()
        val parser = WhatsAppTxtParser()
        val vm = ImportViewModel(
            composeRule.activity, fakeRepo, parser,
            sharedImportManager = TODO()
        )

        val fileName = "WA_TEST_01_1to1_brackets_24h_seconds_missingdash.txt"
        val lines = listOf(
            "[08/11/25, 12:51:24] Mamma: Ciao come stai",
            "[08/11/25, 12:51:40] Io: Bene, tu?",
            "[08/11/25, 12:52:01] Mamma: Tutto ok.",
            "[08/11/25, 12:52:10] Mamma: Ti scrivo una cosa lunga",
            "che continua su una seconda riga",
            "e anche su una terza.",
            "[08/11/25, 12:53:00] I messaggi e le chiamate sono crittografati end-to-end.",
            "[08/11/25, 12:53:12] Io: Perfetto",
            "[08/11/25, 12:53] Mamma: Questo ha solo hh:mm (senza secondi)"
        )

        val tmp = File(composeRule.activity.cacheDir, "wa_test_import.txt")
        tmp.writeText(lines.joinToString("\n"))
        val uri = Uri.fromFile(tmp)

        composeRule.runOnIdle {
            vm.startImport(uri = uri, fileName = fileName)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            vm.uiState.value is ImportState.NeedsDateOrderChoice
        }

        composeRule.runOnIdle {
            vm.onDateOrderSelected(uri = uri, fileName = fileName, choice = DateOrderUsed.DMY)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            vm.uiState.value is ImportState.Imported
        }

        val state = vm.uiState.value as ImportState.Imported
        assertNotNull(fakeRepo.savedConversation)
        assertEquals(state.conversationId, fakeRepo.savedConversation!!.id)
        assertTrue(fakeRepo.savedMessages.isNotEmpty())
    }

    private class FakeChatRepository : ChatRepository {
        var savedConversation: ConversationEntity? = null
        var savedMessages: List<MessageRecord> = emptyList()
        private val conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
        private val messagesByConversation = mutableMapOf<String, MutableList<MessageRecord>>()

        override fun getConversations(): Flow<List<ConversationEntity>> = conversations

        override fun getMessages(conversationId: String, onlyToxic: Boolean): Flow<PagingData<MessageRecord>> =
            flowOf(PagingData.empty())

        override suspend fun insertConversation(conversation: ConversationEntity) {
            savedConversation = conversation
            conversations.value = conversations.value + conversation
        }

        override suspend fun updateConversation(conversation: ConversationEntity) {
            savedConversation = conversation
            conversations.value = conversations.value.map { if (it.id == conversation.id) conversation else it }
        }

        override suspend fun insertMessagesBatch(messages: List<MessageRecord>) {
            savedMessages = savedMessages + messages
            if (messages.isNotEmpty()) {
                val cid = messages.first().conversationId
                val bucket = messagesByConversation.getOrPut(cid) { mutableListOf() }
                bucket.addAll(messages)
            }
        }

        override suspend fun getConversation(id: String): ConversationEntity? =
            conversations.value.firstOrNull { it.id == id }

        override fun getConversationFlow(id: String): Flow<ConversationEntity?> {
            return conversations.map { list -> list.find { it.id == id } }
        }

        override fun getToxicCount(conversationId: String): Flow<Int> = flowOf(0)

        override suspend fun getDistinctParticipants(conversationId: String): List<String> {
            val messages = messagesByConversation[conversationId] ?: return emptyList()
            return messages.mapNotNull { it.speakerRaw }.distinct()
        }

        override suspend fun getLatestMessages(conversationId: String, limit: Int): List<MessageRecord> {
            val messages = messagesByConversation[conversationId] ?: return emptyList()
            return messages.takeLast(limit)
        }

        override suspend fun saveConversation(conversation: ConversationEntity) {
            insertConversation(conversation)
        }

        override suspend fun saveImport(conversation: ConversationEntity, messages: List<MessageRecord>) {
            insertConversation(conversation)
            insertMessagesBatch(messages)
        }
    }
}
