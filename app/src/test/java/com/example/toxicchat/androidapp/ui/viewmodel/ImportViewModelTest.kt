package com.example.toxicchat.androidapp.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.toxicchat.androidapp.data.importer.SharedImportManager
import com.example.toxicchat.androidapp.data.local.ConversationEntity
import com.example.toxicchat.androidapp.data.parser.WhatsAppTxtParser
import com.example.toxicchat.androidapp.domain.model.DateOrderUsed
import com.example.toxicchat.androidapp.domain.model.MessageRecord
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {

    private val repository = mockk<ChatRepository>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val sharedImportManager = mockk<SharedImportManager>(relaxed = true)

    private val parser = WhatsAppTxtParser()
    private lateinit var viewModel: ImportViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { context.contentResolver } returns contentResolver
        viewModel = ImportViewModel(context, repository, parser, sharedImportManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startImport emits NeedsDateOrderChoice when date order is ambiguous`() = runTest {
        val uri = Uri.parse("content://test/wa_ambiguous.txt")
        val lines = listOf("01/02/24, 10:00 - User: Hello")
        stubOpenInputStream(uri, lines)

        viewModel.startImport(uri, "test.txt")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ImportState.NeedsDateOrderChoice)
        val s = state as ImportState.NeedsDateOrderChoice
        assertEquals(uri, s.uri)

        coVerify(exactly = 0) { repository.insertConversation(any()) }
        coVerify(exactly = 0) { repository.insertMessagesBatch(any()) }
    }

    @Test
    fun `startImport emits Error when import phase cannot read stream`() = runTest {
        val uri = Uri.parse("content://test/wa_fail_import.txt")
        val lines = listOf("13/01/24, 10:00 - User: Hello")

        val first: InputStream? = asStream(lines)
        every { contentResolver.openInputStream(uri) } returns first andThen null

        viewModel.startImport(uri, "test.txt")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ImportState.Error)
    }

    @Test
    fun `startImport saves data and emits Imported when parse is successful`() = runTest {
        val uri = Uri.parse("content://test/wa_ok.txt")
        val lines = listOf("13/01/24, 10:00 - User: Hello")

        val first: InputStream? = asStream(lines)
        val second: InputStream? = asStream(lines)
        every { contentResolver.openInputStream(uri) } returns first andThen second

        val convInsertSlot = slot<ConversationEntity>()
        val batchSlot = slot<List<MessageRecord>>()

        viewModel.startImport(uri, "test.txt")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ImportState.Imported)

        val imported = state as ImportState.Imported
        assertTrue(imported.conversationId.isNotBlank())
        assertEquals(1, imported.metadata.parsedMessagesCount)

        coVerify(exactly = 1) { repository.insertConversation(capture(convInsertSlot)) }
        coVerify(atLeast = 1) { repository.insertMessagesBatch(capture(batchSlot)) }

        assertEquals(imported.conversationId, convInsertSlot.captured.id)
        assertTrue(batchSlot.captured.isNotEmpty())
        assertTrue(batchSlot.captured.all { it.conversationId == imported.conversationId })
    }

    @Test
    fun `onDateOrderSelected completes import and saves`() = runTest {
        val uri = Uri.parse("content://test/wa_choice.txt")
        val choice = DateOrderUsed.DMY
        val lines = listOf("01/01/24, 10:00 - User: Hello")

        stubOpenInputStream(uri, lines)

        viewModel.onDateOrderSelected(uri, "test.txt", choice)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ImportState.Imported)

        coVerify(exactly = 1) { repository.insertConversation(any()) }
        coVerify(atLeast = 1) { repository.insertMessagesBatch(any()) }
    }

    private fun stubOpenInputStream(uri: Uri, lines: List<String>) {
        every { contentResolver.openInputStream(uri) } returns asStream(lines)
    }

    private fun asStream(lines: List<String>): ByteArrayInputStream {
        val content = lines.joinToString("\n")
        return ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
    }
}
