package com.offnetic.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.offnetic.data.local.db.dao.CallHistoryDao
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.NostrIdentityDao
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.dao.PreKeyDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.dao.RelayOutboxDao
import com.offnetic.data.local.db.dao.RelayStateDao
import com.offnetic.data.local.db.dao.SignalIdentityDao
import com.offnetic.data.local.db.dao.SignalPreKeyDao
import com.offnetic.data.local.db.dao.SignalSenderKeyDao
import com.offnetic.data.local.db.dao.SignalSessionDao
import com.offnetic.data.local.db.dao.SignalSignedPreKeyDao
import com.offnetic.data.local.db.entity.CallHistoryEntity
import com.offnetic.data.local.db.entity.Contact
import com.offnetic.data.local.db.entity.Identity
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.local.db.entity.NostrIdentityEntity
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.PreKeyBundleEntity
import com.offnetic.data.local.db.entity.Profile
import com.offnetic.data.local.db.entity.RelayOutboxEntity
import com.offnetic.data.local.db.entity.RelayStateEntity
import com.offnetic.data.local.db.entity.SignalIdentityEntity
import com.offnetic.data.local.db.entity.SignalPreKeyEntity
import com.offnetic.data.local.db.entity.SignalSenderKeyEntity
import com.offnetic.data.local.db.entity.SignalSessionEntity
import com.offnetic.data.local.db.entity.SignalSignedPreKeyEntity

@Database(
    entities = [
        Contact::class,
        Message::class,
        PreKeyBundleEntity::class,
        Identity::class,
        Profile::class,
        CallHistoryEntity::class,
        SignalPreKeyEntity::class,
        SignalSignedPreKeyEntity::class,
        SignalSessionEntity::class,
        SignalSenderKeyEntity::class,
        SignalIdentityEntity::class,
        RelayOutboxEntity::class,
        PendingRequestEntity::class,
        RelayStateEntity::class,
        NostrIdentityEntity::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OffneticDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun preKeyDao(): PreKeyDao
    abstract fun identityDao(): IdentityDao
    abstract fun profileDao(): ProfileDao
    abstract fun signalPreKeyDao(): SignalPreKeyDao
    abstract fun signalSignedPreKeyDao(): SignalSignedPreKeyDao
    abstract fun signalSessionDao(): SignalSessionDao
    abstract fun signalSenderKeyDao(): SignalSenderKeyDao
    abstract fun signalIdentityDao(): SignalIdentityDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun relayOutboxDao(): RelayOutboxDao
    abstract fun pendingRequestDao(): PendingRequestDao
    abstract fun relayStateDao(): RelayStateDao
    abstract fun nostrIdentityDao(): NostrIdentityDao

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // O(1) index for nostrPublicKey — called on every incoming relay gift-wrap
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_nostrPublicKey ON contacts(nostrPublicKey)")
                // Drop zombie custom Double-Ratchet sessions table; libsignal signal_sessions is the active store.
                // This table was never written to and contains plaintext ratchet key columns.
                db.execSQL("DROP TABLE IF EXISTS sessions")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Covers the MAX(id) GROUP BY chatId subquery in getChatSummaries (DB22)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatId_id ON messages(chatId, id)")
            }
        }
    }
}
