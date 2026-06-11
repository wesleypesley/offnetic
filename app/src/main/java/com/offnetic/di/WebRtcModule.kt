package com.offnetic.di

import android.content.Context
import com.offnetic.data.nearby.NcapManager
import com.offnetic.data.nearby.WebRtcManager
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
        ncapManager: NcapManager
    ): WebRtcManager {
        return WebRtcManager(context, ncapManager)
    }
}
