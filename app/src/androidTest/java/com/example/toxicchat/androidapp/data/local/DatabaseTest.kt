package com.example.toxicchat.androidapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var conversationDao: ConversationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Usiamo un database in-memory per i test, ma per validare la creazione delle tabelle
        // potremmo anche usare quello reale se volessimo vedere il file.
        // Qui usiamo inMemory per velocità e isolamento.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        conversationDao = db.conversationDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDbCreation() {
        // L'accesso al DAO forza l'apertura/creazione del DB
        val allConversations = conversationDao.getAllConversations()
        assertNotNull(allConversations)
    }
}
