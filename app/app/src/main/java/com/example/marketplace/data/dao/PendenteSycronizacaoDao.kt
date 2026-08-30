package com.example.marketplace.data.dao

import android.service.autofill.OnClickAction
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.marketplace.model.PendenteSycronizacao

@Dao
interface PendenteSycronizacaoDao{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(pendente: PendenteSycronizacao)

    @Query("SELECT * FROM pendente_sycronizacao ORDER BY criadoEm ASC")
    suspend fun listarPendetes(): List<PendenteSycronizacao>

    @Query("DELETE FROM pendente_sycronizacao WHERE id = :id")
    suspend fun remover(id: String)

}