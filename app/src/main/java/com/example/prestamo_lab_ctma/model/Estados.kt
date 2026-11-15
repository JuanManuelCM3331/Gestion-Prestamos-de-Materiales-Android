package com.example.prestamo_lab_ctma.model

enum class RolUsuario {
    ADMIN_EQUIPOS,
    USUARIO
}

enum class CategoriaEquipo {
    ELECTRONICA,
    MULTIMEDIA,
    INFORMATICA,
    HERRAMIENTAS,
    OTRO
}

enum class EstadoEquipo {
    DISPONIBLE,
    RESERVADO,
    PRESTADO
}

enum class EstadoSolicitud {
    ACTIVO,
    ENTREGADO,
    CANCELADO,
    // Valores preservados por compatibilidad con migraciones y pruebas previas
    SOLICITADA,
    APROBADA,
    ENTREGADA,
    DEVUELTA,
    CANCELADA,
    RECHAZADA;

    fun esActivo(): Boolean = this == ACTIVO || this == SOLICITADA || this == APROBADA
    fun esCancelado(): Boolean = this == CANCELADO || this == CANCELADA || this == RECHAZADA
    fun esEntregado(): Boolean = this == ENTREGADO || this == ENTREGADA || this == DEVUELTA

    fun etiquetaEspanol(): String = when {
        esActivo() -> "ACTIVO"
        esEntregado() -> "ENTREGADO"
        esCancelado() -> "CANCELADO"
        else -> name
    }
}

enum class TipoMensajeChat {
    TEXTO,
    IMAGEN,
    ARCHIVO,
    AUDIO
}
