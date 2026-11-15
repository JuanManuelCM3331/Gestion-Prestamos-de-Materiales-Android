package com.example.prestamo_lab_ctma.model

data class SolicitudPrestamo(
    val id: Int,
    val equipoId: Int,
    val ambienteDestino: String,
    val proposito: String,
    val duracionHoras: Int = 4,
    val estado: EstadoSolicitud = EstadoSolicitud.ACTIVO,
    val photoPath: String? = null,
    val codigoCertificado: String = "CTMA-2026-${id.toString().padStart(5, '0')}",
    val usuarioId: Int = 0,
    val usuarioCorreo: String = "",
    val equipoNombre: String = "",
    val equipoCategoria: String = "",
    val cantidad: Int = 1,
    val nombreSolicitante: String = "",
    val documentoSolicitante: String = "",
    val fechaPrestamo: String = "",
    val fechaDevolucionEstimada: String = "",
    val fechaCierre: String? = null,
    val gestionadoPorNombre: String? = null,
    val chatSoloLectura: Boolean = !estado.esActivo(),
    val unreadCount: Int = 0
)
