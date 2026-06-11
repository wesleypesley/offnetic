package com.offnetic.domain.model

sealed class NearbyState {
    data object Idle : NearbyState()
    data object Advertising : NearbyState()
    data object Discovering : NearbyState()
    data object Active : NearbyState()
    data class Error(val message: String) : NearbyState()
}
