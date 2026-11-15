package com.example.marketplace.service

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log em memória dos eventos do pipeline de notificações de entrega.
 * Exibido na tela de rastreamento para facilitar o diagnóstico.
 */
object LogNotificacoesEntrega {
    private const val TAG = "LogNotificacoes"
    private const val MAX_EVENTOS = 60

    data class EventoLog(
        val horario: String,
        val origem: String,
        val mensagem: String
    )

    private val _eventos = MutableStateFlow<List<EventoLog>>(emptyList())
    val eventos: StateFlow<List<EventoLog>> = _eventos.asStateFlow()

    private val formato = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun registrar(origem: String, mensagem: String) {
        val evento = EventoLog(
            horario = formato.format(Date()),
            origem = origem,
            mensagem = mensagem
        )
        _eventos.value = (_eventos.value + evento).takeLast(MAX_EVENTOS)
        Log.i(TAG, "[$origem] $mensagem")
    }

    @Synchronized
    fun limpar() {
        _eventos.value = emptyList()
    }
}
