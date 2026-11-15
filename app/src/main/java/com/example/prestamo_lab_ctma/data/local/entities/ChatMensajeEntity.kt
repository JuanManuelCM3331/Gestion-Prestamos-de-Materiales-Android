package com.example.prestamo_lab_ctma.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.prestamo_lab_ctma.model.RolUsuario
import com.example.prestamo_lab_ctma.model.TipoMensajeChat

@Entity(
    tableName = "chat_mensajes",
    indices = [Index(value = ["prestamoId"])]
)
data class ChatMensajeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val chatId: Int = 0,
    val prestamoId: Int,
    val remitenteId: Int,
    val remitenteNombre: String,
    val remitenteRol: RolUsuario,
    val tipo: TipoMensajeChat = TipoMensajeChat.TEXTO,
    val contenido: String,
    val archivoUrl: String? = null,
    val archivoNombre: String? = null,
    val archivoMime: String? = null,
    val archivoTamano: Int? = null,
    val duracionSeg: Int? = null,
    val createdAt: String = ""
)
