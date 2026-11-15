package com.example.prestamo_lab_ctma.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prestamo_lab_ctma.data.local.UserPreferencesRepository
import com.example.prestamo_lab_ctma.data.remote.SocketManager
import com.example.prestamo_lab_ctma.data.repository.LocalPrestamoRepository
import com.example.prestamo_lab_ctma.data.repository.PrestamoRepository
import com.example.prestamo_lab_ctma.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PrestamoViewModel @Inject constructor(
    private val repository: PrestamoRepository,
    private val userPrefs: UserPreferencesRepository,
    private val socketManager: SocketManager
) : ViewModel() {

    // Constructor secundario para pruebas manuales sin SocketManager
    constructor(
        repository: PrestamoRepository,
        userPrefs: UserPreferencesRepository
    ) : this(repository, userPrefs, SocketManager())

    private val _uiState = MutableStateFlow(PrestamoUiState())
    val uiState: StateFlow<PrestamoUiState> = _uiState.asStateFlow()

    private val _formularioState = MutableStateFlow(
        FormularioSolicitudState(
            fechaDevolucionEstimada = defaultFechaDevolucion()
        )
    )
    val formularioState: StateFlow<FormularioSolicitudState> = _formularioState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var equipoVistoActualmenteId: Int? = null
    private var chatAbiertoActualmentePrestamoId: Int? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.inicializarEquipos()
                observarDatos()
                observarSesionYSocket()
            } catch (_: Exception) {
                _uiState.update { it.copy(error = "Fallo al cargar datos") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun observarDatos() {
        combine(
            repository.obtenerEquipos(),
            repository.obtenerSolicitudes(),
            userPrefs.categoryFilter,
            _searchQuery,
            userPrefs.sesionFlow
        ) { todosEquipos, todasSolicitudes, filter, query, sesion ->
            val activos = todosEquipos.filter { !it.eliminado }
            val eliminados = todosEquipos.filter { it.eliminado }

            val filtradosCategoria = if (filter == null) {
                activos
            } else {
                activos.filter { it.categoria.name == filter }
            }

            val filtradosBusqueda = if (query.isBlank()) {
                filtradosCategoria
            } else {
                val q = query.trim().lowercase()
                filtradosCategoria.filter {
                    it.nombre.lowercase().contains(q) || it.descripcion.lowercase().contains(q)
                }
            }

            val usuario = sesion.usuario
            val solicitudesVisibles = if (usuario == null || usuario.esAdmin) {
                todasSolicitudes
            } else {
                todasSolicitudes.filter { it.usuarioId == 0 || it.usuarioId == usuario.id }
            }

            _uiState.update {
                it.copy(
                    usuarioActual = usuario,
                    estaAutenticado = sesion.estaAutenticado,
                    apiBaseUrl = sesion.apiBaseUrl,
                    todosLosEquipos = todosEquipos,
                    equipos = filtradosBusqueda,
                    equiposEliminados = eliminados,
                    solicitudes = solicitudesVisibles,
                    filterCategory = filter,
                    searchQuery = query
                )
            }

            if (usuario != null && _formularioState.value.nombreSolicitante.isBlank()) {
                _formularioState.update { f -> f.copy(nombreSolicitante = usuario.nombre) }
            }
        }.launchIn(viewModelScope)
    }

    private fun observarSesionYSocket() {
        val localRepo = repository as? LocalPrestamoRepository

        userPrefs.sesionFlow.onEach { sesion ->
            socketManager.connect(sesion.apiBaseUrl, sesion.token)
            if (sesion.estaAutenticado) {
                repository.sincronizarConServidor(incluirEliminados = sesion.usuario?.esAdmin == true)
            }
        }.launchIn(viewModelScope)

        socketManager.catalogoEvents.onEach { event ->
            localRepo?.aplicarEventoCatalogo(event.accion, event.equipo)
            val eqId = event.equipo?.id
            if (eqId != null && eqId == equipoVistoActualmenteId) {
                val aviso = when (event.accion) {
                    "ELIMINADO", "BORRADO_PERMANENTE" ->
                        "⚠️ Atención: El equipo \"${event.equipo.nombre}\" acaba de ser retirado del catálogo por un administrador."
                    "EDITADO" ->
                        "ℹ️ La información o existencias de \"${event.equipo.nombre}\" se actualizaron en tiempo real."
                    else -> null
                }
                if (aviso != null) {
                    _uiState.update { it.copy(avisoTiempoReal = aviso) }
                }
            }
        }.launchIn(viewModelScope)

        socketManager.stockEvents.onEach { event ->
            localRepo?.aplicarEventoStock(event.equipoId, event.stockTotal, event.stockDisponible)
        }.launchIn(viewModelScope)

        socketManager.prestamoEvents.onEach { event ->
            localRepo?.aplicarEventoPrestamo(event.prestamo)
            val p = event.prestamo
            val msg = when (event.accion) {
                "ENTREGADO" -> "✅ Préstamo #${p.id} (${p.equipoNombre}) fue marcado como ENTREGADO."
                "CANCELADO" -> "ℹ️ Préstamo #${p.id} (${p.equipoNombre}) fue CANCELADO y el stock se restableció."
                else -> null
            }
            if (msg != null) {
                _uiState.update { it.copy(avisoTiempoReal = msg) }
            }
        }.launchIn(viewModelScope)

        socketManager.chatEvents.onEach { event ->
            val currentUserId = _uiState.value.usuarioActual?.id
            val estaEnEsteChat = chatAbiertoActualmentePrestamoId == event.prestamoId
            localRepo?.aplicarEventoMensajeChat(
                prestamoId = event.prestamoId,
                mensajeDto = event.mensaje,
                currentUserId = if (estaEnEsteChat) event.mensaje.remitenteId else currentUserId
            )
            if (estaEnEsteChat) {
                repository.marcarChatLeido(event.prestamoId)
            } else if (currentUserId != null && event.mensaje.remitenteId != currentUserId) {
                _uiState.update {
                    it.copy(
                        avisoTiempoReal = "💬 Nuevo mensaje de ${event.mensaje.remitenteNombre} en Préstamo #${event.prestamoId}: ${event.mensaje.contenido}"
                    )
                }
            }
        }.launchIn(viewModelScope)
    }

    // =========================================================================
    // Autenticación y Administración
    // =========================================================================
    fun login(correo: String, password: String) {
        if (correo.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(mensaje = "Ingresa tu correo y contraseña") }
            return
        }
        _uiState.update { it.copy(guardando = true, mensaje = null) }
        viewModelScope.launch {
            val result = repository.login(correo, password)
            _uiState.update {
                it.copy(
                    guardando = false,
                    mensaje = if (result.isSuccess) "Bienvenido, ${result.getOrNull()?.nombre}" else result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun registrarUsuario(nombre: String, correo: String, password: String) {
        if (nombre.trim().length < 3) {
            _uiState.update { it.copy(mensaje = "El nombre debe tener al menos 3 caracteres") }
            return
        }
        if (!correo.contains("@")) {
            _uiState.update { it.copy(mensaje = "Ingresa un correo electrónico válido") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(mensaje = "La contraseña debe tener mínimo 6 caracteres") }
            return
        }
        _uiState.update { it.copy(guardando = true, mensaje = null) }
        viewModelScope.launch {
            val result = repository.registrarUsuario(nombre, correo, password)
            _uiState.update {
                it.copy(
                    guardando = false,
                    mensaje = if (result.isSuccess) "Cuenta creada exitosamente" else result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun cerrarSesion() {
        viewModelScope.launch {
            socketManager.disconnect()
            userPrefs.cerrarSesion()
            _uiState.update { it.copy(mensaje = "Sesión cerrada") }
        }
    }

    fun actualizarUrlServidor(nuevaUrl: String) {
        if (nuevaUrl.isBlank()) return
        viewModelScope.launch {
            userPrefs.guardarApiBaseUrl(nuevaUrl.trim())
            repository.sincronizarConServidor(incluirEliminados = _uiState.value.usuarioActual?.esAdmin == true)
            _uiState.update { it.copy(mensaje = "Servidor actualizado a $nuevaUrl") }
        }
    }

    fun cargarPanelAdmin() {
        viewModelScope.launch {
            val adminsRes = repository.obtenerAdministradores()
            val logsRes = repository.obtenerAuditoria()
            _uiState.update {
                it.copy(
                    admins = adminsRes.getOrDefault(emptyList()),
                    auditoriaLogs = logsRes.getOrDefault(emptyList())
                )
            }
        }
    }

    fun crearNuevoAdmin(nombre: String, correo: String, passwordTemporal: String, onSuccess: () -> Unit = {}) {
        if (_uiState.value.usuarioActual?.esAdmin != true) {
            _uiState.update { it.copy(mensaje = "Acceso denegado: Se requiere rol ADMIN_EQUIPOS") }
            return
        }
        _uiState.update { it.copy(guardando = true, mensaje = null) }
        viewModelScope.launch {
            val res = repository.crearAdministrador(nombre, correo, passwordTemporal)
            _uiState.update {
                it.copy(
                    guardando = false,
                    mensaje = if (res.isSuccess) "Administrador creado exitosamente" else res.exceptionOrNull()?.message
                )
            }
            if (res.isSuccess) {
                cargarPanelAdmin()
                onSuccess()
            }
        }
    }

    // =========================================================================
    // Catálogo y Filtros
    // =========================================================================
    fun updateFilter(category: String?) {
        viewModelScope.launch {
            userPrefs.saveCategoryFilter(category)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refrescarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.sincronizarConServidor(incluirEliminados = _uiState.value.usuarioActual?.esAdmin == true)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun setEquipoVistoActualmente(equipoId: Int?) {
        val anterior = equipoVistoActualmenteId
        if (anterior != null && anterior != equipoId) {
            socketManager.leaveEquipoRoom(anterior)
        }
        equipoVistoActualmenteId = equipoId
        if (equipoId != null && equipoId > 0) {
            socketManager.joinEquipoRoom(equipoId)
        }
    }

    fun obtenerEquipoPorId(id: Int): Equipo? {
        return _uiState.value.todosLosEquipos.find { it.id == id }
            ?: _uiState.value.equipos.find { it.id == id }
    }

    fun obtenerSolicitudPorId(id: Int): SolicitudPrestamo? {
        return _uiState.value.solicitudes.find { it.id == id }
    }

    fun guardarEquipoCatalogo(
        equipoIdEdicion: Int?,
        nombre: String,
        categoria: CategoriaEquipo,
        stockTotalStr: String,
        descripcion: String,
        imagenPath: String?,
        onSuccess: () -> Unit
    ) {
        val stockNum = stockTotalStr.toIntOrNull()
        if (nombre.trim().length < 3) {
            _uiState.update { it.copy(mensaje = "El nombre del equipo debe tener al menos 3 caracteres.") }
            return
        }
        if (stockNum == null || stockNum < 0) {
            _uiState.update { it.copy(mensaje = "La cantidad de existencias debe ser un número entero mayor o igual a 0.") }
            return
        }
        if (descripcion.trim().length < 5) {
            _uiState.update { it.copy(mensaje = "La descripción o aclaración es obligatoria (mínimo 5 caracteres).") }
            return
        }

        val imgFile = imagenPath?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { File(it) }
        _uiState.update { it.copy(guardando = true, mensaje = null) }

        viewModelScope.launch {
            val result = if (equipoIdEdicion == null || equipoIdEdicion <= 0) {
                repository.crearEquipoCatalogo(nombre.trim(), categoria, stockNum, descripcion.trim(), imgFile)
            } else {
                repository.editarEquipoCatalogo(equipoIdEdicion, nombre.trim(), categoria, stockNum, descripcion.trim(), imgFile)
            }

            _uiState.update {
                it.copy(
                    guardando = false,
                    operacionExitosa = result.isSuccess,
                    mensaje = if (result.isSuccess) "Catálogo actualizado exitosamente" else result.exceptionOrNull()?.message
                )
            }
            if (result.isSuccess) {
                onSuccess()
            }
        }
    }

    fun eliminarEquipoLogico(equipoId: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.eliminarEquipoLogico(equipoId)
            _uiState.update {
                it.copy(
                    mensaje = if (res.isSuccess) "Equipo movido a la pestaña Eliminados" else res.exceptionOrNull()?.message
                )
            }
            if (res.isSuccess) onSuccess()
        }
    }

    fun restaurarEquipo(equipoId: Int) {
        viewModelScope.launch {
            val res = repository.restaurarEquipo(equipoId)
            _uiState.update {
                it.copy(
                    mensaje = if (res.isSuccess) "Equipo restaurado al catálogo activo" else res.exceptionOrNull()?.message
                )
            }
        }
    }

    fun eliminarEquipoPermanente(equipoId: Int) {
        viewModelScope.launch {
            val res = repository.eliminarEquipoPermanente(equipoId)
            _uiState.update {
                it.copy(
                    mensaje = if (res.isSuccess) "Equipo eliminado permanentemente" else res.exceptionOrNull()?.message
                )
            }
        }
    }

    // =========================================================================
    // Formulario y Gestión de Préstamos
    // =========================================================================
    fun onCantidadChange(nuevaCantidad: String, maxDisponible: Int = 999) {
        _formularioState.update { it.copy(cantidad = nuevaCantidad) }
        validarFormulario(maxDisponible)
    }

    fun onFechaDevolucionChange(nuevaFecha: String) {
        _formularioState.update { it.copy(fechaDevolucionEstimada = nuevaFecha) }
        validarFormulario()
    }

    fun onNombreSolicitanteChange(nuevoNombre: String) {
        _formularioState.update { it.copy(nombreSolicitante = nuevoNombre) }
        validarFormulario()
    }

    fun onDocumentoSolicitanteChange(nuevoDoc: String) {
        _formularioState.update { it.copy(documentoSolicitante = nuevoDoc) }
        validarFormulario()
    }

    fun onAmbienteChange(nuevoAmbiente: String) {
        _formularioState.update { it.copy(ambienteDestino = nuevoAmbiente) }
        validarFormulario()
    }

    fun onPropositoChange(nuevoProposito: String) {
        _formularioState.update { it.copy(proposito = nuevoProposito) }
        validarFormulario()
    }

    fun onDuracionChange(nuevaDuracion: String) {
        _formularioState.update { it.copy(duracionHoras = nuevaDuracion) }
        validarFormulario()
    }

    fun onPhotoCaptured(path: String) {
        _formularioState.update { it.copy(photoPath = path.ifBlank { null }) }
    }

    private fun validarFormulario(maxDisponible: Int = 999) {
        val state = _formularioState.value

        val cant = state.cantidad.toIntOrNull()
        val errorCantidad = when {
            cant == null || cant < 1 -> "La cantidad debe ser al menos 1 unidad."
            cant > maxDisponible -> "Supera las existencias disponibles ($maxDisponible)."
            else -> null
        }

        val errorAmbiente = if (state.ambienteDestino.isBlank()) {
            "El ambiente o destino es obligatorio."
        } else null

        val errorProposito = when {
            state.proposito.length < 10 -> "El motivo/observaciones debe tener al menos 10 caracteres."
            state.proposito.length > 180 -> "El motivo no debe exceder los 180 caracteres."
            else -> null
        }

        val horas = state.duracionHoras.toIntOrNull()
        val errorDuracion = if (horas != null && horas !in 1..72) {
            "La duración debe ser válida."
        } else null

        val errorFecha = if (state.fechaDevolucionEstimada.isBlank()) {
            "La fecha de devolución estimada es obligatoria."
        } else null

        _formularioState.update {
            it.copy(
                errorCantidad = errorCantidad,
                errorFecha = errorFecha,
                errorAmbiente = errorAmbiente,
                errorProposito = errorProposito,
                errorDuracion = errorDuracion,
                puedeGuardar = errorCantidad == null &&
                    errorAmbiente == null &&
                    errorProposito == null &&
                    errorFecha == null
            )
        }
    }

    fun guardarSolicitud(equipoId: Int) {
        if (_uiState.value.guardando) return

        val equipo = obtenerEquipoPorId(equipoId)
        validarFormulario(equipo?.stockDisponible ?: 999)
        val state = _formularioState.value
        if (!state.puedeGuardar) return

        _uiState.update {
            it.copy(
                guardando = true,
                mensaje = null,
                operacionExitosa = false,
                ultimoPrestamoCreadoId = null
            )
        }

        viewModelScope.launch {
            val usuario = _uiState.value.usuarioActual
            val ahoraStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val solicitud = SolicitudPrestamo(
                id = 0,
                equipoId = equipoId,
                ambienteDestino = state.ambienteDestino.trim(),
                proposito = state.proposito.trim(),
                duracionHoras = state.duracionHoras.toIntOrNull() ?: 4,
                estado = EstadoSolicitud.ACTIVO,
                photoPath = state.photoPath,
                usuarioId = usuario?.id ?: 0,
                usuarioCorreo = usuario?.correo ?: "",
                equipoNombre = equipo?.nombre ?: "",
                equipoCategoria = equipo?.categoria?.name ?: "",
                cantidad = state.cantidad.toIntOrNull() ?: 1,
                nombreSolicitante = state.nombreSolicitante.ifBlank { usuario?.nombre ?: "Aprendiz CTMA" },
                documentoSolicitante = state.documentoSolicitante.trim(),
                fechaPrestamo = ahoraStr,
                fechaDevolucionEstimada = state.fechaDevolucionEstimada.ifBlank { defaultFechaDevolucion() }
            )

            val result = repository.crearSolicitud(solicitud)

            _uiState.update {
                it.copy(
                    guardando = false,
                    mensaje = if (result.isSuccess) "Préstamo ACTIVO registrado y certificado generado" else result.exceptionOrNull()?.message,
                    operacionExitosa = result.isSuccess
                )
            }
            if (result.isSuccess) {
                limpiarFormulario()
            }
        }
    }

    fun cancelarSolicitud(solicitudId: Int) {
        viewModelScope.launch {
            val result = repository.cancelarSolicitud(solicitudId)
            _uiState.update {
                it.copy(
                    operacionExitosa = result.isSuccess,
                    mensaje = if (result.isSuccess) "Préstamo cancelado y existencias devueltas al stock" else result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun marcarPrestamoEntregado(solicitudId: Int) {
        viewModelScope.launch {
            val result = repository.entregarSolicitud(solicitudId)
            _uiState.update {
                it.copy(
                    operacionExitosa = result.isSuccess,
                    mensaje = if (result.isSuccess) "Préstamo marcado como ENTREGADO y unidades devueltas al stock" else result.exceptionOrNull()?.message
                )
            }
        }
    }

    // =========================================================================
    // Chat en Tiempo Real
    // =========================================================================
    fun observarMensajesDePrestamo(prestamoId: Int): Flow<List<ChatMensaje>> {
        return repository.observarMensajesChat(prestamoId)
    }

    fun abrirChatPrestamo(prestamoId: Int) {
        chatAbiertoActualmentePrestamoId = prestamoId
        socketManager.joinPrestamoRoom(prestamoId)
        viewModelScope.launch {
            repository.sincronizarChat(prestamoId)
            repository.marcarChatLeido(prestamoId)
        }
    }

    fun cerrarChatPrestamo(prestamoId: Int) {
        if (chatAbiertoActualmentePrestamoId == prestamoId) {
            chatAbiertoActualmentePrestamoId = null
        }
        socketManager.leavePrestamoRoom(prestamoId)
    }

    fun enviarMensajeTextoChat(prestamoId: Int, texto: String) {
        if (texto.isBlank()) return
        viewModelScope.launch {
            val res = repository.enviarMensajeTexto(prestamoId, texto.trim())
            if (res.isFailure) {
                _uiState.update { it.copy(mensaje = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun enviarAdjuntoChat(
        prestamoId: Int,
        tipo: TipoMensajeChat,
        descripcion: String,
        archivo: File,
        mimeType: String,
        duracionSeg: Int? = null
    ) {
        viewModelScope.launch {
            val res = repository.enviarMensajeAdjunto(
                prestamoId = prestamoId,
                tipo = tipo,
                contenido = descripcion,
                archivo = archivo,
                mimeType = mimeType,
                duracionSeg = duracionSeg
            )
            if (res.isFailure) {
                _uiState.update { it.copy(mensaje = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun limpiarMensaje() {
        _uiState.update { it.copy(mensaje = null, operacionExitosa = false) }
    }

    fun limpiarAvisoTiempoReal() {
        _uiState.update { it.copy(avisoTiempoReal = null) }
    }

    private fun limpiarFormulario() {
        val nombreActual = _uiState.value.usuarioActual?.nombre.orEmpty()
        _formularioState.update {
            FormularioSolicitudState(
                nombreSolicitante = nombreActual,
                fechaDevolucionEstimada = defaultFechaDevolucion()
            )
        }
    }

    private fun defaultFechaDevolucion(): String {
        val manana = Date(System.currentTimeMillis() + 24L * 3600L * 1000L)
        return SimpleDateFormat("yyyy-MM-dd 18:00", Locale.getDefault()).format(manana)
    }
}
