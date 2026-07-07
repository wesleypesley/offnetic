package com.offnetic.di

import com.offnetic.data.repository.ContactRepository
import com.offnetic.data.repository.ContactRepositoryImpl
import com.offnetic.data.repository.IdentityRepository
import com.offnetic.data.repository.IdentityRepositoryImpl
import com.offnetic.data.repository.MessageRepository
import com.offnetic.data.repository.MessageRepositoryImpl
import com.offnetic.data.repository.ProfileRepository
import com.offnetic.data.repository.ProfileRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideContactRepository(impl: ContactRepositoryImpl): ContactRepository = impl

    @Provides
    @Singleton
    fun provideMessageRepository(impl: MessageRepositoryImpl): MessageRepository = impl

    @Provides
    @Singleton
    fun provideIdentityRepository(impl: IdentityRepositoryImpl): IdentityRepository = impl

    @Provides
    @Singleton
    fun provideProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository = impl
}
