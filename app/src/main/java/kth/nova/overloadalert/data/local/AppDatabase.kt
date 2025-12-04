package kth.nova.overloadalert.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Run::class], version = 1, exportSchema = false)
@TypeConverters(kth.nova.overloadalert.data.local.TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
}