package com.example.prestamo_lab_ctma.data.remote.dto

import com.example.prestamo_lab_ctma.data.local.entities.ChatMensajeEntity
import com.example.prestamo_lab_ctma.data.local.entities.EquipoEntity
import com.example.prestamo_lab_ctma.data.local.entities.SolicitudEntity
import com.example.prestamo_lab_ctma.model.*

data class LoginRequestDto(
    val correo: String,
    val password: String
)

data class RegisterRequestDto(
    val nombre: String,
    val correo: String,
    val password: String
)

data class CrearAdminRequestDto(
    val nombre: String,
    val correo: String,
    val passwordTemporal: String
)

data class UsuarioDto(
    val id: Int,
    val nombre: String,
    val correo: String,
    val rol: String,
    val passwordTemporal: Boolean = false,
    val creadoPorNombre: String? = null,
    val createdAt: String? = null
) {
    fun toDomain(): Usuario = Usuario(
        id = id,
        nombre = nombre,
        correo = correo,
        rol = runCatching { RolUsuario.valueOf(rol) }.getOrDefault(RolUsuario.USUARIO),
        passwordTemporal = passwordTemporal,
        creadoPorNombre = creadoPorNombre,
        createdAt = createdAt
    )
}

data class AuthResponseDto(
    val mensaje: String? = null,
    val token: String,
    val usuario: UsuarioDto
)

data class CrearAdminResponseDto(
    val mensaje: String? = null,
    val admin: UsuarioDto
)

data class AdminsListResponseDto(
    val admins: List<UsuarioDto> = emptyList()
)

data class EquipoDto(
    val id: Int,
    val nombre: String,
    val categoria: String,
    val estado: String = "DISPONIBLE",
    val descripcion: String = "",
    val imagenUrl: String? = null,
    val stockTotal: Int = 1,
    val stockDisponible: Int = 1,
    val eliminado: Boolean = false,
    val eliminadoAt: String? = null,
    val updatedAt: Long = 0L
) {
    fun toEntity(baseUrl: String): EquipoEntity {
        val fullImg = resolveMediaUrl(baseUrl, imagenUrl)
        val cat = runCatching { CategoriaEquipo.valueOf(categoria) }.getOrDefault(CategoriaEquipo.OTRO)
        val est = if (stockDisponible > 0) EstadoEquipo.DISPONIBLE else EstadoEquipo.PRESTADO
        return EquipoEntity(
            id = id,
            nombre = nombre,
            categoria = cat,
            estado = est,
            descripcion = descripcion,
            imagenUrl = fullImg,
            stockTotal = stockTotal,
            stockDisponible = stockDisponible,
            eliminado = eliminado,
            eliminadoAt = eliminadoAt,
            updatedAt = updatedAt
        )
    }
}

data class EquiposListResponseDto(
    val equipos: List<EquipoDto> = emptyList()
)

data class EquipoSingleResponseDto(
    val mensaje: String? = null,
    val equipo: EquipoDto
)

data class CrearPrestamoRequestDto(
    val equipoId: Int,
    val cantidad: Int,
    val fechaDevolucionEstimada: String,
    val motivo: String,
    val ambienteDestino: String,
    val nombreSolicitante: String,
    val documentoSolicitante: String
)

data class PrestamoDto(
    val id: Int,
    val codigoCertificado: String = "",
    val usuarioId: Int = 0,
    val usuarioCorreo: String = "",
    val equipoId: Int = 0,
    val equipoNombre: String = "",
    val equipoCategoria: String = "OTRO",
    val cantidad: Int = 1,
    val nombreSolicitante: String = "",
    val documentoSolicitante: String = "",
    val ambienteDestino: String = "",
    val proposito: String = "",
    val motivo: String = "",
    val fechaPrestamo: String = "",
    val fechaDevolucionEstimada: String = "",
    val fechaCierre: String? = null,
    val estado: String = "ACTIVO",
    val photoPath: String? = null,
    val evidenciaUrl: String? = null,
    val gestionadoPorNombre: String? = null,
    val chatSoloLectura: Boolean = false,
    val unreadCount: Int = 0
) {
    fun toEntity(baseUrl: String): SolicitudEntity {
        val est = runCatching { EstadoSolicitud.valueOf(estado) }.getOrDefault(EstadoSolicitud.ACTIVO)
        val media = resolveMediaUrl(baseUrl, evidenciaUrl ?: photoPath)
        return SolicitudEntity(
            id = id,
            equipoId = equipoId,
            ambienteDestino = ambienteDestino,
            proposito = motivo.ifBlank { proposito },
            duracionHoras = 4,
            estado = est,
            photoPath = media,
            codigoCertificado = codigoCertificado,
            usuarioId = usuarioId,
            usuarioCorreo = usuarioCorreo,
            equipoNombre = equipoNombre,
            equipoCategoria = equipoCategoria,
            cantidad = cantidad,
            nombreSolicitante = nombreSolicitante,
            documentoSolicitante = documentoSolicitante,
            fechaPrestamo = fechaPrestamo,
            fechaDevolucionEstimada = fechaDevolucionEstimada,
            fechaCierre = fechaCierre,
            gestionadoPorNombre = gestionadoPorNombre,
            chatSoloLectura = chatSoloLectura || !est.esActivo(),
            unreadCount = unreadCount
        )
    }
}

