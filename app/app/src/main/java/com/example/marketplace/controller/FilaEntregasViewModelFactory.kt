package com.example.marketplace.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.repository.ProdutoRepository
import com.example.marketplace.repository.RastreioEntregaRepository
import com.example.marketplace.repository.VendaRepository

class FilaEntregasViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)

        val repository = RastreioEntregaRepository(
            db.rastreioEntregaDao(),
            db.pendenteSycronizacaoDao()
        )
        val produtoRepository = ProdutoRepository(
            db.produtoDao(),
            db.pendenteSycronizacaoDao()
        )
        val vendaRepository = VendaRepository(
            db.vendaDao(),
            db.pendenteSycronizacaoDao(),
            produtoRepository,
            repository
        )

        @Suppress("UNCHECKED_CAST")
        return FilaEntregasViewModel(repository, vendaRepository) as T
    }
}
