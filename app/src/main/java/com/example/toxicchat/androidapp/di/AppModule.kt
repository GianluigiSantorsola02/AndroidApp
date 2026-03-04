package com.example.toxicchat.androidapp.di

import android.content.Context
import androidx.work.WorkManager
import com.example.toxicchat.androidapp.data.repository.AnalysisRepositoryImpl
import com.example.toxicchat.androidapp.data.repository.ChatRepositoryImpl
import com.example.toxicchat.androidapp.domain.repository.AnalysisRepository
import com.example.toxicchat.androidapp.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindAnalysisRepository(impl: AnalysisRepositoryImpl): AnalysisRepository

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
            return WorkManager.getInstance(context)
        }
    }
}
