package com.riyazuddin.zing.di

import com.riyazuddin.zing.repositories.implementation.DefaultMainRepository
import com.riyazuddin.zing.repositories.abstraction.MainRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
object MainModule {

    @ActivityScoped
    @Provides
    fun provideMainRepository() = DefaultMainRepository() as MainRepository
}