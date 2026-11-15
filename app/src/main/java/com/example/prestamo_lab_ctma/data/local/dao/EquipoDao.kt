package com.example.prestamo_lab_ctma.data.local.dao

import androidx.room.*
import com.example.prestamo_lab_ctma.data.local.entities.EquipoEntity
import com.example.prestamo_lab_ctma.model.EstadoEquipo
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipoDao {
    @Query("SELECT * FROM equipos ORDER BY id DESC")
    fun obtenerTodos(): Flow<List<EquipoEntity>>

    @Query("SELECT * FROM equipos WHERE id = :id")
    suspend fun obtenerPorId(id: Int): EquipoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarEquipos(equipos: List<EquipoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarEquipo(equipo: EquipoEntity): Long

    @Update
    suspend fun actualizarEquipo(equipo: EquipoEntity)

    @Query(
        """
        UPDATE equipos
        SET stockTotal = :stockTotal,
            stockDisponible = :stockDisponible,
            estado = :estado
        WHERE id = :equipoId
        """
    )
    suspend fun actualizarStock(
        equipoId: Int,
        stockTotal: Int,
        stockDisponible: Int,
        estado: EstadoEquipo = if (stockDisponible > 0) EstadoEquipo.DISPONIBLE else EstadoEquipo.PRESTADO
    )

    @Query("DELETE FROM equipos WHERE id = :id")
    suspend fun eliminarPorId(id: Int)
}
