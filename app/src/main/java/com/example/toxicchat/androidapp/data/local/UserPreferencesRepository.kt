package com.example.toxicchat.androidapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val GLOBAL_USER_NAME = stringPreferencesKey("global_user_name")

    val globalUserName: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[GLOBAL_USER_NAME]
        }

    suspend fun setGlobalUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[GLOBAL_USER_NAME] = name
        }
    }
}
