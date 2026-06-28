package com.offnetic.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.offnetic.data.local.db.dao.CallHistoryDao
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.PreKeyDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.db.dao.SessionDao
import com.offnetic.data.local.db.dao.SignalIdentityDao
import com.offnetic.data.local.db.dao.SignalPreKeyDao
import com.offnetic.data.local.db.dao.SignalSenderKeyDao
import com.offnetic.data.local.db.dao.SignalSessionDao
import com.offnetic.data.local.db.dao.SignalSignedPreKeyDao
import com.offnetic.data.local.db.dao.PendingRequestDao
import com.offnetic.data.local.db.dao.RelayOutboxDao
import com.offnetic.data.local.db.dao.RelayStateDao
import com.offnetic.data.local.db.dao.NostrIdentityDao
import com.offnetic.data.local.db.entity.CallHistoryEntity
import com.offnetic.data.local.db.entity.Contact
import com.offnetic.data.local.db.entity.Identity
import com.offnetic.data.local.db.entity.Message
import com.offnetic.data.local.db.entity.PreKeyBundleEntity
import com.offnetic.data.local.db.entity.Profile
import com.offnetic.data.local.db.entity.Session
import com.offnetic.data.local.db.entity.SignalIdentityEntity
import com.offnetic.data.local.db.entity.SignalPreKeyEntity
import com.offnetic.data.local.db.entity.SignalSenderKeyEntity
import com.offnetic.data.local.db.entity.SignalSessionEntity
import com.offnetic.data.local.db.entity.SignalSignedPreKeyEntity
import com.offnetic.data.local.db.entity.PendingRequestEntity
import com.offnetic.data.local.db.entity.RelayOutboxEntity
import com.offnetic.data.local.db.entity.RelayStateEntity
import com.offnetic.data.local.db.entity.NostrIdentityEntity

@Database(
    entities = [
        Contact::class,
        Message::class,
        Session::class,
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
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class OffneticDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao
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
        @Volatile
        private var INSTANCE: OffneticDatabase? = null

        fun getInstance(context: Context): OffneticDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OffneticDatabase::class.java,
                    "offnetic.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
