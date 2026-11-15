package com.example.prestamo_lab_ctma.di

import android.content.Context
import com.example.prestamo_lab_ctma.BuildConfig
import com.example.prestamo_lab_ctma.data.local.AppDatabase
import com.example.prestamo_lab_ctma.data.local.UserPreferencesRepository
import com.example.prestamo_lab_ctma.data.local.dao.ChatDao
import com.example.prestamo_lab_ctma.data.local.dao.EquipoDao
import com.example.prestamo_lab_ctma.data.local.dao.SolicitudDao
import com.example.prestamo_lab_ctma.data.remote.ApiService
import com.example.prestamo_lab_ctma.data.repository.LocalPrestamoRepository
import com.example.prestamo_lab_ctma.data.repository.PrestamoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideEquipoDao(db: AppDatabase): EquipoDao = db.equipoDao()

    @Provides
    fun provideSolicitudDao(db: AppDatabase): SolicitudDao = db.solicitudDao()

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        @ApplicationContext context: Context
    ): UserPreferencesRepository {
        return UserPreferencesRepository(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        userPrefs: UserPreferencesRepository
    ): OkHttpClient {
        val authAndHostInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = runBlocking { userPrefs.obtenerTokenActual() }
            val configuredBase = runBlocking { userPrefs.obtenerBaseUrlActual() }

            var newUrl = original.url
            val targetBase = configuredBase.toHttpUrlOrNull()
            if (targetBase != null) {
                newUrl = original.url.newBuilder()
                    .scheme(targetBase.scheme)
                    .host(targetBase.host)
                    .port(targetBase.port)
                    .build()
            }

            val reqBuilder = original.newBuilder().url(newUrl)
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            chain.proceed(reqBuilder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(authAndHostInterceptor)
            .addInterceptor(logging)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePrestamoRepository(
        equipoDao: EquipoDao,
        solicitudDao: SolicitudDao,
        chatDao: ChatDao,
        apiService: ApiService,
        userPrefs: UserPreferencesRepository
    ): PrestamoRepository {
        return LocalPrestamoRepository(
            equipoDao = equipoDao,
            solicitudDao = solicitudDao,
            chatDao = chatDao,
            apiService = apiService,
            userPrefs = userPrefs
        )
    }
}
