package com.example.marketplace.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.marketplace.model.RastreioEntrega

@Dao
interface RastreioEntregaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rastreioEntrega: RastreioEntrega)

    @Update
    suspend fun update(rastreioEntrega: RastreioEntrega)

    @Delete
    suspend fun deletar(rastreioEntrega: RastreioEntrega)

    @Query("SELECT * FROM rastreios_entrega WHERE id = :id LIMIT 1")
    suspend fun buscarPorId(id: String): RastreioEntrega?

    @Query("SELECT * FROM rastreios_entrega WHERE vendaId = :vendaId LIMIT 1")
    suspend fun buscarPorVendaId(vendaId: String): RastreioEntrega?

    @Query("SELECT * FROM rastreios_entrega WHERE motoristaId = :motoristaId ORDER BY posicaoNaFila ASC")
    suspend fun listarFilaDoMotorista(motoristaId: String): List<RastreioEntrega>
}