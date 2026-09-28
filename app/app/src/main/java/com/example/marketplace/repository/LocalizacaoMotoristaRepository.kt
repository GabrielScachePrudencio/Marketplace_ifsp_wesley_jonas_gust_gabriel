package com.example.marketplace.repository

import com.example.marketplace.service.FirebaseService
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LocalizacaoMotoristaRepository {

    private val colecao = FirebaseService.firestore.collection("localizacoes_tempo_real")

    /**
     * Envia a localização atual do motorista pro Firestore.
     * Fire-and-forget: se falhar (sem internet, timeout), simplesmente descarta,
     * pois a próxima atualização do GPS (poucos segundos depois) já resolve.
     * Não faz sentido enfileirar posições antigas pra sincronizar depois.
     */
    suspend fun enviarLocalizacao(vendaId: String, latitude: Double, longitude: Double): Boolean {
        val dados = mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "timestamp" to System.currentTimeMillis()
        )

        return withTimeoutOrNull(5000) {
            try {
                colecao.document(vendaId).set(dados).await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    /** Chamado quando a entrega termina, cancela ou o app do motorista fecha o rastreio. */
    suspend fun limparLocalizacao(vendaId: String) {
        withTimeoutOrNull(3000) {
            try {
                colecao.document(vendaId).delete().await()
            } catch (e: Exception) {
                // silencioso: se falhar, o comprador só vai ver a última posição, sem problema
            }
        }
    }
}