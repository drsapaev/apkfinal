package com.aistudio.clinicsystem.di

import android.content.Context
import com.aistudio.clinicsystem.data.api.ApiService
import com.aistudio.clinicsystem.data.api.MobileApiService
import com.aistudio.clinicsystem.data.db.ClinicDatabase
import com.aistudio.clinicsystem.data.db.SyncLogDao
import com.aistudio.clinicsystem.data.realtime.RealtimeManager
import com.aistudio.clinicsystem.data.repository.AuthRepository
import com.aistudio.clinicsystem.data.repository.ClinicRepository
import com.aistudio.clinicsystem.data.session.SessionRepository
import com.aistudio.clinicsystem.utils.SessionManager
import com.aistudio.clinicsystem.utils.SessionManagerImpl
import com.aistudio.clinicsystem.utils.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Stage 2.6: Main Hilt module.
 *
 * Provides:
 *   - [SessionManager] (delegates to [TokenManager] → EncryptedSharedPreferences)
 *   - [ClinicDatabase] (SQLCipher-encrypted Room DB)
 *   - [SyncLogDao] (for NetworkMonitor injection)
 *   - [ClinicRepository] and [AuthRepository] (singleton — fixes H-6, PERF-2)
 *   - [SessionRepository] (singleton — closes M-5)
 *   - [ApiService] / [MobileApiService] (delegated to ApiClient)
 *
 * [RealtimeManager] is `@Inject constructor` + `@Singleton` on the class —
 * no `@Provides` needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager =
        SessionManagerImpl(context.applicationContext)

    @Provides
    @Singleton
    fun provideClinicDatabase(@ApplicationContext context: Context): ClinicDatabase =
        ClinicDatabase.getDatabase(context)

    @Provides
    fun provideSyncLogDao(db: ClinicDatabase): SyncLogDao = db.syncLogDao()

    @Provides
    @Singleton
    fun provideClinicRepository(
        database: ClinicDatabase,
        apiService: ApiService,
        mobileApiService: MobileApiService,
        moshi: com.squareup.moshi.Moshi,
    ): ClinicRepository = ClinicRepository(
        database = database,
        mobileApiService = mobileApiService,
        legacyApiService = apiService,
        moshi = moshi,
    )

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        database: ClinicDatabase,
        apiService: ApiService,
        mobileApiService: MobileApiService,
        sessionRepository: SessionRepository,
    ): AuthRepository = AuthRepository(
        context = context,
        database = database,
        mobileApiService = mobileApiService,
        apiService = apiService,
        sessionRepository = sessionRepository,
    )

    @Provides
    @Singleton
    fun provideApiService(apiClient: com.aistudio.clinicsystem.data.api.ApiClient): ApiService =
        apiClient.service

    @Provides
    @Singleton
    fun provideMobileApiService(apiClient: com.aistudio.clinicsystem.data.api.ApiClient): MobileApiService =
        apiClient.mobileService
}
