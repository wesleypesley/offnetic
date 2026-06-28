package com.offnetic.data.relay

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RelayFilterTest {

    @Test
    fun `inbox filter serializes kinds p-tag and since`() {
        val json = JSONObject(RelayFilter(kinds = listOf(1059), pTags = listOf("abc"), since = 123L).toJson())
        assertEquals(1059, json.getJSONArray("kinds").getInt(0))
        assertEquals("abc", json.getJSONArray("#p").getString(0))
        assertEquals(123L, json.getLong("since"))
        assertFalse(json.has("until"))
        assertFalse(json.has("limit"))
    }

    @Test
    fun `empty filter is empty object`() {
        assertEquals(0, JSONObject(RelayFilter().toJson()).length())
    }
}
