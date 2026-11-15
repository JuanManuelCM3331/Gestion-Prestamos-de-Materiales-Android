package com.example.prestamo_lab_ctma.data.remote

import com.example.prestamo_lab_ctma.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // 1. Auth y Roles
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<AuthResponseDto>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): Response<AuthResponseDto>

    @GET("api/admin/admins")
    suspend fun obtenerAdmins(): Response<AdminsListResponseDto>

    @POST("api/admin/admins")
    suspend fun crearAdmin(@Body request: CrearAdminRequestDto): Response<CrearAdminResponseDto>

    @GET("api/admin/auditoria")
    suspend fun obtenerAuditoria(@Query("limit") limit: Int = 100): Response<AuditoriaListResponseDto>

    // 2. Catálogo de Equipos
    @GET("api/equipos")
    suspend fun obtenerEquiposRemotos(
        @Query("search") search: String? = null,
        @Query("categoria") categoria: String? = null,
        @Query("eliminados") eliminados: Boolean = false
    ): Response<EquiposListResponseDto>

    @Multipart
    @POST("api/equipos")
    suspend fun crearEquipoMultipart(
        @Part("nombre") nombre: RequestBody,
        @Part("categoria") categoria: RequestBody,
        @Part("stockTotal") stockTotal: RequestBody,
        @Part("descripcion") descripcion: RequestBody,
        @Part imagen: MultipartBody.Part? = null
    ): Response<EquipoSingleResponseDto>

    @Multipart
    @PUT("api/equipos/{id}")
    suspend fun editarEquipoMultipart(
        @Path("id") equipoId: Int,
        @Part("nombre") nombre: RequestBody,
        @Part("categoria") categoria: RequestBody,
        @Part("stockTotal") stockTotal: RequestBody,
        @Part("descripcion") descripcion: RequestBody,
        @Part imagen: MultipartBody.Part? = null
    ): Response<EquipoSingleResponseDto>

    @DELETE("api/equipos/{id}")
    suspend fun eliminarEquipoLogico(@Path("id") equipoId: Int): Response<EquipoSingleResponseDto>

    @PATCH("api/equipos/{id}/restaurar")
    suspend fun restaurarEquipo(@Path("id") equipoId: Int): Response<EquipoSingleResponseDto>

    @DELETE("api/equipos/{id}/permanente")
    suspend fun eliminarEquipoPermanente(@Path("id") equipoId: Int): Response<Map<String, Any>>

    // 3. Préstamos
    @POST("api/prestamos")
    suspend fun crearPrestamo(@Body request: CrearPrestamoRequestDto): Response<PrestamoSingleResponseDto>

    @GET("api/prestamos")
    suspend fun obtenerPrestamos(@Query("estado") estado: String? = null): Response<PrestamosListResponseDto>

    @PATCH("api/prestamos/{id}/cancelar")
    suspend fun cancelarPrestamo(@Path("id") prestamoId: Int): Response<PrestamoSingleResponseDto>

    @PATCH("api/prestamos/{id}/entregar")
    suspend fun entregarPrestamo(@Path("id") prestamoId: Int): Response<PrestamoSingleResponseDto>

    // 4. Chat en Tiempo Real
    @GET("api/prestamos/{id}/chat")
    suspend fun obtenerHistorialChat(@Path("id") prestamoId: Int): Response<ChatHistorialResponseDto>

    @POST("api/prestamos/{id}/chat/mensajes")
    suspend fun enviarMensajeTexto(
        @Path("id") prestamoId: Int,
        @Body request: EnviarMensajeTextoRequestDto
    ): Response<EnviarMensajeResponseDto>

    @Multipart
    @POST("api/prestamos/{id}/chat/mensajes")
    suspend fun enviarMensajeAdjunto(
        @Path("id") prestamoId: Int,
        @Part("tipo") tipo: RequestBody,
        @Part("contenido") contenido: RequestBody,
        @Part("duracionSeg") duracionSeg: RequestBody? = null,
        @Part archivo: MultipartBody.Part
    ): Response<EnviarMensajeResponseDto>

    @POST("api/prestamos/{id}/chat/leido")
    suspend fun marcarChatLeido(@Path("id") prestamoId: Int): Response<Map<String, Any>>
}
