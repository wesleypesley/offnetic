package com.offnetic.di

import com.offnetic.data.crypto.NostrKeyGenerator
import com.offnetic.data.crypto.Secp256k1NostrKeyGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NostrCryptoModule {
    @Binds
    abstract fun bindNostrKeyGenerator(impl: Secp256k1NostrKeyGenerator): NostrKeyGenerator
}
