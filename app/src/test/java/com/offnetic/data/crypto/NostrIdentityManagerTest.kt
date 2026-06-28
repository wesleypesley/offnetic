package com.offnetic.data.crypto

import android.app.Application
import androidx.room.Room
import com.offnetic.data.local.db.OffneticDatabase
import com.offnetic.data.local.db.dao.NostrIdentityDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrIdentityManagerTest {

    private lateinit var db: OffneticDatabase
    private lateinit var dao: NostrIdentityDao

    private class FakeKeyGenerator : NostrKeyGenerator {
        var calls = 0
        override fun generate(): NostrKeyPair {
            calls++
            return NostrKeyPair(ByteArray(32) { 1 }, ByteArray(32) { 2 })
        }
    }

    private val generator = FakeKeyGenerator()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, OffneticDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.nostrIdentityDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `generates once and persists`() = runBlocking {
        val manager = NostrIdentityManager(dao, generator)
        val first = manager.generateIfNeeded()
        assertEquals(1, generator.calls)
        assertArrayEquals(ByteArray(32) { 1 }, first.privateKey)
        assertArrayEquals(ByteArray(32) { 2 }, first.publicKey)
        assertArrayEquals(ByteArray(32) { 2 }, dao.get()!!.publicKey)
    }

    @Test
    fun `does not regenerate when one already exists`() = runBlocking {
        val manager = NostrIdentityManager(dao, generator)
        manager.generateIfNeeded()
        val second = manager.generateIfNeeded()
        assertEquals(1, generator.calls)
        assertArrayEquals(ByteArray(32) { 2 }, second.publicKey)
    }
}
