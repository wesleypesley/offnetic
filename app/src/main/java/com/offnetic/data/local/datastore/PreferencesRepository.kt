package com.offnetic.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val isDiscoverable: Flow<Boolean>
    val isBackgroundScanningEnabled: Flow<Boolean>
    val themeMode: Flow<Int>
    val proximityPingsEnabled: Flow<Boolean>
    val proximityPingThresholdMinutes: Flow<Int>
    val biometricTimeoutMinutes: Flow<Int>
    val dataSelfDestructEnabled: Flow<Boolean>
    val dataRetentionDays: Flow<Int>

    suspend fun setDiscoverable(value: Boolean)
    suspend fun setBackgroundScanningEnabled(value: Boolean)
    suspend fun setThemeMode(value: Int)
    suspend fun setProximityPingsEnabled(value: Boolean)
    suspend fun setProximityPingThresholdMinutes(value: Int)
    suspend fun setBiometricTimeoutMinutes(value: Int)
    suspend fun setDataSelfDestructEnabled(value: Boolean)
    suspend fun setDataRetentionDays(value: Int)
}