data class PrestamosListResponseDto(
    val prestamos: List<PrestamoDto> = emptyList()
)

data class PrestamoSingleResponseDto(
    val mensaje: String? = null,
    val prestamo: PrestamoDto,
    val stockDisponible: Int? = null
)

data class ChatMensajeDto(
    val id: Int,
    val chatId: Int = 0,
    val prestamoId: Int,
    val remitenteId: Int,
    val remitenteNombre: String,
    val remitenteRol: String,
    val tipo: String = "TEXTO",
    val contenido: String,
    val archivoUrl: String? = null,
    val archivoNombre: String? = null,
    val archivoMime: String? = null,
    val archivoTamano: Int? = null,
    val duracionSeg: Int? = null,
    val createdAt: String = ""
) {
    fun toEntity(baseUrl: String): ChatMensajeEntity {
        return ChatMensajeEntity(
            id = id,
            chatId = chatId,
            prestamoId = prestamoId,
            remitenteId = remitenteId,
            remitenteNombre = remitenteNombre,
            remitenteRol = runCatching { RolUsuario.valueOf(remitenteRol) }.getOrDefault(RolUsuario.USUARIO),
            tipo = runCatching { TipoMensajeChat.valueOf(tipo) }.getOrDefault(TipoMensajeChat.TEXTO),
            contenido = contenido,
            archivoUrl = resolveMediaUrl(baseUrl, archivoUrl),
            archivoNombre = archivoNombre,
            archivoMime = archivoMime,
            archivoTamano = archivoTamano,
            duracionSeg = duracionSeg,
            createdAt = createdAt
        )
    }
}

data class ChatHistorialResponseDto(
    val chatId: Int,
    val prestamoId: Int,
    val prestamoEstado: String,
    val soloLectura: Boolean,
    val ultimoMensajeLeidoId: Int,
    val unreadCount: Int,
    val mensajes: List<ChatMensajeDto> = emptyList()
)

data class EnviarMensajeTextoRequestDto(
    val contenido: String,
    val tipo: String = "TEXTO"
)

data class EnviarMensajeResponseDto(
    val mensaje: String? = null,
    val chatMensaje: ChatMensajeDto
)

data class AuditoriaLogDto(
    val id: Int,
    val usuarioId: Int? = null,
    val usuarioNombre: String = "",
    val usuarioRol: String = "",
    val accion: String = "",
    val entidad: String = "",
    val entidadId: Int? = null,
    val detalle: String? = null,
    val createdAt: String = ""
) {
    fun toDomain() = AuditoriaLog(
        id = id,
        usuarioId = usuarioId,
        usuarioNombre = usuarioNombre,
        usuarioRol = usuarioRol,
        accion = accion,
        entidad = entidad,
        entidadId = entidadId,
        detalle = detalle,
        createdAt = createdAt
    )
}

data class AuditoriaListResponseDto(
    val logs: List<AuditoriaLogDto> = emptyList()
)

fun resolveMediaUrl(baseUrl: String, pathOrUrl: String?): String? {
    if (pathOrUrl.isNullOrBlank()) return null
    if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("/data/") || pathOrUrl.startsWith("file:")) {
        return pathOrUrl
    }
    val cleanBase = baseUrl.trimEnd('/')
    val cleanPath = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
    return "$cleanBase$cleanPath"
}
