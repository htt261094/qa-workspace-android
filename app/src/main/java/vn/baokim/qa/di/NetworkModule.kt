package vn.baokim.qa.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create
import vn.baokim.qa.BuildConfig
import vn.baokim.qa.data.net.AuthInterceptor
import vn.baokim.qa.data.net.PatRedactingLogger
import vn.baokim.qa.data.pat.PatApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor) // D2 hướng C: Bearer session token (no cookie jar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply { if (BuildConfig.DEBUG) addInterceptor(PatRedactingLogger.create()) }
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun providePatApi(retrofit: Retrofit): PatApi = retrofit.create()
}
