package com.example.marketplace.data.repository

import com.example.marketplace.data.dao.AvaliacaoProdutoDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.AvaliacaoProdutoRegras
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.service.FirebaseService
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime

class AvaliacaoProdutoRepository(private val avaliacaoDao: AvaliacaoProdutoDao) {

    private val colecao = FirebaseService.firestore.collection("avaliacoesProdutos")

    suspend fun avaliarProduto(
        produtoId: String,
        usuarioId: String,
        nota: Int,
        comentario: String
    ): AvaliacaoProduto {
        AvaliacaoProdutoRegras.validar(nota)

        val id = "${produtoId}_$usuarioId"
        val existente = colecao.document(id).get().await()
        if (existente.exists()) throw Exception("Você já avaliou este produto")

        val avaliacao = AvaliacaoProduto(
            id = id,
            produtoId = produtoId,
            usuarioId = usuarioId,
            nota = nota,
            comentario = comentario,
            data = System.currentTimeMillis(),
            dataCriacao = LocalDateTime.now()
        )

        salvarNoFirestore(avaliacao)
        avaliacaoDao.insert(avaliacao)

        return avaliacao
    }

    suspend fun excluirAvaliacao(avaliacao: AvaliacaoProduto) {
        colecao.document(avaliacao.id).delete().await()
        avaliacaoDao.deletar(avaliacao)
    }

    suspend fun buscarAvaliacaoPorId(id: String): AvaliacaoProduto? {
        avaliacaoDao.buscarPorId(id)?.let { return it }

        val doc = colecao.document(id).get().await()
        if (!doc.exists()) return null
        return avaliacaoDeDocumento(doc)
    }

    fun buscarAvaliacoesDoProduto(produtoId: String): Flow<List<AvaliacaoProduto>> =
        avaliacaoDao.listarPorProduto(produtoId)

    private suspend fun salvarNoFirestore(avaliacao: AvaliacaoProduto) {
        val dados = mapOf(
            "produtoId" to avaliacao.produtoId,
            "usuarioId" to avaliacao.usuarioId,
            "nota" to avaliacao.nota,
            "comentario" to avaliacao.comentario,
            "data" to avaliacao.data,
            "dataCriacao" to FirestoreDateConverter.paraMillis(avaliacao.dataCriacao)
        )
        colecao.document(avaliacao.id).set(dados).await()
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
