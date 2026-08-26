package com.example.marketplace.controller

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.data.repository.VeiculoRepository

class VeiculoViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        val db =
            AppDatabase.getDatabase(context)

        val repository =
            VeiculoRepository(
                db.veiculoDao()
            )

        @Suppress("UNCHECKED_CAST")
        return VeiculoViewModel(
            repository
        ) as T
    }
}
