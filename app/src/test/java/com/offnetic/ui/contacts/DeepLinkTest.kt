package com.offnetic.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {

    @Test
    fun `build and parse round-trip`() {
        val payload = "abc123-_xyz"
        val link = DeepLink.buildAddLink(payload)
        assertEquals("offnetic://add?data=abc123-_xyz", link)
        assertEquals(payload, DeepLink.parseAddLink(link))
    }

    @Test
    fun `parse rejects a wrong scheme or host`() {
        assertNull(DeepLink.parseAddLink("https://example.com?data=abc"))
        assertNull(DeepLink.parseAddLink("offnetic://other?data=abc"))
    }

    @Test
    fun `parse rejects empty data`() {
        assertNull(DeepLink.parseAddLink("offnetic://add?data="))
    }

    @Test
    fun `parse ignores trailing params`() {
        assertEquals("abc", DeepLink.parseAddLink("offnetic://add?data=abc&foo=bar"))
    }
}
