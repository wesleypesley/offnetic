package com.offnetic.data.crypto.nostr

import org.junit.Assert.assertEquals
import org.junit.Test

class NostrEventTest {

    @Test
    fun `canonical string of empty event`() {
        val s = NostrEvent.canonicalString(
            pubkey = "abc",
            createdAt = 0,
            kind = 0,
            tags = emptyList(),
            content = ""
        )
        assertEquals("[0,\"abc\",0,0,[],\"\"]", s)
    }

    @Test
    fun `canonical string with tags`() {
        val s = NostrEvent.canonicalString(
            pubkey = "pk",
            createdAt = 1700000000,
            kind = 1,
            tags = listOf(listOf("e", "id1"), listOf("p", "pk2")),
            content = "hi"
        )
        assertEquals("[0,\"pk\",1700000000,1,[[\"e\",\"id1\"],[\"p\",\"pk2\"]],\"hi\"]", s)
    }

    @Test
    fun `content escaping follows nip01 and leaves slash unescaped`() {
        val s = NostrEvent.canonicalString("pk", 0, 1, emptyList(), "a\"b\\c\nd\te/f")
        assertEquals("[0,\"pk\",0,1,[],\"a\\\"b\\\\c\\nd\\te/f\"]", s)
    }

    @Test
    fun `unicode content is preserved`() {
        val s = NostrEvent.canonicalString("pk", 0, 1, emptyList(), "héllo 😀")
        assertEquals("[0,\"pk\",0,1,[],\"héllo 😀\"]", s)
    }

    @Test
    fun `control char is unicode escaped`() {
        val s = NostrEvent.canonicalString("pk", 0, 1, emptyList(), "\u0001")
        assertEquals("[0,\"pk\",0,1,[],\"\\u0001\"]", s)
    }

    @Test
    fun `computeId is deterministic lowercase and 64 hex chars`() {
        val id1 = NostrEvent.computeId("pk", 1700000000, 1, emptyList(), "hello")
        val id2 = NostrEvent.computeId("pk", 1700000000, 1, emptyList(), "hello")
        assertEquals(id1, id2)
        assertEquals(64, id1.length)
        assertEquals(id1, id1.lowercase())
    }
}
