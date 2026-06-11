package com.offnetic.di

import android.content.Context
import android.net.wifi.WifiManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SystemModule {

    @Provides
    @Singleton
    fun provideWifiManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ): WifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
}
