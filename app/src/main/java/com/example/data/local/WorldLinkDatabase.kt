package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContactEntity::class,
        CallRecordEntity::class,
        BlockedUserEntity::class,
        NearbyMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WorldLinkDatabase : RoomDatabase() {
    abstract fun dao(): WorldLinkDao

    companion object {
        @Volatile
        private var instance: WorldLinkDatabase? = null

        fun getInstance(context: Context): WorldLinkDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorldLinkDatabase::class.java,
                    "worldlink.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also {
                    instance = it
                }
            }
        }
    }
}
