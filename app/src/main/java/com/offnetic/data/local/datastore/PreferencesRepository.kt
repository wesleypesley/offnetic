package com.offnetic.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val themeMode: Flow<Int>
    val proximityPingsEnabled: Flow<Boolean>
    val proximityPingThresholdMinutes: Flow<Int>

    suspend fun setThemeMode(value: Int)
    suspend fun setProximityPingsEnabled(value: Boolean)
    suspend fun setProximityPingThresholdMinutes(value: Int)
}