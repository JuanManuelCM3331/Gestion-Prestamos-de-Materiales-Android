package com.example.prestamo_lab_ctma.data.local.dao

import androidx.room.*
import com.example.prestamo_lab_ctma.data.local.entities.ChatMensajeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_mensajes WHERE prestamoId = :prestamoId ORDER BY id ASC")
    fun observarMensajesPorPrestamo(prestamoId: Int): Flow<List<ChatMensajeEntity>>

    @Query("SELECT * FROM chat_mensajes WHERE prestamoId = :prestamoId ORDER BY id ASC")
    suspend fun obtenerMensajesPorPrestamo(prestamoId: Int): List<ChatMensajeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMensajes(mensajes: List<ChatMensajeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMensaje(mensaje: ChatMensajeEntity): Long
}
