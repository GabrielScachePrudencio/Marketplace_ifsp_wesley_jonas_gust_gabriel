// controller/ProdutoListViewModelFactory.kt
package com.example.marketplace.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.data.repository.ProdutoRepository

class ProdutoListViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val repository = ProdutoRepository(db.produtoDao(), db.pendenteSycronizacaoDao())

        @Suppress("UNCHECKED_CAST")
        return ProdutoListViewModel(repository) as T
    }
}
