package com.example.prestamo_lab_ctma.model

data class Equipo(
    val id: Int,
    val nombre: String,
    val categoria: CategoriaEquipo,
    val estado: EstadoEquipo = EstadoEquipo.DISPONIBLE,
    val descripcion: String = "",
    val imagenUrl: String? = null,
    val stockTotal: Int = 1,
    val stockDisponible: Int = if (estado == EstadoEquipo.DISPONIBLE) 1 else 0,
    val eliminado: Boolean = false,
    val eliminadoAt: String? = null,
    val updatedAt: Long = 0L
) {
    val unidadesEnPrestamoActivo: Int
        get() = (stockTotal - stockDisponible).coerceAtLeast(0)

    val tieneExistencias: Boolean
        get() = !eliminado && stockDisponible > 0
}
