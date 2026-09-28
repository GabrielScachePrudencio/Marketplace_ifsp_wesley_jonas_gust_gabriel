package com.example.marketplace.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.marketplace.model.PontoReferencia

@Dao
interface PontoReferenciaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pontoReferencia: PontoReferencia)

    @Update
    suspend fun update(pontoReferencia: PontoReferencia)

    @Delete
    suspend fun deletar(pontoReferencia: PontoReferencia)

    @Query("SELECT * FROM pontos_referencia WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: String): PontoReferencia?

    @Query("SELECT * FROM pontos_referencia WHERE negocianteId = :negocianteId ORDER BY dataCriacao DESC")
    suspend fun listarPorNegociante(negocianteId: String): List<PontoReferencia>
}