package com.example.marketplace.repository

import android.content.Context
import android.net.Uri
import com.example.marketplace.model.ComprovanteRetirada
import com.example.marketplace.service.FirebaseService
import com.example.marketplace.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ComprovanteRetiradaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val comprovantes = FirebaseService.firestore.collection("comprovantes_retirada")

    suspend fun salvar(
        vendaId: String,
        motoristaId: String,
        fotoUri: Uri,
        latitude: Double,
        longitude: Double
    ): ComprovanteRetirada {
        require(vendaId.isNotBlank()) { "Pedido inválido para registrar comprovante de retirada." }
        require(motoristaId.isNotBlank()) { "Motorista inválido para registrar comprovante de retirada." }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Localização inválida para registrar comprovante de retirada."
        }
        require(latitude != 0.0 || longitude != 0.0) {
            "A localização do comprovante de retirada não está disponível."
        }

        val fotoBase64 = withContext(Dispatchers.IO) {
            listOf(900, 720, 600, 480).firstNotNullOfOrNull { dimensao ->
                ImageUtils.uriParaBase64(
                    context = appContext,
                    uri = fotoUri,
                    maxDimensao = dimensao,
                    qualidade = 65
                )?.takeIf { it.length <= TAMANHO_MAXIMO_FOTO_BASE64 }
            }
        } ?: throw IllegalStateException(
            "Não foi possível preparar a foto da retirada. Tente tirar outra foto."
        )

        val comprovante = ComprovanteRetirada(
            vendaId = vendaId,
            motoristaId = motoristaId,
            fotoBase64 = fotoBase64,
            latitude = latitude,
            longitude = longitude,
            criadoEm = System.currentTimeMillis()
        )

        try {
            comprovantes.document(vendaId).set(
                mapOf(
                    "vendaId" to comprovante.vendaId,
                    "motoristaId" to comprovante.motoristaId,
                    "fotoBase64" to comprovante.fotoBase64,
                    "latitude" to comprovante.latitude,
                    "longitude" to comprovante.longitude,
                    "criadoEm" to comprovante.criadoEm
                )
            ).await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(
                "ComprovanteRetirada",
                "Falha ao salvar o comprovante de retirada no Firestore. vendaId=$vendaId",
                e
            )
            throw IllegalStateException(
                "Não foi possível salvar o comprovante de retirada: " +
                        (e.message ?: "erro no Firestore"),
                e
            )
        }

        return comprovante
    }

    suspend fun excluirPorVenda(vendaId: String) {
        require(vendaId.isNotBlank()) { "Pedido inválido para excluir comprovante de retirada." }
        try {
            comprovantes.document(vendaId).delete().await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(
                "ComprovanteRetirada",
                "Falha ao excluir o comprovante de retirada. vendaId=$vendaId",
                e
            )
            throw IllegalStateException(
                "Não foi possível excluir o comprovante de retirada: " +
                        (e.message ?: "erro no Firestore"),
                e
            )
        }
    }

    fun observarPorVenda(vendaId: String): Flow<ComprovanteRetirada?> = callbackFlow {
        val listener = comprovantes.document(vendaId).addSnapshotListener { snapshot, erro ->
            if (erro != null) {
                close(erro)
                return@addSnapshotListener
            }

            val comprovante = snapshot?.takeIf { it.exists() }?.let {
                val fotoBase64 = it.getString("fotoBase64")
                val latitude = it.getDouble("latitude")
                val longitude = it.getDouble("longitude")
                if (fotoBase64.isNullOrBlank() || latitude == null || longitude == null) {
                    null
                } else {
                    ComprovanteRetirada(
                        vendaId = it.getString("vendaId") ?: vendaId,
                        motoristaId = it.getString("motoristaId") ?: "",
                        fotoBase64 = fotoBase64,
                        latitude = latitude,
                        longitude = longitude,
                        criadoEm = it.getLong("criadoEm") ?: 0L
                    )
                }
            }
            trySend(comprovante)
        }
        awaitClose { listener.remove() }
    }

    private companion object {
        const val TAMANHO_MAXIMO_FOTO_BASE64 = 650_000
    }
}
