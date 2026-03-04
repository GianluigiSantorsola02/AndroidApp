package com.example.toxicchat.androidapp.di

import android.content.Context
import com.example.toxicchat.androidapp.data.local.AppDatabase
import com.example.toxicchat.androidapp.data.local.AnalysisDao
import com.example.toxicchat.androidapp.data.local.ConversationDao
import com.example.toxicchat.androidapp.data.local.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideAnalysisDao(database: AppDatabase): AnalysisDao {
        return database.analysisDao()
    }
}
