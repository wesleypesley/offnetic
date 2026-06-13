package com.offnetic.di

import android.content.Context
import androidx.room.Room
import com.offnetic.data.local.crypto.SQLCipherKeyProvider
import com.offnetic.data.local.db.OffneticDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
        sqlCipherKeyProvider: SQLCipherKeyProvider
    ): OffneticDatabase {
        val key = sqlCipherKeyProvider.getKey()
        val factory = SupportFactory(key)
        Timber.d("SQLCipher database opening: offnetic.db")
        return Room.databaseBuilder(
            context.applicationContext,
            OffneticDatabase::class.java,
            "offnetic.db"
        )
            .openHelperFactory(factory)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    Timber.d("Database created with SQLCipher")
                }
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    Timber.d("Database opened (SQLCipher)")
                }
            })
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides @Singleton
    fun provideContactDao(database: OffneticDatabase): ContactDao = database.contactDao()

    @Provides @Singleton
    fun provideMessageDao(database: OffneticDatabase): MessageDao = database.messageDao()

    @Provides @Singleton
    fun provideSessionDao(database: OffneticDatabase): SessionDao = database.sessionDao()

    @Provides @Singleton
    fun providePreKeyDao(database: OffneticDatabase): PreKeyDao = database.preKeyDao()

    @Provides @Singleton
    fun provideIdentityDao(database: OffneticDatabase): IdentityDao = database.identityDao()

    @Provides @Singleton
    fun provideProfileDao(database: OffneticDatabase): ProfileDao = database.profileDao()

    @Provides @Singleton
    fun provideSignalPreKeyDao(database: OffneticDatabase): SignalPreKeyDao = database.signalPreKeyDao()

    @Provides @Singleton
    fun provideSignalSignedPreKeyDao(database: OffneticDatabase): SignalSignedPreKeyDao = database.signalSignedPreKeyDao()

    @Provides @Singleton
    fun provideSignalSessionDao(database: OffneticDatabase): SignalSessionDao = database.signalSessionDao()

    @Provides @Singleton
    fun provideSignalSenderKeyDao(database: OffneticDatabase): SignalSenderKeyDao = database.signalSenderKeyDao()

    @Provides @Singleton
    fun provideSignalIdentityDao(database: OffneticDatabase): SignalIdentityDao = database.signalIdentityDao()

    @Provides @Singleton
    fun provideCallHistoryDao(database: OffneticDatabase): CallHistoryDao = database.callHistoryDao()
}
