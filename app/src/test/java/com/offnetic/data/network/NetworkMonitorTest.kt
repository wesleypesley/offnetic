package com.offnetic.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkMonitorTest {

    private lateinit var cm: ConnectivityManager
    private val callbackSlot = slot<ConnectivityManager.NetworkCallback>()

    @Before
    fun setUp() {
        cm = mockk(relaxed = true)
        every { cm.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit
    }

    private fun caps(internet: Boolean, validated: Boolean): NetworkCapabilities = mockk {
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns internet
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
    }

    @Test
    fun `starts offline when there is no active network`() {
        val monitor = NetworkMonitor(cm)
        assertFalse(monitor.isOnline.value)
    }

    @Test
    fun `goes online when validated internet capability arrives`() {
        val monitor = NetworkMonitor(cm)
        callbackSlot.captured.onCapabilitiesChanged(mockk(), caps(internet = true, validated = true))
        assertTrue(monitor.isOnline.value)
    }

    @Test
    fun `stays offline when internet is present but not validated`() {
        val monitor = NetworkMonitor(cm)
        callbackSlot.captured.onCapabilitiesChanged(mockk(), caps(internet = true, validated = false))
        assertFalse(monitor.isOnline.value)
    }

    @Test
    fun `goes offline on network lost`() {
        val monitor = NetworkMonitor(cm)
        callbackSlot.captured.onCapabilitiesChanged(mockk(), caps(internet = true, validated = true))
        callbackSlot.captured.onLost(mockk())
        assertFalse(monitor.isOnline.value)
    }

    @Test
    fun `close unregisters the callback`() {
        val monitor = NetworkMonitor(cm)
        monitor.close()
        verify { cm.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }
}
