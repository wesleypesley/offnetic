package com.offnetic.data.relay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDeduperTest {

    @Test
    fun `marks new ids and rejects duplicates`() {
        val d = EventDeduper()
        assertTrue(d.markSeen("a"))
        assertFalse(d.markSeen("a"))
        assertTrue(d.markSeen("b"))
        assertFalse(d.markSeen("b"))
    }

    @Test
    fun `evicts oldest beyond capacity`() {
        val d = EventDeduper(maxSize = 2)
        assertTrue(d.markSeen("a"))
        assertTrue(d.markSeen("b"))
        assertTrue(d.markSeen("c"))
        assertTrue(d.markSeen("a"))
        assertFalse(d.markSeen("c"))
    }
}
