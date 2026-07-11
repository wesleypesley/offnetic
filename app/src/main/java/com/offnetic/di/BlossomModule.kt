package com.offnetic.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BlossomModule {

    @Provides
    @Singleton
    @Blossom
    fun provideBlossomOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(com.offnetic.config.OffneticConfig.BLOSSOM_CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(com.offnetic.config.OffneticConfig.BLOSSOM_READ_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(com.offnetic.config.OffneticConfig.BLOSSOM_CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .build()
}
