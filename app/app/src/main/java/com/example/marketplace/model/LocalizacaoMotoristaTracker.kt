package com.example.marketplace.model

import android.annotation.SuppressLint
import android.content.Context
import com.example.marketplace.repository.LocalizacaoMotoristaRepository
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocalizacaoMotoristaTracker(
    private val context: Context,
    private val repository: LocalizacaoMotoristaRepository = LocalizacaoMotoristaRepository()
) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Guarda qual venda está sendo rastreada no momento
    private var vendaIdAtual: String? = null

    @SuppressLint("MissingPermission")
    fun iniciar(vendaId: String) {
        // Se já estiver rastreando a mesma venda, não faz nada
        if (vendaIdAtual == vendaId && callback != null) return

        // Para qualquer rastreamento anterior antes de começar um novo
        pararSeAtivo()

        vendaIdAtual = vendaId

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L // A cada 5 segundos
        ).setMinUpdateIntervalMillis(3000L).build()

        val novoCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                scope.launch {
                    repository.enviarLocalizacao(vendaId, loc.latitude, loc.longitude)
                }
            }
        }

        callback = novoCallback
        fusedClient.requestLocationUpdates(request, novoCallback, context.mainLooper)
    }

    fun pararSeAtivo() {
        // Remove as atualizações de GPS
        callback?.let { fusedClient.removeLocationUpdates(it) }
        callback = null

        // Se tínhamos uma venda ativa, limpa a localização dela no Firestore
        vendaIdAtual?.let { id ->
            scope.launch { repository.limparLocalizacao(id) }
        }
        vendaIdAtual = null
    }
}