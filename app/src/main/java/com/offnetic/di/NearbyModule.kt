package com.offnetic.di

import android.content.Context
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.Nearby
import com.offnetic.data.crypto.SignalProtocolManager
import com.offnetic.data.local.db.dao.ContactDao
import com.offnetic.data.local.db.dao.IdentityDao
import com.offnetic.data.local.db.dao.MessageDao
import com.offnetic.data.local.db.dao.ProfileDao
import com.offnetic.data.local.datastore.PreferencesRepository
import com.offnetic.data.nearby.NcapManager
import com.offnetic.data.nearby.NcapManagerImpl
import com.offnetic.data.nearby.WifiP2pHandler
import com.offnetic.util.ProximityPingNotifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NearbyModule {

    @Provides
    @Singleton
    fun provideConnectionsClient(@dagger.hilt.android.qualifiers.ApplicationContext context: Context): ConnectionsClient {
        return Nearby.getConnectionsClient(context)
    }

    @Provides
    @Singleton
    fun provideNcapManager(
        connectionsClient: ConnectionsClient,
        identityDao: IdentityDao,
        contactDao: ContactDao,
        profileDao: ProfileDao,
        prefs: PreferencesRepository,
        proximityPingNotifier: ProximityPingNotifier,
        signalProtocolManager: SignalProtocolManager,
        messageDao: MessageDao,
        wifiP2pHandler: WifiP2pHandler,
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): NcapManager = NcapManagerImpl(
        connectionsClient,
        identityDao,
        contactDao,
        profileDao,
        prefs,
        proximityPingNotifier,
        signalProtocolManager,
        messageDao,
        wifiP2pHandler,
        context
    )
}
