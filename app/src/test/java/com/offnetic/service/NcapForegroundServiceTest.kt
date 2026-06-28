package com.offnetic.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NcapForegroundServiceTest {

    @Test
    fun `computeSince back-dates lastSeen by 2 days to survive NIP-17 randomized timestamps`() {
        val nowSec = System.currentTimeMillis() / 1000
        val recent = nowSec - 10
        val backdateMargin = 2L * 24 * 60 * 60
        val since = NcapForegroundService.computeSince(recent)
        assertEquals(recent - backdateMargin, since)
    }

    @Test
    fun `computeSince falls back to 3 days when lastSeen is too old`() {
        val nowSec = System.currentTimeMillis() / 1000
        val old = nowSec - 10L * 24 * 60 * 60
        val expected = nowSec - 3L * 24 * 60 * 60
        assertEquals(expected, NcapForegroundService.computeSince(old))
    }

    @Test
    fun `computeSince uses backdate when lastSeen is null`() {
        val nowSec = System.currentTimeMillis() / 1000
        val expected = nowSec - 3L * 24 * 60 * 60
        assertEquals(expected, NcapForegroundService.computeSince(null))
    }
}
