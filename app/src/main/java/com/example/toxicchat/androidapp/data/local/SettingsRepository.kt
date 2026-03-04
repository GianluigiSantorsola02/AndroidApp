package com.example.toxicchat.androidapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val SERVER_ANALYSIS_CONSENT = booleanPreferencesKey("server_analysis_consent")

    val serverAnalysisConsent: Flow<Boolean> = context.settingsDataStore.data
        .map { it[SERVER_ANALYSIS_CONSENT] ?: false }

    suspend fun setServerAnalysisConsent(accepted: Boolean) {
        context.settingsDataStore.edit { it[SERVER_ANALYSIS_CONSENT] = accepted }
    }
}
