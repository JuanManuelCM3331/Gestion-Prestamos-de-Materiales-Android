package com.example.prestamo_lab_ctma.ui.viewmodel

import com.example.prestamo_lab_ctma.BuildConfig
import com.example.prestamo_lab_ctma.model.*

data class PrestamoUiState(
    val usuarioActual: Usuario? = null,
    val estaAutenticado: Boolean = false,
    val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    val todosLosEquipos: List<Equipo> = emptyList(),
    val equipos: List<Equipo> = emptyList(),
    val equiposEliminados: List<Equipo> = emptyList(),
    val solicitudes: List<SolicitudPrestamo> = emptyList(),
    val admins: List<Usuario> = emptyList(),
    val auditoriaLogs: List<AuditoriaLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val filterCategory: String? = null,
    val mensaje: String? = null,
    val avisoTiempoReal: String? = null,
    val guardando: Boolean = false,
    val operacionExitosa: Boolean = false,
    val ultimoPrestamoCreadoId: Int? = null
) {
    val totalMensajesNoLeidos: Int
        get() = solicitudes.sumOf { it.unreadCount }
}

data class FormularioSolicitudState(
    val cantidad: String = "1",
    val fechaDevolucionEstimada: String = "",
    val ambienteDestino: String = "",
    val proposito: String = "",
    val duracionHoras: String = "4",
    val nombreSolicitante: String = "",
    val documentoSolicitante: String = "",
    val photoPath: String? = null,
    val errorCantidad: String? = null,
    val errorFecha: String? = null,
    val errorAmbiente: String? = null,
    val errorProposito: String? = null,
    val errorDuracion: String? = null,
    val errorSolicitante: String? = null,
    val puedeGuardar: Boolean = false
)
