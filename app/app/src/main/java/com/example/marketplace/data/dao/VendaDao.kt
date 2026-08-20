package com.example.marketplace.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.marketplace.model.Venda
import kotlinx.coroutines.flow.Flow

@Dao
interface VendaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(venda: Venda)

    @Query("SELECT * FROM vendas WHERE id = :uid")
    suspend fun buscarPorId(uid: String): Venda?

    @Query("SELECT * FROM vendas")
    fun listarTodos(): Flow<List<Venda>>

    @Delete
    suspend fun deletar(venda: Venda)
}