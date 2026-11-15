package com.example.prestamo_lab_ctma.model

data class Usuario(
    val id: Int,
    val nombre: String,
    val correo: String,
    val rol: RolUsuario,
    val passwordTemporal: Boolean = false,
    val creadoPorNombre: String? = null,
    val createdAt: String? = null
) {
    val esAdmin: Boolean
        get() = rol == RolUsuario.ADMIN_EQUIPOS
}

data class ChatMensaje(
    val id: Int,
    val chatId: Int,
    val prestamoId: Int,
    val remitenteId: Int,
    val remitenteNombre: String,
    val remitenteRol: RolUsuario,
    val tipo: TipoMensajeChat,
    val contenido: String,
    val archivoUrl: String? = null,
    val archivoNombre: String? = null,
    val archivoMime: String? = null,
    val archivoTamano: Int? = null,
    val duracionSeg: Int? = null,
    val createdAt: String = ""
)

data class AuditoriaLog(
    val id: Int,
    val usuarioId: Int?,
    val usuarioNombre: String,
    val usuarioRol: String,
    val accion: String,
    val entidad: String,
    val entidadId: Int?,
    val detalle: String?,
    val createdAt: String
)
