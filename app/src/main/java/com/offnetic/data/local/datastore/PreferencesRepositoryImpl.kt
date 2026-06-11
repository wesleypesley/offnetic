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

    private val DISCOVERABLE = booleanPreferencesKey("discoverable")
    private val BACKGROUND_SCANNING = booleanPreferencesKey("background_scanning")
    private val THEME_MODE = intPreferencesKey("theme_mode")
    private val PROXIMITY_PINGS_ENABLED = booleanPreferencesKey("proximity_pings_enabled")
    private val PROXIMITY_PING_THRESHOLD_MINUTES = intPreferencesKey("proximity_ping_threshold_minutes")
    private val BIOMETRIC_TIMEOUT_MINUTES = intPreferencesKey("biometric_timeout_minutes")
    private val DATA_SELF_DESTRUCT_ENABLED = booleanPreferencesKey("data_self_destruct_enabled")
    private val DATA_RETENTION_DAYS = intPreferencesKey("data_retention_days")

    override val isDiscoverable: Flow<Boolean> = dataStore.data
        .map { it[DISCOVERABLE] ?: true }

    override val isBackgroundScanningEnabled: Flow<Boolean> = dataStore.data
        .map { it[BACKGROUND_SCANNING] ?: true }

    override val themeMode: Flow<Int> = dataStore.data
        .map { it[THEME_MODE] ?: 0 }

    override val proximityPingsEnabled: Flow<Boolean> = dataStore.data
        .map { it[PROXIMITY_PINGS_ENABLED] ?: true }

    override val proximityPingThresholdMinutes: Flow<Int> = dataStore.data
        .map { it[PROXIMITY_PING_THRESHOLD_MINUTES] ?: 30 }

    override val biometricTimeoutMinutes: Flow<Int> = dataStore.data
        .map { it[BIOMETRIC_TIMEOUT_MINUTES] ?: 5 }

    override val dataSelfDestructEnabled: Flow<Boolean> = dataStore.data
        .map { it[DATA_SELF_DESTRUCT_ENABLED] ?: false }

    override val dataRetentionDays: Flow<Int> = dataStore.data
        .map { it[DATA_RETENTION_DAYS] ?: 30 }

    override suspend fun setDiscoverable(value: Boolean) {
        dataStore.edit { it[DISCOVERABLE] = value }
    }

    override suspend fun setBackgroundScanningEnabled(value: Boolean) {
        dataStore.edit { it[BACKGROUND_SCANNING] = value }
    }

    override suspend fun setThemeMode(value: Int) {
        dataStore.edit { it[THEME_MODE] = value }
    }

    override suspend fun setProximityPingsEnabled(value: Boolean) {
        dataStore.edit { it[PROXIMITY_PINGS_ENABLED] = value }
    }

    override suspend fun setProximityPingThresholdMinutes(value: Int) {
        dataStore.edit { it[PROXIMITY_PING_THRESHOLD_MINUTES] = value }
    }

    override suspend fun setBiometricTimeoutMinutes(value: Int) {
        dataStore.edit { it[BIOMETRIC_TIMEOUT_MINUTES] = value }
    }

    override suspend fun setDataSelfDestructEnabled(value: Boolean) {
        dataStore.edit { it[DATA_SELF_DESTRUCT_ENABLED] = value }
    }

    override suspend fun setDataRetentionDays(value: Int) {
        dataStore.edit { it[DATA_RETENTION_DAYS] = value }
    }
}