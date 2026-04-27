package com.sozolab.zampa.di

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.sozolab.zampa.data.CurrencyService
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.LocalizationManager
import com.sozolab.zampa.data.LocationService
import com.sozolab.zampa.data.ThemeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideFirebaseService(): FirebaseService = FirebaseService()

    @Provides
    @Singleton
    fun provideLocationService(@ApplicationContext context: Context): LocationService = LocationService(context)

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideCurrencyService(db: FirebaseFirestore): CurrencyService = CurrencyService(db)

    @Provides
    @Singleton
    fun provideLocalizationManager(@ApplicationContext context: Context): LocalizationManager = LocalizationManager(context)

    @Provides
    @Singleton
    fun provideThemeManager(@ApplicationContext context: Context): ThemeManager = ThemeManager(context)
}
