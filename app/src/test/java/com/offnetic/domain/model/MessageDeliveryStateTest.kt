package com.offnetic.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDeliveryStateTest {

    @Test
    fun `name and valueOf round-trip for all states`() {
        for (state in MessageDeliveryState.values()) {
            assertEquals(state, MessageDeliveryState.valueOf(state.name))
        }
    }
}
