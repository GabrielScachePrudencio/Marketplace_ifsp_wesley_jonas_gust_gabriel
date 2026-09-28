package com.example.marketplace.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.repository.RastreioEntregaRepository

class FilaEntregasViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)

        val repository = RastreioEntregaRepository(
            db.rastreioEntregaDao(),
            db.pendenteSycronizacaoDao()
        )

        @Suppress("UNCHECKED_CAST")
        return FilaEntregasViewModel(repository) as T
    }
}
