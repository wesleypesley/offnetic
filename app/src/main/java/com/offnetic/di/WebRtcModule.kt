package com.offnetic.di

import android.content.Context
import com.offnetic.data.nearby.NcapManager
import com.offnetic.data.nearby.WebRtcManager
import com.offnetic.data.nearby.WifiP2pHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WebRtcModule {

    @Provides
    @Singleton
    fun provideWebRtcManager(
        @ApplicationContext context: Context,
        ncapManager: NcapManager,
        wifiP2pHandler: WifiP2pHandler
    ): WebRtcManager {
        return WebRtcManager(context, ncapManager, wifiP2pHandler)
    }
}
