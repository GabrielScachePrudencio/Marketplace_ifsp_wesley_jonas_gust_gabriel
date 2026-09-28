package com.example.marketplace.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.repository.PontoReferenciaRepository
import com.example.marketplace.repository.RastreioEntregaRepository
import com.example.marketplace.repository.VendaRepository

class RastreamentoEntregaViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)

        val vendaRepository = VendaRepository(
            db.vendaDao(),
            db.pendenteSycronizacaoDao(),
            com.example.marketplace.repository.ProdutoRepository(db.produtoDao(), db.pendenteSycronizacaoDao()),
            RastreioEntregaRepository(db.rastreioEntregaDao(), db.pendenteSycronizacaoDao())
        )

        val rastreioEntregaRepository = RastreioEntregaRepository(
            db.rastreioEntregaDao(),
            db.pendenteSycronizacaoDao()
        )

        val pontoReferenciaRepository = PontoReferenciaRepository(
            db.pontoReferenciaDao(),
            db.pendenteSycronizacaoDao()
        )

        @Suppress("UNCHECKED_CAST")
        return RastreamentoEntregaViewModel(
            vendaRepository,
            rastreioEntregaRepository,
            pontoReferenciaRepository
        ) as T
    }
}
