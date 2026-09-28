// controller/VendaListViewModelFactory.kt
package com.example.marketplace.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.repository.ProdutoRepository
import com.example.marketplace.repository.RastreioEntregaRepository
import com.example.marketplace.repository.VendaRepository

class VendaListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val produtoRepository = ProdutoRepository(db.produtoDao(), db.pendenteSycronizacaoDao())
        val rastreioEntregaRepository = RastreioEntregaRepository(db.rastreioEntregaDao(), db.pendenteSycronizacaoDao())
        val vendaRepository = VendaRepository(db.vendaDao(), db.pendenteSycronizacaoDao(), produtoRepository, rastreioEntregaRepository)

        @Suppress("UNCHECKED_CAST")
        return VendaListViewModel(vendaRepository) as T
    }
}
