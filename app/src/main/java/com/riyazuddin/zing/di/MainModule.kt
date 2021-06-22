package com.riyazuddin.zing.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.riyazuddin.zing.repositories.abstraction.MainRepository
import com.riyazuddin.zing.repositories.implementation.DefaultMainRepository
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
    fun provideMainRepository(auth: FirebaseAuth, firestore: FirebaseFirestore, database: FirebaseDatabase) =
        DefaultMainRepository(auth, firestore, database) as MainRepository
}