package com.example.toxicchat.androidapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore by preferencesDataStore(name = "security_prefs")

@Singleton
class SecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
    private val SALT = "ToxicChat_2024_Salt"

    val hasPinSet: Flow<Boolean> = context.securityDataStore.data.map { it[PIN_HASH_KEY] != null }

    suspend fun savePin(pin: String) {
        val hash = hashPin(pin)
        context.securityDataStore.edit { it[PIN_HASH_KEY] = hash }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val savedHash = context.securityDataStore.data.map { it[PIN_HASH_KEY] }.first()
        return savedHash == hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((pin + SALT).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
