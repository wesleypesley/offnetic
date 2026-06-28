package com.offnetic.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyNumberTest {

    @Test
    fun `compute is deterministic`() {
        assertEquals(
            SafetyNumber.compute("alice", "bob"),
            SafetyNumber.compute("alice", "bob")
        )
    }

    @Test
    fun `compute is symmetric`() {
        assertEquals(
            SafetyNumber.compute("alice", "bob"),
            SafetyNumber.compute("bob", "alice")
        )
    }

    @Test
    fun `compute returns 60 digits`() {
        val number = SafetyNumber.compute("alice", "bob")
        assertEquals(60, number.length)
        assertTrue(number.all { it.isDigit() })
    }

    @Test
    fun `different peers produce different numbers`() {
        assertNotEquals(
            SafetyNumber.compute("alice", "bob"),
            SafetyNumber.compute("alice", "carol")
        )
    }

    @Test
    fun `formatGroups produces twelve groups of five`() {
        val formatted = SafetyNumber.formatGroups(SafetyNumber.compute("alice", "bob"))
        val groups = formatted.split(" ")
        assertEquals(12, groups.size)
        assertTrue(groups.all { it.length == 5 })
    }
}
