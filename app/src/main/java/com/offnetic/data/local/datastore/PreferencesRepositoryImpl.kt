package com.offnetic.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    private val THEME_MODE = intPreferencesKey("theme_mode")
    private val PROXIMITY_PINGS_ENABLED = booleanPreferencesKey("proximity_pings_enabled")
    private val PROXIMITY_PING_THRESHOLD_MINUTES = intPreferencesKey("proximity_ping_threshold_minutes")

    override val themeMode: Flow<Int> = dataStore.data
        .map { it[THEME_MODE] ?: 0 }

    override val proximityPingsEnabled: Flow<Boolean> = dataStore.data
        .map { it[PROXIMITY_PINGS_ENABLED] ?: true }

    override val proximityPingThresholdMinutes: Flow<Int> = dataStore.data
        .map { it[PROXIMITY_PING_THRESHOLD_MINUTES] ?: 30 }

    override suspend fun setThemeMode(value: Int) {
        dataStore.edit { it[THEME_MODE] = value }
    }

    override suspend fun setProximityPingsEnabled(value: Boolean) {
        dataStore.edit { it[PROXIMITY_PINGS_ENABLED] = value }
    }

    override suspend fun setProximityPingThresholdMinutes(value: Int) {
        dataStore.edit { it[PROXIMITY_PING_THRESHOLD_MINUTES] = value }
    }
}