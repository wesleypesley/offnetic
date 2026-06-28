package com.offnetic.ui.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrScannerViewModelTest {

    @Test
    fun `onCodeDetected captures first payload and ignores later ones until cleared`() {
        val vm = QrScannerViewModel()

        vm.onCodeDetected("first")
        assertEquals("first", vm.state.value.detectedPayload)

        vm.onCodeDetected("second")
        assertEquals("first", vm.state.value.detectedPayload)

        vm.clearDetected()
        assertNull(vm.state.value.detectedPayload)

        vm.onCodeDetected("third")
        assertEquals("third", vm.state.value.detectedPayload)
    }
}
