package com.example.prestamo_lab_ctma.data.local.dao

import androidx.room.*
import com.example.prestamo_lab_ctma.data.local.entities.SolicitudEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SolicitudDao {
    @Query("SELECT * FROM solicitudes ORDER BY id DESC")
    fun obtenerTodas(): Flow<List<SolicitudEntity>>

    @Query("SELECT * FROM solicitudes WHERE id = :id")
    suspend fun obtenerPorId(id: Int): SolicitudEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarSolicitud(solicitud: SolicitudEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarSolicitudes(solicitudes: List<SolicitudEntity>)

    @Update
    suspend fun actualizarSolicitud(solicitud: SolicitudEntity)

    @Query("UPDATE solicitudes SET unreadCount = :unreadCount WHERE id = :prestamoId")
    suspend fun actualizarUnreadCount(prestamoId: Int, unreadCount: Int)
}
