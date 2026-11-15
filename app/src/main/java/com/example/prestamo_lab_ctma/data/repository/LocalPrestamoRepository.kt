package com.example.prestamo_lab_ctma.data.repository

import com.example.prestamo_lab_ctma.BuildConfig
import com.example.prestamo_lab_ctma.data.local.UserPreferencesRepository
import com.example.prestamo_lab_ctma.data.local.dao.ChatDao
import com.example.prestamo_lab_ctma.data.local.dao.EquipoDao
import com.example.prestamo_lab_ctma.data.local.dao.SolicitudDao
import com.example.prestamo_lab_ctma.data.local.entities.ChatMensajeEntity
import com.example.prestamo_lab_ctma.data.local.entities.EquipoEntity
import com.example.prestamo_lab_ctma.data.local.entities.SolicitudEntity
import com.example.prestamo_lab_ctma.data.remote.ApiService
import com.example.prestamo_lab_ctma.data.remote.dto.*
import com.example.prestamo_lab_ctma.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPrestamoRepository @Inject constructor(
    private val equipoDao: EquipoDao,
    private val solicitudDao: SolicitudDao,
    private val chatDao: ChatDao? = null,
    private val apiService: ApiService? = null,
    private val userPrefs: UserPreferencesRepository? = null
) : PrestamoRepository {

    // Constructor secundario compatible con pruebas unitarias existentes
    constructor(equipoDao: EquipoDao, solicitudDao: SolicitudDao) : this(
        equipoDao = equipoDao,
        solicitudDao = solicitudDao,
        chatDao = null,
        apiService = null,
        userPrefs = null
    )

    private suspend fun baseUrl(): String =
        userPrefs?.obtenerBaseUrlActual() ?: BuildConfig.API_BASE_URL

    private fun parseError(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string()
            if (!raw.isNullOrBlank()) {
                JSONObject(raw).optString("error", fallback).ifBlank { fallback }
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    override fun obtenerEquipos(): Flow<List<Equipo>> {
        return equipoDao.obtenerTodos().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun obtenerEquipo(id: Int): Equipo? {
        return equipoDao.obtenerPorId(id)?.toDomain()
    }

    override fun obtenerSolicitudes(): Flow<List<SolicitudPrestamo>> {
        return solicitudDao.obtenerTodas().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun obtenerSolicitud(id: Int): SolicitudPrestamo? {
        return solicitudDao.obtenerPorId(id)?.toDomain()
    }

    override suspend fun sincronizarConServidor(incluirEliminados: Boolean) {
        val api = apiService ?: return
        val currentBase = baseUrl()
        try {
            val respActivos = api.obtenerEquiposRemotos(eliminados = false)
            if (respActivos.isSuccessful) {
                val activos = respActivos.body()?.equipos.orEmpty().map { it.toEntity(currentBase) }
                equipoDao.insertarEquipos(activos)
            }
            if (incluirEliminados) {
                val respEliminados = api.obtenerEquiposRemotos(eliminados = true)
                if (respEliminados.isSuccessful) {
                    val eliminados = respEliminados.body()?.equipos.orEmpty().map { it.toEntity(currentBase) }
                    equipoDao.insertarEquipos(eliminados)
                }
            }
            val respPrestamos = api.obtenerPrestamos()
            if (respPrestamos.isSuccessful) {
                val prestamos = respPrestamos.body()?.prestamos.orEmpty().map { it.toEntity(currentBase) }
                solicitudDao.insertarSolicitudes(prestamos)
            }
        } catch (_: Exception) {
            // Modo offline: se mantiene la caché de Room
        }
    }

    override suspend fun crearEquipoCatalogo(
        nombre: String,
        categoria: CategoriaEquipo,
        stockTotal: Int,
        descripcion: String,
        imagenFile: File?
    ): Result<Equipo> {
        val api = apiService
        if (api != null) {
            try {
                val textMedia = "text/plain".toMediaTypeOrNull()
                val partImagen = imagenFile?.takeIf { it.exists() }?.let { file ->
                    val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("imagen", file.name, reqFile)
                }
                val response = api.crearEquipoMultipart(
                    nombre = nombre.toRequestBody(textMedia),
                    categoria = categoria.name.toRequestBody(textMedia),
                    stockTotal = stockTotal.toString().toRequestBody(textMedia),
                    descripcion = descripcion.toRequestBody(textMedia),
                    imagen = partImagen
                )
                if (response.isSuccessful && response.body() != null) {
                    val entity = response.body()!!.equipo.toEntity(baseUrl())
                    equipoDao.insertarEquipo(entity)
                    return Result.success(entity.toDomain())
                } else if (response.code() in 400..499) {
                    return Result.failure(Exception(parseError(response, "Error al crear el equipo")))
                }
            } catch (_: Exception) {
                // Fallback local en Room si el servidor no responde
            }
        }

        return try {
            val estado = if (stockTotal > 0) EstadoEquipo.DISPONIBLE else EstadoEquipo.PRESTADO
            val nuevo = EquipoEntity(
                id = 0,
                nombre = nombre,
                categoria = categoria,
                estado = estado,
                descripcion = descripcion,
                imagenUrl = imagenFile?.absolutePath,
                stockTotal = stockTotal,
                stockDisponible = stockTotal,
                eliminado = false,
                updatedAt = System.currentTimeMillis()
            )
            val idGen = equipoDao.insertarEquipo(nuevo).toInt()
            val guardado = nuevo.copy(id = if (idGen > 0) idGen else (100..9999).random())
            Result.success(guardado.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun editarEquipoCatalogo(
        equipoId: Int,
        nombre: String,
        categoria: CategoriaEquipo,
        stockTotal: Int,
        descripcion: String,
        imagenFile: File?
    ): Result<Equipo> {
        val api = apiService
        if (api != null) {
            try {
                val textMedia = "text/plain".toMediaTypeOrNull()
                val partImagen = imagenFile?.takeIf { it.exists() }?.let { file ->
                    val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("imagen", file.name, reqFile)
                }
                val response = api.editarEquipoMultipart(
                    equipoId = equipoId,
                    nombre = nombre.toRequestBody(textMedia),
                    categoria = categoria.name.toRequestBody(textMedia),
                    stockTotal = stockTotal.toString().toRequestBody(textMedia),
                    descripcion = descripcion.toRequestBody(textMedia),
                    imagen = partImagen
                )
                if (response.isSuccessful && response.body() != null) {
                    val entity = response.body()!!.equipo.toEntity(baseUrl())
                    equipoDao.insertarEquipo(entity)
                    return Result.success(entity.toDomain())
                } else if (response.code() in 400..499) {
                    return Result.failure(Exception(parseError(response, "No se pudo editar el equipo")))
                }
            } catch (_: Exception) {
                // Fallback local
            }
        }

        return try {
            val actual = equipoDao.obtenerPorId(equipoId)
                ?: return Result.failure(Exception("Equipo no encontrado"))
            val enPrestamo = (actual.stockTotal - actual.stockDisponible).coerceAtLeast(0)
            if (stockTotal < enPrestamo) {
                return Result.failure(
                    Exception("No puedes reducir el stock total a $stockTotal porque hay $enPrestamo unidad(es) en préstamo ACTIVO.")
                )
            }
            val nuevoDisponible = stockTotal - enPrestamo
            val actualizado = actual.copy(
                nombre = nombre,
                categoria = categoria,
                descripcion = descripcion,
                imagenUrl = imagenFile?.absolutePath ?: actual.imagenUrl,
                stockTotal = stockTotal,
                stockDisponible = nuevoDisponible,
                estado = if (nuevoDisponible > 0) EstadoEquipo.DISPONIBLE else EstadoEquipo.PRESTADO,
                updatedAt = System.currentTimeMillis()
            )
            equipoDao.actualizarEquipo(actualizado)
            Result.success(actualizado.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun eliminarEquipoLogico(equipoId: Int): Result<Unit> {
        val api = apiService
        if (api != null) {
            try {
                val resp = api.eliminarEquipoLogico(equipoId)
                if (resp.isSuccessful && resp.body() != null) {
                    equipoDao.insertarEquipo(resp.body()!!.equipo.toEntity(baseUrl()))
                    return Result.success(Unit)
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "No se pudo eliminar el equipo")))
                }
            } catch (_: Exception) {
                // Fallback local
            }
        }

        val actual = equipoDao.obtenerPorId(equipoId)
            ?: return Result.failure(Exception("Equipo no encontrado"))
        val ahoraIso = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        equipoDao.actualizarEquipo(actual.copy(eliminado = true, eliminadoAt = ahoraIso))
        return Result.success(Unit)
    }

    override suspend fun restaurarEquipo(equipoId: Int): Result<Unit> {
        val api = apiService
        if (api != null) {
            try {
                val resp = api.restaurarEquipo(equipoId)
                if (resp.isSuccessful && resp.body() != null) {
                    equipoDao.insertarEquipo(resp.body()!!.equipo.toEntity(baseUrl()))
                    return Result.success(Unit)
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "No se pudo restaurar el equipo")))
                }
            } catch (_: Exception) {
                // Fallback local
            }
        }

        val actual = equipoDao.obtenerPorId(equipoId)
            ?: return Result.failure(Exception("Equipo no encontrado"))
        equipoDao.actualizarEquipo(actual.copy(eliminado = false, eliminadoAt = null))
        return Result.success(Unit)
    }

    override suspend fun eliminarEquipoPermanente(equipoId: Int): Result<Unit> {
        val api = apiService
        if (api != null) {
            try {
                val resp = api.eliminarEquipoPermanente(equipoId)
                if (resp.isSuccessful) {
                    equipoDao.eliminarPorId(equipoId)
                    return Result.success(Unit)
                } else if (resp.code() in 400..499) {
                    return Result.failure(
                        Exception(parseError(resp, "No se permite eliminar un equipo con préstamos activos"))
                    )
                }
            } catch (_: Exception) {
                // Fallback local
            }
        }

        val todas = solicitudDao.obtenerTodas().first()
        val tieneActivos = todas.any { it.equipoId == equipoId && it.estado.esActivo() }
        if (tieneActivos) {
            return Result.failure(
                Exception("No se puede eliminar permanentemente este equipo porque tiene préstamos ACTIVOS.")
            )
        }
        equipoDao.eliminarPorId(equipoId)
        return Result.success(Unit)
    }

    override suspend fun crearSolicitud(solicitud: SolicitudPrestamo): Result<Unit> {
        val api = apiService
        if (api != null) {
            try {
                val response = api.crearPrestamo(
                    CrearPrestamoRequestDto(
                        equipoId = solicitud.equipoId,
                        cantidad = solicitud.cantidad.coerceAtLeast(1),
                        fechaDevolucionEstimada = solicitud.fechaDevolucionEstimada,
                        motivo = solicitud.proposito,
                        ambienteDestino = solicitud.ambienteDestino,
                        nombreSolicitante = solicitud.nombreSolicitante,
                        documentoSolicitante = solicitud.documentoSolicitante
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val entity = body.prestamo.toEntity(baseUrl())
                    solicitudDao.insertarSolicitud(entity)
                    val eqActual = equipoDao.obtenerPorId(solicitud.equipoId)
                    if (eqActual != null && body.stockDisponible != null) {
                        equipoDao.actualizarStock(
                            equipoId = eqActual.id,
                            stockTotal = eqActual.stockTotal,
                            stockDisponible = body.stockDisponible
                        )
                    }
                    return Result.success(Unit)
                } else if (response.code() in 400..499) {
                    return Result.failure(
                        Exception(parseError(response, "No hay suficientes existencias disponibles"))
                    )
                }
            } catch (_: Exception) {
                // Si el backend no está alcanzable, ejecutamos de forma atómica en Room local
            }
        }

        return try {
            val equipo = equipoDao.obtenerPorId(solicitud.equipoId)
                ?: return Result.failure(Exception("Equipo no encontrado"))

            val cantSolicitada = solicitud.cantidad.coerceAtLeast(1)
            if (equipo.eliminado) {
                return Result.failure(Exception("El equipo fue retirado del catálogo"))
            }
            if (equipo.estado != EstadoEquipo.DISPONIBLE || equipo.stockDisponible < cantSolicitada) {
                return Result.failure(
                    Exception("El equipo no tiene suficientes unidades disponibles (disponibles: ${equipo.stockDisponible})")
                )
            }

            val nuevoStockDisp = (equipo.stockDisponible - cantSolicitada).coerceAtLeast(0)
            val nuevoEstadoEquipo = if (nuevoStockDisp > 0) EstadoEquipo.DISPONIBLE else EstadoEquipo.RESERVADO

            val ahoraStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val certCode = solicitud.codigoCertificado.ifBlank {
                "CTMA-2026-${(10000..99999).random()}"
            }

            val entity = solicitud.copy(
                codigoCertificado = certCode,
                equipoNombre = solicitud.equipoNombre.ifBlank { equipo.nombre },
                equipoCategoria = solicitud.equipoCategoria.ifBlank { equipo.categoria.name },
                fechaPrestamo = solicitud.fechaPrestamo.ifBlank { ahoraStr }
            ).toEntity()

            solicitudDao.insertarSolicitud(entity)
            equipoDao.actualizarEquipo(
                equipo.copy(
                    stockDisponible = nuevoStockDisp,
                    estado = nuevoEstadoEquipo
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelarSolicitud(id: Int): Result<Unit> {
        val api = apiService
        if (api != null) {
            try {
                val resp = api.cancelarPrestamo(id)
                if (resp.isSuccessful && resp.body() != null) {
                    val entity = resp.body()!!.prestamo.toEntity(baseUrl())
                    solicitudDao.actualizarSolicitud(entity)
                    sincronizarConServidor(incluirEliminados = false)
                    return Result.success(Unit)
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "No se pudo cancelar el préstamo")))
                }
            } catch (_: Exception) {
                // Fallback local
            }
        }

        return try {
            val solicitud = solicitudDao.obtenerPorId(id)
                ?: return Result.failure(Exception("Solicitud no encontrada"))

            if (!solicitud.estado.esActivo()) {
                return Result.failure(Exception("Solo se pueden cancelar préstamos activos"))
            }

            val nuevoEstado = if (solicitud.estado == EstadoSolicitud.SOLICITADA) {
                EstadoSolicitud.CANCELADA
            } else {
                EstadoSolicitud.CANCELADO
            }

            val fechaCierre = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            solicitudDao.actualizarSolicitud(
                solicitud.copy(
                    estado = nuevoEstado,
                    fechaCierre = fechaCierre,
                    chatSoloLectura = true
                )
            )

            val equipo = equipoDao.obtenerPorId(solicitud.equipoId)
            if (equipo != null) {
                val cantDevuelta = solicitud.cantidad.coerceAtLeast(1)
                val nuevoDisp = (equipo.stockDisponible + cantDevuelta).coerceAtMost(equipo.stockTotal.coerceAtLeast(1))
                equipoDao.actualizarEquipo(
                    equipo.copy(
                        stockDisponible = nuevoDisp,
                        estado = EstadoEquipo.DISPONIBLE
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun entregarSolicitud(id: Int): Result<Unit> {
        val api = apiService
        if (api != null) {
            try {
                val resp = api.entregarPrestamo(id)
                if (resp.isSuccessful && resp.body() != null) {
                    val entity = resp.body()!!.prestamo.toEntity(baseUrl())
                    solicitudDao.actualizarSolicitud(entity)
                    sincronizarConServidor(incluirEliminados = false)
                    return Result.success(Unit)
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "No se pudo marcar como entregado")))
                }
            } catch (_: Exception) {
                // Fallback local
            }
        }

        return try {
            val solicitud = solicitudDao.obtenerPorId(id)
                ?: return Result.failure(Exception("Préstamo no encontrado"))

            if (!solicitud.estado.esActivo()) {
                return Result.failure(Exception("El préstamo ya no está activo"))
            }

            val fechaCierre = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            solicitudDao.actualizarSolicitud(
                solicitud.copy(
                    estado = EstadoSolicitud.ENTREGADO,
                    fechaCierre = fechaCierre,
                    chatSoloLectura = true
                )
            )

            val equipo = equipoDao.obtenerPorId(solicitud.equipoId)
            if (equipo != null) {
                val cantDevuelta = solicitud.cantidad.coerceAtLeast(1)
                val nuevoDisp = (equipo.stockDisponible + cantDevuelta).coerceAtMost(equipo.stockTotal.coerceAtLeast(1))
                equipoDao.actualizarEquipo(
                    equipo.copy(
                        stockDisponible = nuevoDisp,
                        estado = EstadoEquipo.DISPONIBLE
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun inicializarEquipos() {
        val count = equipoDao.obtenerTodos().first().size
        if (count == 0) {
            val iniciales = listOf(
                EquipoEntity(
                    id = 1,
                    nombre = "Multímetro digital",
                    categoria = CategoriaEquipo.ELECTRONICA,
                    estado = EstadoEquipo.DISPONIBLE,
                    descripcion = "Multímetro digital de precisión True RMS con puntas de prueba para laboratorio de electrónica.",
                    stockTotal = 5,
                    stockDisponible = 5
                ),
                EquipoEntity(
                    id = 2,
                    nombre = "Kit Arduino",
                    categoria = CategoriaEquipo.ELECTRONICA,
                    estado = EstadoEquipo.DISPONIBLE,
                    descripcion = "Kit Arduino UNO R3 con sensores, jumpers, resistencias y cable USB para prototipado.",
                    stockTotal = 8,
                    stockDisponible = 8
                ),
                EquipoEntity(
                    id = 3,
                    nombre = "Cámara digital",
                    categoria = CategoriaEquipo.MULTIMEDIA,
                    estado = EstadoEquipo.DISPONIBLE,
                    descripcion = "Cámara réflex digital Full HD con lente 18-55mm, batería y tarjeta SD de 64GB.",
                    stockTotal = 3,
                    stockDisponible = 3
                ),
                EquipoEntity(
                    id = 4,
                    nombre = "Tablet educativa",
                    categoria = CategoriaEquipo.INFORMATICA,
                    estado = EstadoEquipo.RESERVADO,
                    descripcion = "Tablet de 10 pulgadas para diseño, lectura de planos y control de aplicaciones móviles.",
                    stockTotal = 6,
                    stockDisponible = 0
                ),
                EquipoEntity(
                    id = 5,
                    nombre = "Taladro inalámbrico",
                    categoria = CategoriaEquipo.HERRAMIENTAS,
                    estado = EstadoEquipo.PRESTADO,
                    descripcion = "Taladro percutor inalámbrico de 18V con dos baterías de litio y maletín de brocas.",
                    stockTotal = 4,
                    stockDisponible = 0
                ),
                EquipoEntity(
                    id = 6,
                    nombre = "Fuente de alimentación",
                    categoria = CategoriaEquipo.ELECTRONICA,
                    estado = EstadoEquipo.DISPONIBLE,
                    descripcion = "Fuente de poder DC regulable de laboratorio (0-30V, 0-5A) con protección contra cortocircuito.",
                    stockTotal = 5,
                    stockDisponible = 5
                ),
                EquipoEntity(
                    id = 7,
                    nombre = "Protoboard",
                    categoria = CategoriaEquipo.ELECTRONICA,
                    estado = EstadoEquipo.DISPONIBLE,
                    descripcion = "Placa de pruebas de 830 puntos sin soldadura con rieles de alimentación dobles.",
                    stockTotal = 15,
                    stockDisponible = 15
                ),
                EquipoEntity(
                    id = 8,
                    nombre = "Cámara web",
                    categoria = CategoriaEquipo.MULTIMEDIA,
                    estado = EstadoEquipo.DISPONIBLE,
                    descripcion = "Cámara web 1080p con micrófono estéreo integrado y soporte universal para monitor.",
                    stockTotal = 6,
                    stockDisponible = 6
                )
            )
            equipoDao.insertarEquipos(iniciales)
        }
        sincronizarConServidor(incluirEliminados = false)
    }

    // =========================================================================
    // Autenticación y Roles
    // =========================================================================
    override suspend fun login(correo: String, password: String): Result<Usuario> {
        val mail = correo.trim().lowercase()
        val api = apiService
        if (api != null) {
            try {
                val resp = api.login(LoginRequestDto(mail, password))
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    val usuario = body.usuario.toDomain()
                    userPrefs?.guardarSesion(body.token, usuario)
                    sincronizarConServidor(incluirEliminados = usuario.esAdmin)
                    return Result.success(usuario)
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "Credenciales inválidas")))
                }
            } catch (_: Exception) {
                // Si el backend local no está encendido, permitir login offline con el admin semilla o usuario local
            }
        }

        // Modo offline de respaldo
        return if (mail == "admin@ctma.sena.edu.co" && password == "Admin123*") {
            val admin = Usuario(
                id = 1,
                nombre = "Administrador Principal CTMA",
                correo = mail,
                rol = RolUsuario.ADMIN_EQUIPOS
            )
            userPrefs?.guardarSesion("offline-admin-token", admin)
            Result.success(admin)
        } else if (mail.contains("@") && password.length >= 6) {
            val user = Usuario(
                id = 2,
                nombre = mail.substringBefore("@").replaceFirstChar { it.uppercase() },
                correo = mail,
                rol = RolUsuario.USUARIO
            )
            userPrefs?.guardarSesion("offline-user-token", user)
            Result.success(user)
        } else {
            Result.failure(Exception("Credenciales inválidas (verifique correo y contraseña)"))
        }
    }

    override suspend fun registrarUsuario(
        nombre: String,
        correo: String,
        password: String
    ): Result<Usuario> {
        val mail = correo.trim().lowercase()
        val api = apiService
        if (api != null) {
            try {
                val resp = api.register(RegisterRequestDto(nombre.trim(), mail, password))
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    val usuario = body.usuario.toDomain()
                    userPrefs?.guardarSesion(body.token, usuario)
                    sincronizarConServidor(incluirEliminados = false)
                    return Result.success(usuario)
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "No se pudo registrar el usuario")))
                }
            } catch (_: Exception) {
                // Fallback offline
            }
        }

        val nuevo = Usuario(
            id = (10..9999).random(),
            nombre = nombre.trim(),
            correo = mail,
            rol = RolUsuario.USUARIO
        )
        userPrefs?.guardarSesion("offline-user-token-${nuevo.id}", nuevo)
        return Result.success(nuevo)
    }

    override suspend fun crearAdministrador(
        nombre: String,
        correo: String,
        passwordTemporal: String
    ): Result<Usuario> {
        val api = apiService
        if (api != null) {
            try {
                val resp = api.crearAdmin(CrearAdminRequestDto(nombre.trim(), correo.trim().lowercase(), passwordTemporal))
                if (resp.isSuccessful && resp.body() != null) {
                    return Result.success(resp.body()!!.admin.toDomain())
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "Error al crear administrador")))
                }
            } catch (_: Exception) {
                // Fallback
            }
        }
        return Result.success(
            Usuario(
                id = (100..999).random(),
                nombre = nombre.trim(),
                correo = correo.trim().lowercase(),
                rol = RolUsuario.ADMIN_EQUIPOS,
                passwordTemporal = true
            )
        )
    }

    override suspend fun obtenerAdministradores(): Result<List<Usuario>> {
        val api = apiService
        if (api != null) {
            try {
                val resp = api.obtenerAdmins()
                if (resp.isSuccessful && resp.body() != null) {
                    return Result.success(resp.body()!!.admins.map { it.toDomain() })
                }
            } catch (_: Exception) {
                // Fallback
            }
        }
        return Result.success(
            listOf(
                Usuario(
                    id = 1,
                    nombre = "Administrador Principal CTMA",
                    correo = "admin@ctma.sena.edu.co",
                    rol = RolUsuario.ADMIN_EQUIPOS
                )
            )
        )
    }

    override suspend fun obtenerAuditoria(): Result<List<AuditoriaLog>> {
        val api = apiService
        if (api != null) {
            try {
                val resp = api.obtenerAuditoria()
                if (resp.isSuccessful && resp.body() != null) {
                    return Result.success(resp.body()!!.logs.map { it.toDomain() })
                }
            } catch (_: Exception) {
                // Fallback
            }
        }
        return Result.success(emptyList())
    }

    // =========================================================================
    // Chat por Préstamo
    // =========================================================================
    override fun observarMensajesChat(prestamoId: Int): Flow<List<ChatMensaje>> {
        val dao = chatDao ?: return flowOf(emptyList())
        return dao.observarMensajesPorPrestamo(prestamoId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun sincronizarChat(prestamoId: Int): Result<Boolean> {
        val api = apiService ?: return Result.success(false)
        val dao = chatDao ?: return Result.success(false)
        return try {
            val resp = api.obtenerHistorialChat(prestamoId)
            if (resp.isSuccessful && resp.body() != null) {
                val body = resp.body()!!
                val currentBase = baseUrl()
                dao.insertarMensajes(body.mensajes.map { it.toEntity(currentBase) })
                solicitudDao.actualizarUnreadCount(prestamoId, body.unreadCount)
                Result.success(body.soloLectura)
            } else {
                Result.failure(Exception(parseError(resp, "No tienes acceso a este chat")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enviarMensajeTexto(
        prestamoId: Int,
        contenido: String
    ): Result<ChatMensaje> {
        val prestamo = solicitudDao.obtenerPorId(prestamoId)
        if (prestamo != null && !prestamo.estado.esActivo()) {
            return Result.failure(Exception("El préstamo está cerrado. El chat es de solo lectura."))
        }

        val api = apiService
        if (api != null) {
            try {
                val resp = api.enviarMensajeTexto(prestamoId, EnviarMensajeTextoRequestDto(contenido))
                if (resp.isSuccessful && resp.body() != null) {
                    val entity = resp.body()!!.chatMensaje.toEntity(baseUrl())
                    chatDao?.insertarMensaje(entity)
                    return Result.success(entity.toDomain())
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "No se pudo enviar el mensaje")))
                }
            } catch (_: Exception) {
                // Fallback local
            }
        }

        val sesion = userPrefs?.sesionFlow?.first()
        val user = sesion?.usuario
        val ahora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val entity = ChatMensajeEntity(
            id = 0,
            chatId = prestamoId,
            prestamoId = prestamoId,
            remitenteId = user?.id ?: 1,
            remitenteNombre = user?.nombre ?: "Usuario",
            remitenteRol = user?.rol ?: RolUsuario.USUARIO,
            tipo = TipoMensajeChat.TEXTO,
            contenido = contenido,
            createdAt = ahora
        )
        val newId = (chatDao?.insertarMensaje(entity) ?: 1L).toInt()
        return Result.success(entity.copy(id = newId).toDomain())
    }

    override suspend fun enviarMensajeAdjunto(
        prestamoId: Int,
        tipo: TipoMensajeChat,
        contenido: String,
        archivo: File,
        mimeType: String,
        duracionSeg: Int?
    ): Result<ChatMensaje> {
        val prestamo = solicitudDao.obtenerPorId(prestamoId)
        if (prestamo != null && !prestamo.estado.esActivo()) {
            return Result.failure(Exception("El préstamo está cerrado. El chat es de solo lectura."))
        }

        // Validación local de tamaño (máximo 10 MB)
        val maxBytes = 10L * 1024L * 1024L
        if (archivo.length() > maxBytes) {
            return Result.failure(Exception("El archivo supera el límite máximo permitido de 10 MB."))
        }

        val api = apiService
        if (api != null) {
            try {
                val textMedia = "text/plain".toMediaTypeOrNull()
                val reqFile = archivo.asRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("archivo", archivo.name, reqFile)
                val durBody = duracionSeg?.toString()?.toRequestBody(textMedia)

                val resp = api.enviarMensajeAdjunto(
                    prestamoId = prestamoId,
                    tipo = tipo.name.toRequestBody(textMedia),
                    contenido = contenido.toRequestBody(textMedia),
                    duracionSeg = durBody,
                    archivo = part
                )
                if (resp.isSuccessful && resp.body() != null) {
                    val entity = resp.body()!!.chatMensaje.toEntity(baseUrl())
                    chatDao?.insertarMensaje(entity)
                    return Result.success(entity.toDomain())
                } else if (resp.code() in 400..499) {
                    return Result.failure(Exception(parseError(resp, "No se pudo subir el adjunto")))
                }
            } catch (_: Exception) {
                // Fallback local
            }
        }

        val sesion = userPrefs?.sesionFlow?.first()
        val user = sesion?.usuario
        val ahora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val entity = ChatMensajeEntity(
            id = 0,
            chatId = prestamoId,
            prestamoId = prestamoId,
            remitenteId = user?.id ?: 1,
            remitenteNombre = user?.nombre ?: "Usuario",
            remitenteRol = user?.rol ?: RolUsuario.USUARIO,
            tipo = tipo,
            contenido = contenido,
            archivoUrl = archivo.absolutePath,
            archivoNombre = archivo.name,
            archivoMime = mimeType,
            archivoTamano = archivo.length().toInt(),
            duracionSeg = duracionSeg,
            createdAt = ahora
        )
        val newId = (chatDao?.insertarMensaje(entity) ?: 1L).toInt()
        return Result.success(entity.copy(id = newId).toDomain())
    }

    override suspend fun marcarChatLeido(prestamoId: Int) {
        solicitudDao.actualizarUnreadCount(prestamoId, 0)
        runCatching { apiService?.marcarChatLeido(prestamoId) }
    }

    // =========================================================================
    // Aplicación directa de eventos Socket.IO sobre Room
    // =========================================================================
    suspend fun aplicarEventoCatalogo(accion: String, equipoDto: EquipoDto?) {
        if (equipoDto == null) return
        if (accion == "BORRADO_PERMANENTE") {
            equipoDao.eliminarPorId(equipoDto.id)
        } else {
            equipoDao.insertarEquipo(equipoDto.toEntity(baseUrl()))
        }
    }

    suspend fun aplicarEventoStock(equipoId: Int, stockTotal: Int, stockDisponible: Int) {
        equipoDao.actualizarStock(equipoId, stockTotal, stockDisponible)
    }

    suspend fun aplicarEventoPrestamo(prestamoDto: PrestamoDto) {
        solicitudDao.insertarSolicitud(prestamoDto.toEntity(baseUrl()))
    }

    suspend fun aplicarEventoMensajeChat(prestamoId: Int, mensajeDto: ChatMensajeDto, currentUserId: Int?) {
        val entity = mensajeDto.toEntity(baseUrl())
        chatDao?.insertarMensaje(entity)
        if (currentUserId != null && mensajeDto.remitenteId != currentUserId) {
            val actual = solicitudDao.obtenerPorId(prestamoId)
            if (actual != null) {
                solicitudDao.actualizarUnreadCount(prestamoId, actual.unreadCount + 1)
            }
        }
    }

    // Mappers
    private fun EquipoEntity.toDomain() = Equipo(
        id = id,
        nombre = nombre,
        categoria = categoria,
        estado = estado,
        descripcion = descripcion,
        imagenUrl = imagenUrl,
        stockTotal = stockTotal,
        stockDisponible = stockDisponible,
        eliminado = eliminado,
        eliminadoAt = eliminadoAt,
        updatedAt = updatedAt
    )

    private fun SolicitudEntity.toDomain() = SolicitudPrestamo(
        id = id,
        equipoId = equipoId,
        ambienteDestino = ambienteDestino,
        proposito = proposito,
        duracionHoras = duracionHoras,
        estado = estado,
        photoPath = photoPath,
        codigoCertificado = codigoCertificado.ifBlank { "CTMA-2026-${id.toString().padStart(5, '0')}" },
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
        chatSoloLectura = chatSoloLectura || !estado.esActivo(),
        unreadCount = unreadCount
    )

    private fun SolicitudPrestamo.toEntity() = SolicitudEntity(
        id = id,
        equipoId = equipoId,
        ambienteDestino = ambienteDestino,
        proposito = proposito,
        duracionHoras = duracionHoras,
        estado = estado,
        photoPath = photoPath,
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
        chatSoloLectura = chatSoloLectura,
        unreadCount = unreadCount
    )

    private fun ChatMensajeEntity.toDomain() = ChatMensaje(
        id = id,
        chatId = chatId,
        prestamoId = prestamoId,
        remitenteId = remitenteId,
        remitenteNombre = remitenteNombre,
        remitenteRol = remitenteRol,
        tipo = tipo,
        contenido = contenido,
        archivoUrl = archivoUrl,
        archivoNombre = archivoNombre,
        archivoMime = archivoMime,
        archivoTamano = archivoTamano,
        duracionSeg = duracionSeg,
        createdAt = createdAt
    )
}
