package com.example.prestamo_lab_ctma.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.prestamo_lab_ctma.model.EstadoSolicitud

@Entity(tableName = "solicitudes")
data class SolicitudEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val equipoId: Int,
    val ambienteDestino: String,
    val proposito: String,
    val duracionHoras: Int = 4,
    val estado: EstadoSolicitud = EstadoSolicitud.ACTIVO,
    val photoPath: String? = null,
    val codigoCertificado: String = "",
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
    val chatSoloLectura: Boolean = false,
    val unreadCount: Int = 0
)
