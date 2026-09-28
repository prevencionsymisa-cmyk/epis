package com.episcan.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EpiEntity::class], version = 3, exportSchema = false)
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

        /**
         * Prepara la sincronización con el servidor: cada ficha existente recibe un UUID único y queda
         * marcada como pendiente de enviar (pendiente = 1, revision = 0). No se pierde ningún dato.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE epis ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE epis ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE epis ADD COLUMN actualizadoEn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE epis ADD COLUMN pendiente INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE epis ADD COLUMN eliminado INTEGER NOT NULL DEFAULT 0")
                // UUID versión 4 generado por SQLite (randomblob se evalúa fila a fila)
                db.execSQL(
                    """
                    UPDATE epis SET actualizadoEn = creadoEn, uid =
                        lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' ||
                        substr(lower(hex(randomblob(2))), 2) || '-' ||
                        substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' ||
                        lower(hex(randomblob(6)))
                    """.trimIndent(),
                )
            }
        }

        fun crear(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "epis.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
