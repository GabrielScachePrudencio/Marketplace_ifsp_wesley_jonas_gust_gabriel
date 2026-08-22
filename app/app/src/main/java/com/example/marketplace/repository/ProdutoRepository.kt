package com.example.marketplace.data.repository

import com.example.marketplace.data.dao.ProdutoDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.ProdutoRegras
import com.example.marketplace.model.Produto
import com.example.marketplace.service.FirebaseService
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime

class ProdutoRepository(private val produtoDao: ProdutoDao) {

    private val colecao = FirebaseService.firestore.collection("produtos")

    suspend fun criarProduto(
        vendedorId: String,
        titulo: String,
        descricao: String,
        categoria: String,
        preco: Double,
        quantidade: Int,
        imagens: String
    ): Produto {
        ProdutoRegras.validar(titulo, descricao, categoria, preco, quantidade, vendedorId)

        val produto = Produto(
            id = colecao.document().id,
            vendedorId = vendedorId,
            titulo = titulo,
            descricao = descricao,
            categoria = categoria,
            preco = preco,
            quantidade = quantidade,
            imagens = imagens,
            dataCriacao = LocalDateTime.now()
        )

        salvarNoFirestore(produto)
        produtoDao.insert(produto)

        return produto
    }

    suspend fun atualizarProduto(produto: Produto) {
        ProdutoRegras.validar(produto.titulo, produto.descricao, produto.categoria, produto.preco, produto.quantidade, produto.vendedorId)

        salvarNoFirestore(produto)
        produtoDao.update(produto)
    }

    suspend fun excluirProduto(produto: Produto) {
        colecao.document(produto.id).delete().await()
        produtoDao.deletar(produto)
    }

    suspend fun buscarProdutoPorId(id: String): Produto? {
        produtoDao.buscarPorId(id)?.let { return it }

        val doc = colecao.document(id).get().await()
        if (!doc.exists()) return null
        return produtoDeDocumento(doc)
    }

    fun buscarProdutos(): Flow<List<Produto>> = produtoDao.listarTodos()

    private suspend fun salvarNoFirestore(produto: Produto) {
        val dados = mapOf(
            "vendedorId" to produto.vendedorId,
            "titulo" to produto.titulo,
            "descricao" to produto.descricao,
            "categoria" to produto.categoria,
            "preco" to produto.preco,
            "quantidade" to produto.quantidade,
            "imagens" to produto.imagens,
            "dataCriacao" to FirestoreDateConverter.paraMillis(produto.dataCriacao)
        )
        colecao.document(produto.id).set(dados).await()
    }

    private fun produtoDeDocumento(doc: DocumentSnapshot): Produto {
        return Produto(
            id = doc.id,
            vendedorId = doc.getString("vendedorId") ?: "",
            titulo = doc.getString("titulo") ?: "",
            descricao = doc.getString("descricao") ?: "",
            categoria = doc.getString("categoria") ?: "",
            preco = doc.getDouble("preco") ?: 0.0,
            quantidade = (doc.getLong("quantidade") ?: 0L).toInt(),
            imagens = doc.getString("imagens") ?: "",
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}
