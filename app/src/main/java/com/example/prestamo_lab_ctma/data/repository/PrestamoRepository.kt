package com.example.prestamo_lab_ctma.data.repository

import com.example.prestamo_lab_ctma.model.*
import kotlinx.coroutines.flow.Flow
import java.io.File

interface PrestamoRepository {
    // Equipos y Catálogo
    fun obtenerEquipos(): Flow<List<Equipo>>
    suspend fun obtenerEquipo(id: Int): Equipo?
    suspend fun inicializarEquipos()
    suspend fun sincronizarConServidor(incluirEliminados: Boolean = false)

    // CRUD Admin de Catálogo
    suspend fun crearEquipoCatalogo(
        nombre: String,
        categoria: CategoriaEquipo,
        stockTotal: Int,
        descripcion: String,
        imagenFile: File? = null
    ): Result<Equipo>

    suspend fun editarEquipoCatalogo(
        equipoId: Int,
        nombre: String,
        categoria: CategoriaEquipo,
        stockTotal: Int,
        descripcion: String,
        imagenFile: File? = null
    ): Result<Equipo>

    suspend fun eliminarEquipoLogico(equipoId: Int): Result<Unit>
    suspend fun restaurarEquipo(equipoId: Int): Result<Unit>
    suspend fun eliminarEquipoPermanente(equipoId: Int): Result<Unit>

    // Préstamos
    fun obtenerSolicitudes(): Flow<List<SolicitudPrestamo>>
    suspend fun obtenerSolicitud(id: Int): SolicitudPrestamo?
    suspend fun crearSolicitud(solicitud: SolicitudPrestamo): Result<Unit>
    suspend fun cancelarSolicitud(id: Int): Result<Unit>
    suspend fun entregarSolicitud(id: Int): Result<Unit>

    // Auth y Administración
    suspend fun login(correo: String, password: String): Result<Usuario>
    suspend fun registrarUsuario(nombre: String, correo: String, password: String): Result<Usuario>
    suspend fun crearAdministrador(nombre: String, correo: String, passwordTemporal: String): Result<Usuario>
    suspend fun obtenerAdministradores(): Result<List<Usuario>>
    suspend fun obtenerAuditoria(): Result<List<AuditoriaLog>>

    // Chat por Préstamo
    fun observarMensajesChat(prestamoId: Int): Flow<List<ChatMensaje>>
    suspend fun sincronizarChat(prestamoId: Int): Result<Boolean>
    suspend fun enviarMensajeTexto(prestamoId: Int, contenido: String): Result<ChatMensaje>
    suspend fun enviarMensajeAdjunto(
        prestamoId: Int,
        tipo: TipoMensajeChat,
        contenido: String,
        archivo: File,
        mimeType: String,
        duracionSeg: Int? = null
    ): Result<ChatMensaje>
    suspend fun marcarChatLeido(prestamoId: Int)
}
