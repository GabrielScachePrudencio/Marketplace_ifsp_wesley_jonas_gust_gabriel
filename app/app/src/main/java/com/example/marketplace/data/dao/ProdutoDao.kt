package com.example.marketplace.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.marketplace.model.Produto
import kotlinx.coroutines.flow.Flow

@Dao
interface ProdutoDao{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(produto: Produto)

    @Update
    suspend fun update(produto: Produto)

    @Query("SELECT * FROM produtos WHERE id = :uid")
    suspend fun buscarPorId(uid: String): Produto?

    @Query("SELECT * FROM produtos")
    fun listarTodos(): Flow<List<Produto>>

    @Delete
    suspend fun deletar(produto: Produto)
}