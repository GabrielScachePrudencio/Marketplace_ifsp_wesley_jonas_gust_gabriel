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

    // Guarda as vendas que estão sendo rastreadas no momento
    private var vendasIdsAtuais: Set<String> = emptySet()

    @SuppressLint("MissingPermission")
    fun iniciar(vendaId: String) {
        iniciar(listOf(vendaId))
    }

    @SuppressLint("MissingPermission")
    fun iniciar(vendasIds: List<String>) {
        val novoConjunto = vendasIds.filter { it.isNotBlank() }.toSet()
        if (novoConjunto.isEmpty()) {
            pararSeAtivo(limparNoServidor = true)
            return
        }

        // Se já estiver rastreando exatamente as mesmas vendas, não reinicia o GPS
        if (vendasIdsAtuais == novoConjunto && callback != null) return

        // Limpa no Firestore apenas as vendas que saíram da lista ativa (ex: foram entregues)
        val removidas = vendasIdsAtuais - novoConjunto
        removidas.forEach { id ->
            scope.launch { repository.limparLocalizacao(id) }
        }

        callback?.let { fusedClient.removeLocationUpdates(it) }
        callback = null
        vendasIdsAtuais = novoConjunto

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L // A cada 5 segundos
        ).setMinUpdateIntervalMillis(3000L).build()

        val novoCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val idsParaEnviar = vendasIdsAtuais
                scope.launch {
                    idsParaEnviar.forEach { id ->
                        repository.enviarLocalizacao(id, loc.latitude, loc.longitude)
                    }
                }
            }
        }

        callback = novoCallback
        fusedClient.requestLocationUpdates(request, novoCallback, context.mainLooper)
    }

    fun pararSeAtivo(limparNoServidor: Boolean = true) {
        // Remove as atualizações de GPS
        callback?.let { fusedClient.removeLocationUpdates(it) }
        callback = null

        // Se solicitado, limpa a localização das vendas ativas no Firestore
        if (limparNoServidor) {
            val idsParaLimpar = vendasIdsAtuais
            idsParaLimpar.forEach { id ->
                scope.launch { repository.limparLocalizacao(id) }
            }
        }
        vendasIdsAtuais = emptySet()
    }
}