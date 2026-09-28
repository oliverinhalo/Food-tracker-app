package dev.foodtracker.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.foodtracker.core.database.FoodTrackerDatabase
import dev.foodtracker.core.database.Migrations
import dev.foodtracker.core.database.dao.DiaryDao
import dev.foodtracker.core.database.dao.FoodDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): FoodTrackerDatabase =
        Room.databaseBuilder(context, FoodTrackerDatabase::class.java, FoodTrackerDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun providesFoodDao(database: FoodTrackerDatabase): FoodDao = database.foodDao()

    @Provides
    fun providesDiaryDao(database: FoodTrackerDatabase): DiaryDao = database.diaryDao()
}
