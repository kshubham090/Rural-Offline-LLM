package com.gyan.offline.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gyan_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE    = booleanPreferencesKey("onboarding_done")
        val MODELS_DOWNLOADED  = booleanPreferencesKey("models_downloaded")
        val AD_CONSENT_GIVEN   = booleanPreferencesKey("ad_consent_given")
        val VOICE_ENABLED      = booleanPreferencesKey("voice_enabled")
        val IS_CHILD_DIRECTED  = booleanPreferencesKey("child_directed")
    }

    val onboardingDone: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    val modelsDownloaded: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MODELS_DOWNLOADED] ?: false }

    val adConsentGiven: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AD_CONSENT_GIVEN] ?: false }

    val voiceEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.VOICE_ENABLED] ?: true }

    val isChildDirected: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.IS_CHILD_DIRECTED] ?: false }

    suspend fun setOnboardingDone()              = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    suspend fun setModelsDownloaded()            = context.dataStore.edit { it[Keys.MODELS_DOWNLOADED] = true }
    suspend fun setAdConsent(given: Boolean)     = context.dataStore.edit { it[Keys.AD_CONSENT_GIVEN] = given }
    suspend fun setVoiceEnabled(enabled: Boolean)= context.dataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
    suspend fun setChildDirected(v: Boolean)     = context.dataStore.edit { it[Keys.IS_CHILD_DIRECTED] = v }
}
