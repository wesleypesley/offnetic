package com.offnetic.di

import android.content.Context
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.crypto.SignalProtocolStoreImpl
import com.offnetic.data.local.crypto.IdentityKeyManager
import com.offnetic.data.local.crypto.IdentityKeyManagerImpl
import com.offnetic.data.local.crypto.SQLCipherKeyProvider
import com.offnetic.data.local.crypto.SQLCipherKeyProviderImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideSQLCipherKeyProvider(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): SQLCipherKeyProvider = SQLCipherKeyProviderImpl(context)

    @Provides
    @Singleton
    fun provideIdentityKeyManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
        identityDao: com.offnetic.data.local.db.dao.IdentityDao
    ): IdentityKeyManager = IdentityKeyManagerImpl(context, identityDao)

    @Provides
    @Singleton
    fun provideSignalProtocolStore(
        identityKeyManager: IdentityKeyManager,
        signalPreKeyDao: com.offnetic.data.local.db.dao.SignalPreKeyDao,
        signalSignedPreKeyDao: com.offnetic.data.local.db.dao.SignalSignedPreKeyDao,
        signalSessionDao: com.offnetic.data.local.db.dao.SignalSessionDao,
        signalSenderKeyDao: com.offnetic.data.local.db.dao.SignalSenderKeyDao,
        signalIdentityDao: com.offnetic.data.local.db.dao.SignalIdentityDao
    ): SignalProtocolStoreImpl = SignalProtocolStoreImpl(
        identityKeyManager = identityKeyManager,
        signalPreKeyDao = signalPreKeyDao,
        signalSignedPreKeyDao = signalSignedPreKeyDao,
        signalSessionDao = signalSessionDao,
        signalSenderKeyDao = signalSenderKeyDao,
        signalIdentityDao = signalIdentityDao
    )

    @Provides
    @Singleton
    fun provideSignalProtocolManager(
        identityKeyManager: IdentityKeyManager,
        protocolStore: SignalProtocolStoreImpl,
        preKeyDao: com.offnetic.data.local.db.dao.PreKeyDao
    ): SignalProtocolManager = SignalProtocolManager(
        identityKeyManager = identityKeyManager,
        protocolStore = protocolStore,
        preKeyDao = preKeyDao
    )
}
