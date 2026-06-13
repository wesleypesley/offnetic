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

    val proximityPingsEnabled: StateFlow<Boolean> = prefs.proximityPingsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val proximityPingThresholdMinutes: StateFlow<Int> = prefs.proximityPingThresholdMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    fun setProximityPingsEnabled(value: Boolean) = viewModelScope.launch { prefs.setProximityPingsEnabled(value) }
    fun setProximityPingThresholdMinutes(value: Int) = viewModelScope.launch { prefs.setProximityPingThresholdMinutes(value) }
}
