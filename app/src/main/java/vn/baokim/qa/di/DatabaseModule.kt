package vn.baokim.qa.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import vn.baokim.qa.data.local.AppDatabase
import vn.baokim.qa.data.local.MyWorkDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "qa-workspace.db")
            .fallbackToDestructiveMigration() // cache-only DB — safe to rebuild on schema bump
            .build()

    @Provides
    fun provideMyWorkDao(db: AppDatabase): MyWorkDao = db.myWorkDao()
}
