package com.example.marketplace.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.marketplace.model.AvaliacaoProduto
import kotlinx.coroutines.flow.Flow

@Dao
interface AvaliacaoProdutoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(avaliacaoProduto: AvaliacaoProduto)

    @Query("SELECT * FROM avalicaoProdutos WHERE id = :uid")
    suspend fun buscarPorId(uid: String): AvaliacaoProduto?

    @Query("SELECT * FROM avalicaoProdutos")
    fun listarTodos(): Flow<List<AvaliacaoProduto>>

    @Delete
    suspend fun deletar(avaliacaoProduto: AvaliacaoProduto)
}