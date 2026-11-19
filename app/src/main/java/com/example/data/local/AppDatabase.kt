package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StreamingAccountEntity::class,
        ProfileSaleEntity::class,
        ClientEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun streamingAccountDao(): StreamingAccountDao
    abstract fun profileSaleDao(): ProfileSaleDao
    abstract fun clientDao(): ClientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "control_streaming_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
