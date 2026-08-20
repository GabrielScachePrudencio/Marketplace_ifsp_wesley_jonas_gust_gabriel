package com.example.marketplace.data.dao

import android.service.autofill.OnClickAction
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.marketplace.model.Usuario
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usuario: Usuario)

    @Query("SELECT * FROM usuarios WHERE uid = :uid")
    suspend fun buscarPorId(uid: String): Usuario?

    @Query("SELECT * FROM usuarios")
    fun listarTodos(): Flow<List<Usuario>>

    @Delete
    suspend fun deletar(usuario: Usuario)

}