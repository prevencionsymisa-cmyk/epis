package com.episcan.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EpiEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun epiDao(): EpiDao

    companion object {
        /** Añade subcategoría y ficha técnica sin tocar los EPIs ya registrados. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE epis ADD COLUMN subcategoria TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE epis ADD COLUMN fichaTecnica TEXT NOT NULL DEFAULT ''")
            }
        }

        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "epis.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
