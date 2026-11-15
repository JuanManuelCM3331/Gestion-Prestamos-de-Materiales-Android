package com.example.prestamo_lab_ctma.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.prestamo_lab_ctma.model.CategoriaEquipo
import com.example.prestamo_lab_ctma.model.EstadoEquipo

@Entity(tableName = "equipos")
data class EquipoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
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
)
