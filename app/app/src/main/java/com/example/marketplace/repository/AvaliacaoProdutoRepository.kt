package com.example.marketplace.data.repository

import com.example.marketplace.data.dao.AvaliacaoProdutoDao
import com.example.marketplace.data.dao.PendenteSycronizacaoDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.AvaliacaoProdutoRegras
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.model.PendenteSycronizacao
import com.example.marketplace.model.enums.OperacaoPendente
import com.example.marketplace.model.enums.TipoPendenteSyncronizacao
import com.example.marketplace.service.FirebaseService
import com.google.firebase.firestore.DocumentSnapshot
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

class AvaliacaoProdutoRepository(
    private val avaliacaoDao: AvaliacaoProdutoDao,
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao
) {

    private val colecao = FirebaseService.firestore.collection("avaliacoesProdutos")
    private val gson = Gson()

    suspend fun avaliarProduto(
        produtoId: String,
        usuarioId: String,
        nota: Int,
        comentario: String
    ): AvaliacaoProduto {
        AvaliacaoProdutoRegras.validar(nota)

        val id = "${produtoId}_$usuarioId"

        // 1. Checa duplicidade: local primeiro (não depende de internet)
        val jaAvaliouLocal = avaliacaoDao.buscarPorId(id) != null
        if (jaAvaliouLocal) throw Exception("Você já avaliou este produto")

        // 2. Só confirma no Firestore se tiver internet; se estiver offline, segue sem bloquear
        val existeNoFirestore = withTimeoutOrNull(5000) {
            try {
                colecao.document(id).get().await().exists()
            } catch (e: Exception) {
                null // offline / erro: não conseguimos confirmar, não bloqueia o usuário
            }
        }
        if (existeNoFirestore == true) throw Exception("Você já avaliou este produto")

        val avaliacao = AvaliacaoProduto(
            id = id,
            produtoId = produtoId,
            usuarioId = usuarioId,
            nota = nota,
            comentario = comentario,
            data = System.currentTimeMillis(),
            dataCriacao = LocalDateTime.now()
        )

        val sucesso = salvarNoFirestore(avaliacao)

        avaliacaoDao.insert(avaliacao)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = avaliacao.id,
                    tipo = TipoPendenteSyncronizacao.AVALIACAO,
                    operacao = OperacaoPendente.CREATE,
                    payloadJson = gson.toJson(avaliacao)
                )
            )
        }

        return avaliacao
    }

    suspend fun excluirAvaliacao(avaliacao: AvaliacaoProduto) {
        val sucesso = withTimeoutOrNull(5000) {
            try {
                colecao.document(avaliacao.id).delete().await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false

        avaliacaoDao.deletar(avaliacao)

        if (!sucesso) {
            pendenteSycronizacaoDao.inserir(
                PendenteSycronizacao(
                    id = avaliacao.id,
                    tipo = TipoPendenteSyncronizacao.AVALIACAO,
                    operacao = OperacaoPendente.DELETE,
                    payloadJson = gson.toJson(avaliacao)
                )
            )
        }
    }

    suspend fun buscarAvaliacaoPorId(id: String): AvaliacaoProduto? {
        avaliacaoDao.buscarPorId(id)?.let { return it }

        return try {
            val doc = colecao.document(id).get().await()
            if (!doc.exists()) return null
            val avaliacao = avaliacaoDeDocumento(doc)
            avaliacaoDao.insert(avaliacao)
            avaliacao
        } catch (e: Exception) {
            null
        }
    }

    fun buscarAvaliacoesDoProduto(produtoId: String): Flow<List<AvaliacaoProduto>> =
        avaliacaoDao.listarPorProduto(produtoId)

    /** Sincroniza Firestore -> Room (chamar ao entrar na tela / puxar pra atualizar) */
    suspend fun sincronizarAvaliacoesDoProduto(produtoId: String) {
        try {
            val snapshot = colecao.whereEqualTo("produtoId", produtoId).get().await()
            for (doc in snapshot.documents) {
                if (doc.exists()) {
                    avaliacaoDao.insert(avaliacaoDeDocumento(doc))
                }
            }
        } catch (e: Exception) {
            // offline: mantém o que já tem localmente
        }
    }

    private suspend fun salvarNoFirestore(avaliacao: AvaliacaoProduto): Boolean {
        val dados = mapOf(
            "produtoId" to avaliacao.produtoId,
            "usuarioId" to avaliacao.usuarioId,
            "nota" to avaliacao.nota,
            "comentario" to avaliacao.comentario,
            "data" to avaliacao.data,
            "dataCriacao" to FirestoreDateConverter.paraMillis(avaliacao.dataCriacao)
        )

        return withTimeoutOrNull(5000) {
            try {
                colecao.document(avaliacao.id).set(dados).await()
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private fun avaliacaoDeDocumento(doc: DocumentSnapshot): AvaliacaoProduto {
        return AvaliacaoProduto(
            id = doc.id,
            produtoId = doc.getString("produtoId") ?: "",
            usuarioId = doc.getString("usuarioId") ?: "",
            nota = (doc.getLong("nota") ?: 0L).toInt(),
            comentario = doc.getString("comentario") ?: "",
            data = doc.getLong("data") ?: 0L,
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}