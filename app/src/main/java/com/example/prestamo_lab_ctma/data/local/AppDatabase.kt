package com.example.prestamo_lab_ctma.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.prestamo_lab_ctma.data.local.dao.ChatDao
import com.example.prestamo_lab_ctma.data.local.dao.EquipoDao
import com.example.prestamo_lab_ctma.data.local.dao.SolicitudDao
import com.example.prestamo_lab_ctma.data.local.entities.ChatMensajeEntity
import com.example.prestamo_lab_ctma.data.local.entities.EquipoEntity
import com.example.prestamo_lab_ctma.data.local.entities.SolicitudEntity

@Database(
    entities = [
        EquipoEntity::class,
        SolicitudEntity::class,
        ChatMensajeEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun equipoDao(): EquipoDao
    abstract fun solicitudDao(): SolicitudDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migración segura v1 -> v2 sin pérdida de datos existentes:
         * Conserva todos los equipos y solicitudes locales creados en la versión 1.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Ampliar tabla equipos
                db.execSQL("ALTER TABLE equipos ADD COLUMN descripcion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE equipos ADD COLUMN imagenUrl TEXT")
                db.execSQL("ALTER TABLE equipos ADD COLUMN stockTotal INTEGER NOT NULL DEFAULT 5")
                db.execSQL(
                    "ALTER TABLE equipos ADD COLUMN stockDisponible INTEGER NOT NULL DEFAULT 5"
                )
                db.execSQL("ALTER TABLE equipos ADD COLUMN eliminado INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE equipos ADD COLUMN eliminadoAt TEXT")
                db.execSQL("ALTER TABLE equipos ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

                // Ajustar stockDisponible de acuerdo al estado previo
                db.execSQL(
                    "UPDATE equipos SET stockDisponible = CASE WHEN estado = 'DISPONIBLE' THEN 5 ELSE 0 END"
                )

                // 2. Ampliar tabla solicitudes preservando registros
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN codigoCertificado TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN usuarioId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN usuarioCorreo TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN equipoNombre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN equipoCategoria TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN cantidad INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN nombreSolicitante TEXT NOT NULL DEFAULT 'Aprendiz CTMA'")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN documentoSolicitante TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN fechaPrestamo TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN fechaDevolucionEstimada TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN fechaCierre TEXT")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN gestionadoPorNombre TEXT")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN chatSoloLectura INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN unreadCount INTEGER NOT NULL DEFAULT 0")

                // Normalizar estados antiguos a ACTIVO / ENTREGADO / CANCELADO
                db.execSQL(
                    """
                    UPDATE solicitudes
                    SET estado = CASE
                        WHEN estado IN ('SOLICITADA', 'APROBADA') THEN 'ACTIVO'
                        WHEN estado IN ('ENTREGADA', 'DEVUELTA') THEN 'ENTREGADO'
                        WHEN estado IN ('CANCELADA', 'RECHAZADA') THEN 'CANCELADO'
                        ELSE estado
                    END
                    """
                )

                // 3. Crear tabla de caché de mensajes de chat
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_mensajes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        chatId INTEGER NOT NULL DEFAULT 0,
                        prestamoId INTEGER NOT NULL,
                        remitenteId INTEGER NOT NULL,
                        remitenteNombre TEXT NOT NULL,
                        remitenteRol TEXT NOT NULL,
                        tipo TEXT NOT NULL DEFAULT 'TEXTO',
                        contenido TEXT NOT NULL,
                        archivoUrl TEXT,
                        archivoNombre TEXT,
                        archivoMime TEXT,
                        archivoTamano INTEGER,
                        duracionSeg INTEGER,
                        createdAt TEXT NOT NULL DEFAULT ''
                    )
                    """
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_chat_mensajes_prestamoId ON chat_mensajes(prestamoId)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "prestamo_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
