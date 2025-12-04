package kth.nova.overloadalert.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Run::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun runDao(): RunDao
}