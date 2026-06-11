package com.offnetic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offnetic.data.local.datastore.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository
) : ViewModel() {

    val isDiscoverable: StateFlow<Boolean> = prefs.isDiscoverable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isBackgroundScanningEnabled: StateFlow<Boolean> = prefs.isBackgroundScanningEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val proximityPingsEnabled: StateFlow<Boolean> = prefs.proximityPingsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val proximityPingThresholdMinutes: StateFlow<Int> = prefs.proximityPingThresholdMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val biometricTimeoutMinutes: StateFlow<Int> = prefs.biometricTimeoutMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val dataSelfDestructEnabled: StateFlow<Boolean> = prefs.dataSelfDestructEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dataRetentionDays: StateFlow<Int> = prefs.dataRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    fun setDiscoverable(value: Boolean) = viewModelScope.launch { prefs.setDiscoverable(value) }
    fun setBackgroundScanningEnabled(value: Boolean) = viewModelScope.launch { prefs.setBackgroundScanningEnabled(value) }
    fun setProximityPingsEnabled(value: Boolean) = viewModelScope.launch { prefs.setProximityPingsEnabled(value) }
    fun setProximityPingThresholdMinutes(value: Int) = viewModelScope.launch { prefs.setProximityPingThresholdMinutes(value) }
    fun setBiometricTimeoutMinutes(value: Int) = viewModelScope.launch { prefs.setBiometricTimeoutMinutes(value) }
    fun setDataSelfDestructEnabled(value: Boolean) = viewModelScope.launch { prefs.setDataSelfDestructEnabled(value) }
    fun setDataRetentionDays(value: Int) = viewModelScope.launch { prefs.setDataRetentionDays(value) }
}
