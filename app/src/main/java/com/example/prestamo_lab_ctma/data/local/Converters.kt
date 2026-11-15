package com.example.prestamo_lab_ctma.data.local

import androidx.room.TypeConverter
import com.example.prestamo_lab_ctma.model.CategoriaEquipo
import com.example.prestamo_lab_ctma.model.EstadoEquipo
import com.example.prestamo_lab_ctma.model.EstadoSolicitud
import com.example.prestamo_lab_ctma.model.RolUsuario
import com.example.prestamo_lab_ctma.model.TipoMensajeChat

class Converters {
    @TypeConverter
    fun fromCategoria(value: CategoriaEquipo): String = value.name

    @TypeConverter
    fun toCategoria(value: String): CategoriaEquipo =
        runCatching { CategoriaEquipo.valueOf(value) }.getOrDefault(CategoriaEquipo.OTRO)

    @TypeConverter
    fun fromEstadoEquipo(value: EstadoEquipo): String = value.name

    @TypeConverter
    fun toEstadoEquipo(value: String): EstadoEquipo =
        runCatching { EstadoEquipo.valueOf(value) }.getOrDefault(EstadoEquipo.DISPONIBLE)

    @TypeConverter
    fun fromEstadoSolicitud(value: EstadoSolicitud): String = value.name

    @TypeConverter
    fun toEstadoSolicitud(value: String): EstadoSolicitud =
        runCatching { EstadoSolicitud.valueOf(value) }.getOrDefault(EstadoSolicitud.ACTIVO)

    @TypeConverter
    fun fromRolUsuario(value: RolUsuario): String = value.name

    @TypeConverter
    fun toRolUsuario(value: String): RolUsuario =
        runCatching { RolUsuario.valueOf(value) }.getOrDefault(RolUsuario.USUARIO)

    @TypeConverter
    fun fromTipoMensajeChat(value: TipoMensajeChat): String = value.name

    @TypeConverter
    fun toTipoMensajeChat(value: String): TipoMensajeChat =
        runCatching { TipoMensajeChat.valueOf(value) }.getOrDefault(TipoMensajeChat.TEXTO)
}
