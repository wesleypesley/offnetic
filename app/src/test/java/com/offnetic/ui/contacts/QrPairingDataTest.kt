package com.offnetic.ui.contacts

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QrPairingDataTest {

    @Test
    fun `round-trips with nostr key`() {
        val original = QrPairingData(
            publicKey = "alicePk",
            displayName = "Alice",
            nostrPublicKey = "npub1xyz"
        )
        val decoded = QrPairingData.fromQrPayload(original.toQrPayload())
        assertEquals("alicePk", decoded!!.publicKey)
        assertEquals("Alice", decoded.displayName)
        assertEquals("npub1xyz", decoded.nostrPublicKey)
    }

    @Test
    fun `parses payload without nostr key`() {
        val original = QrPairingData(publicKey = "alicePk", displayName = "Alice")
        val decoded = QrPairingData.fromQrPayload(original.toQrPayload())
        assertEquals("alicePk", decoded!!.publicKey)
        assertEquals("Alice", decoded.displayName)
        assertNull(decoded.nostrPublicKey)
    }

    @Test
    fun `round-trips with null display name`() {
        val original = QrPairingData(
            publicKey = "alicePk",
            displayName = null,
            nostrPublicKey = "npub1xyz"
        )
        val decoded = QrPairingData.fromQrPayload(original.toQrPayload())
        assertEquals("alicePk", decoded!!.publicKey)
        assertNull(decoded.displayName)
        assertEquals("npub1xyz", decoded.nostrPublicKey)
    }

    @Test
    fun `returns null for invalid input`() {
        assertNull(QrPairingData.fromQrPayload("@@@invalid@@@"))
    }
}
