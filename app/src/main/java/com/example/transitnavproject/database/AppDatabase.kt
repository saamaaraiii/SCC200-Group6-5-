package com.example.transitnavproject.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.transitnavproject.models.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [StationEntity::class, RouteSegmentEntity::class, BookingEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun routeSegmentDao(): RouteSegmentDao
    abstract fun bookingDao(): BookingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "transit_nav_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.stationDao(), database.routeSegmentDao())
                }
            }
        }

        suspend fun populateDatabase(stationDao: StationDao, routeSegmentDao: RouteSegmentDao) {
            // Seed data matching the iOS app
            val stations = listOf(
                StationEntity(1, "London Euston", "EUS", 51.5282, -0.1337),
                StationEntity(2, "Birmingham New Street", "BHM", 52.4778, -1.8980),
                StationEntity(3, "Manchester Piccadilly", "MAN", 53.4770, -2.2301),
                StationEntity(4, "Liverpool Lime Street", "LIV", 53.4075, -2.9988),
                StationEntity(5, "Leeds", "LDS", 53.7944, -1.5473),
                StationEntity(6, "Sheffield", "SHF", 53.3781, -1.4620),
                StationEntity(7, "York", "YRK", 53.9581, -1.0916),
                StationEntity(8, "Newcastle", "NCL", 54.9683, -1.6174),
                StationEntity(9, "Edinburgh Waverley", "EDB", 55.9521, -3.1895),
                StationEntity(10, "Glasgow Central", "GLC", 55.8599, -4.2572)
            )
            stationDao.insertAll(stations)

            val segments = listOf(
                RouteSegmentEntity(1, 1, 2, 170.0, 82),
                RouteSegmentEntity(2, 2, 1, 170.0, 82),
                RouteSegmentEntity(3, 2, 3, 120.0, 88),
                RouteSegmentEntity(4, 3, 2, 120.0, 88),
                RouteSegmentEntity(5, 3, 4, 50.0, 35),
                RouteSegmentEntity(6, 4, 3, 50.0, 35),
                RouteSegmentEntity(7, 3, 6, 60.0, 48),
                RouteSegmentEntity(8, 6, 3, 60.0, 48),
                RouteSegmentEntity(9, 6, 5, 40.0, 35),
                RouteSegmentEntity(10, 5, 6, 40.0, 35),
                RouteSegmentEntity(11, 5, 7, 45.0, 25),
                RouteSegmentEntity(12, 7, 5, 45.0, 25),
                RouteSegmentEntity(13, 7, 8, 80.0, 55),
                RouteSegmentEntity(14, 8, 7, 80.0, 55),
                RouteSegmentEntity(15, 8, 9, 160.0, 90),
                RouteSegmentEntity(16, 9, 8, 160.0, 90),
                RouteSegmentEntity(17, 9, 10, 75.0, 50),
                RouteSegmentEntity(18, 10, 9, 75.0, 50),
                RouteSegmentEntity(19, 2, 6, 95.0, 58),
                RouteSegmentEntity(20, 6, 2, 95.0, 58),
                RouteSegmentEntity(21, 6, 7, 55.0, 38),
                RouteSegmentEntity(22, 7, 6, 55.0, 38)
            )
            routeSegmentDao.insertAll(segments)
        }
    }
}
