package com.offnetic.di

import com.offnetic.data.relay.RelayPool
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RelayModule {

    @Provides
    @Singleton
    fun provideRelayPool(): RelayPool =
        RelayPool.create(CoroutineScope(SupervisorJob() + Dispatchers.IO))
}
