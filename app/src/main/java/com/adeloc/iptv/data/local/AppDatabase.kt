package com.adeloc.iptv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.adeloc.iptv.data.local.dao.*
import com.adeloc.iptv.data.local.entity.*

@Database(
    entities = [
        PlaylistProfile::class,
        StreamCategory::class,
        LiveChannel::class,
        EpgProgramEntity::class,
        StreamEntity::class,
        FavoriteEntity::class,
        RecentEntity::class
    ],
    version = 21, // Bumped version to trigger migration
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun streamDao(): StreamDao
    abstract fun epgDao(): EpgDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentDao(): RecentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "adeloc_iptv_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
