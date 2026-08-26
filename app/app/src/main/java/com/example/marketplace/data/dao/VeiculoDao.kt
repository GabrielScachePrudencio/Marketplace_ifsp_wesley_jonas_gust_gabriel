package com.example.marketplace.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.marketplace.model.Veiculo
import kotlinx.coroutines.flow.Flow

@Dao
interface VeiculoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        veiculo: Veiculo
    )

    @Update
    suspend fun update(
        veiculo: Veiculo
    )

    @Query(
        "SELECT * FROM veiculos WHERE id = :id"
    )
    suspend fun buscarPorId(
        id: String
    ): Veiculo?

    @Query(
        "SELECT * FROM veiculos"
    )
    fun listarTodos(): Flow<List<Veiculo>>

    @Query(
        "SELECT * FROM veiculos WHERE motoristaId = :motoristaId"
    )
    fun listarPorMotorista(
        motoristaId: String
    ): Flow<List<Veiculo>>

    @Delete
    suspend fun deletar(
        veiculo: Veiculo
    )
}
