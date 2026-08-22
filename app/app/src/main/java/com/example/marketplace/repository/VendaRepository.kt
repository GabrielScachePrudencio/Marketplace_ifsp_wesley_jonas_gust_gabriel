package com.example.marketplace.data.repository

import com.example.marketplace.data.dao.VendaDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.VendaRegras
import com.example.marketplace.model.Venda
import com.example.marketplace.service.FirebaseService
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime

class VendaRepository(
    private val vendaDao: VendaDao,
    private val produtoRepository: ProdutoRepository
) {

    private val colecao = FirebaseService.firestore.collection("vendas")

    suspend fun criarVenda(
        compradorId: String,
        vendedorId: String,
        motoristaId: String,
        produtoId: String,
        quantidade: Int
    ): Venda {
        val produto = produtoRepository.buscarProdutoPorId(produtoId)
            ?: throw Exception("Produto não encontrado")

        VendaRegras.validarCriacao(produto, quantidade, vendedorId)

        val valorUnitario = produto.preco
        val valorTotal = VendaRegras.calcularValorTotal(valorUnitario, quantidade)

        val venda = Venda(
            id = colecao.document().id,
            compradorId = compradorId,
            vendedorId = vendedorId,
            motoristaId = motoristaId,
            produtoId = produtoId,
            quantidade = quantidade,
            valorUnitario = valorUnitario,
            valorTotal = valorTotal,
            status = VendaRegras.STATUS_INICIAL,
            data = System.currentTimeMillis(),
            dataCriacao = LocalDateTime.now()
        )

        salvarNoFirestore(venda)
        vendaDao.insert(venda)

        produtoRepository.atualizarProduto(produto.copy(quantidade = produto.quantidade - quantidade))

        return venda
    }

    suspend fun atualizarStatusVenda(id: String, novoStatus: String): Venda {
        val venda = buscarVendaPorId(id) ?: throw Exception("Venda não encontrada")
        VendaRegras.validarTransicao(venda.status, novoStatus)

        val atualizada = venda.copy(status = novoStatus)
        salvarNoFirestore(atualizada)
        vendaDao.update(atualizada)

        return atualizada
    }

    suspend fun buscarVendaPorId(id: String): Venda? {
        vendaDao.buscarPorId(id)?.let { return it }

        val doc = colecao.document(id).get().await()
        if (!doc.exists()) return null
        return vendaDeDocumento(doc)
    }

    fun buscarVendas(): Flow<List<Venda>> = vendaDao.listarTodos()

    private suspend fun salvarNoFirestore(venda: Venda) {
        val dados = mapOf(
            "compradorId" to venda.compradorId,
            "vendedorId" to venda.vendedorId,
            "motoristaId" to venda.motoristaId,
            "produtoId" to venda.produtoId,
            "quantidade" to venda.quantidade,
            "valorUnitario" to venda.valorUnitario,
            "valorTotal" to venda.valorTotal,
            "status" to venda.status,
            "data" to venda.data,
            "dataCriacao" to FirestoreDateConverter.paraMillis(venda.dataCriacao)
        )
        colecao.document(venda.id).set(dados).await()
    }

    private fun vendaDeDocumento(doc: DocumentSnapshot): Venda {
        return Venda(
            id = doc.id,
            compradorId = doc.getString("compradorId") ?: "",
            vendedorId = doc.getString("vendedorId") ?: "",
            motoristaId = doc.getString("motoristaId") ?: "",
            produtoId = doc.getString("produtoId") ?: "",
            quantidade = (doc.getLong("quantidade") ?: 0L).toInt(),
            valorUnitario = doc.getDouble("valorUnitario") ?: 0.0,
            valorTotal = doc.getDouble("valorTotal") ?: 0.0,
            status = doc.getString("status") ?: VendaRegras.STATUS_INICIAL,
            data = doc.getLong("data") ?: 0L,
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}
