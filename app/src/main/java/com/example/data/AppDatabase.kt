package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ScanReport::class, ScanFeedback::class, CommunityComment::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanReportDao(): ScanReportDao
    abstract fun scanFeedbackDao(): ScanFeedbackDao
    abstract fun communityCommentDao(): CommunityCommentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "object_scanner_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
