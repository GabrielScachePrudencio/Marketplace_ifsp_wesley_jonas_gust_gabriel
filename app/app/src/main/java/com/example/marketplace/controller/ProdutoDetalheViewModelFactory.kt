// controller/ProdutoDetalheViewModelFactory.kt
package com.example.marketplace.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.data.repository.AvaliacaoProdutoRepository
import com.example.marketplace.data.repository.ProdutoRepository
import com.example.marketplace.data.repository.VendaRepository

class ProdutoDetalheViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val produtoRepository = ProdutoRepository(db.produtoDao())
        val vendaRepository = VendaRepository(db.vendaDao(), produtoRepository)
        val avaliacaoRepository = AvaliacaoProdutoRepository(db.avaliacaoProdutoDao())

        @Suppress("UNCHECKED_CAST")
        return ProdutoDetalheViewModel(produtoRepository, vendaRepository, avaliacaoRepository) as T
    }
}
