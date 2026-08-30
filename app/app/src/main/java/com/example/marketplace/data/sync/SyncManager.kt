package com.example.marketplace.data.sync

import com.example.marketplace.data.dao.PendenteSycronizacaoDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.model.AvaliacaoProduto
import com.example.marketplace.model.PendenteSycronizacao
import com.example.marketplace.model.Produto
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Veiculo
import com.example.marketplace.model.Venda
import com.example.marketplace.model.enums.OperacaoPendente
import com.example.marketplace.model.enums.TipoPendenteSyncronizacao
import com.example.marketplace.service.FirebaseService
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class SyncManager(
    private val pendenteSycronizacaoDao: PendenteSycronizacaoDao
) {

    private val gson = Gson()
    private val firestore = FirebaseService.firestore

    /**
     * Percorre a fila de pendentes e tenta reenviar cada um pro Firestore.
     * Remove da fila local só quando confirma sucesso.
     * Retorna quantos itens foram sincronizados com sucesso.
     */
    suspend fun sincronizarPendentes(): Int {
        val pendentes = pendenteSycronizacaoDao.listarPendetes()
        var sucessos = 0

        for (item in pendentes) {
            val ok = tentarSincronizar(item)
            if (ok) {
                pendenteSycronizacaoDao.remover(item.id)
                sucessos++
            }
        }

        return sucessos
    }

    private suspend fun tentarSincronizar(item: PendenteSycronizacao): Boolean {
        return when (item.tipo) {
            TipoPendenteSyncronizacao.PRODUTOS -> sincronizarProduto(item)
            TipoPendenteSyncronizacao.USUARIOS -> sincronizarUsuario(item)
            TipoPendenteSyncronizacao.VEICULOS -> sincronizarVeiculo(item)
            TipoPendenteSyncronizacao.VENDAS -> sincronizarVenda(item)
            TipoPendenteSyncronizacao.AVALIACAO -> sincronizarAvaliacao(item)
        }
    }

    private suspend fun sincronizarProduto(item: PendenteSycronizacao): Boolean {
        return withTimeoutOrNull(5000) {
            try {
                val colecao = firestore.collection("produtos")
                when (item.operacao) {
                    OperacaoPendente.CREATE, OperacaoPendente.UPDATE -> {
                        val produto = gson.fromJson(item.payloadJson, Produto::class.java)
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
                        colecao.document(item.id).set(dados).await()
                    }
                    OperacaoPendente.DELETE -> {
                        colecao.document(item.id).delete().await()
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private suspend fun sincronizarUsuario(item: PendenteSycronizacao): Boolean {
        return withTimeoutOrNull(5000) {
            try {
                val colecao = firestore.collection("usuarios")
                when (item.operacao) {
                    OperacaoPendente.CREATE -> {
                        val usuario = gson.fromJson(item.payloadJson, Usuario::class.java)
                        val dados = mapOf(
                            "uid" to usuario.uid,
                            "nome" to usuario.nome,
                            "email" to usuario.email,
                            "perfil" to usuario.perfil,
                            "cpf" to usuario.cpf,
                            "rua" to usuario.rua,
                            "numero" to usuario.numero,
                            "cidade" to usuario.cidade,
                            "estado" to usuario.estado,
                            "cep" to usuario.cep,
                            "negocianteId" to usuario.negocianteId,
                            "dataCriacao" to FirestoreDateConverter.paraMillis(usuario.dataCriacao)
                        )
                        colecao.document(item.id).set(dados).await()
                    }
                    OperacaoPendente.UPDATE -> {
                        // caso específico: vincularNegociante salva só {motoristaUid, negocianteId}
                        @Suppress("UNCHECKED_CAST")
                        val payload = gson.fromJson(item.payloadJson, Map::class.java) as Map<String, String>
                        val motoristaUid = payload["motoristaUid"] ?: item.id
                        val negocianteId = payload["negocianteId"] ?: ""
                        colecao.document(motoristaUid).update("negocianteId", negocianteId).await()
                    }
                    OperacaoPendente.DELETE -> {
                        colecao.document(item.id).delete().await()
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private suspend fun sincronizarVeiculo(item: PendenteSycronizacao): Boolean {
        return withTimeoutOrNull(5000) {
            try {
                val colecao = firestore.collection("veiculos")
                when (item.operacao) {
                    OperacaoPendente.CREATE, OperacaoPendente.UPDATE -> {
                        val veiculo = gson.fromJson(item.payloadJson, Veiculo::class.java)
                        val dados = mapOf(
                            "motoristaId" to veiculo.motoristaId,
                            "tipo" to veiculo.tipo,
                            "marca" to veiculo.marca,
                            "modelo" to veiculo.modelo,
                            "ano" to veiculo.ano,
                            "placa" to veiculo.placa,
                            "cor" to veiculo.cor,
                            "dataCriacao" to FirestoreDateConverter.paraMillis(veiculo.dataCriacao)
                        )
                        colecao.document(item.id).set(dados).await()
                    }
                    OperacaoPendente.DELETE -> {
                        colecao.document(item.id).delete().await()
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private suspend fun sincronizarVenda(item: PendenteSycronizacao): Boolean {
        return withTimeoutOrNull(5000) {
            try {
                val colecao = firestore.collection("vendas")
                when (item.operacao) {
                    OperacaoPendente.CREATE, OperacaoPendente.UPDATE -> {
                        val venda = gson.fromJson(item.payloadJson, Venda::class.java)
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
                        colecao.document(item.id).set(dados).await()
                    }
                    OperacaoPendente.DELETE -> {
                        colecao.document(item.id).delete().await()
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private suspend fun sincronizarAvaliacao(item: PendenteSycronizacao): Boolean {
        return withTimeoutOrNull(5000) {
            try {
                val colecao = firestore.collection("avaliacoesProdutos")
                when (item.operacao) {
                    OperacaoPendente.CREATE, OperacaoPendente.UPDATE -> {
                        val avaliacao = gson.fromJson(item.payloadJson, AvaliacaoProduto::class.java)
                        val dados = mapOf(
                            "produtoId" to avaliacao.produtoId,
                            "usuarioId" to avaliacao.usuarioId,
                            "nota" to avaliacao.nota,
                            "comentario" to avaliacao.comentario,
                            "data" to avaliacao.data,
                            "dataCriacao" to FirestoreDateConverter.paraMillis(avaliacao.dataCriacao)
                        )
                        colecao.document(item.id).set(dados).await()
                    }
                    OperacaoPendente.DELETE -> {
                        colecao.document(item.id).delete().await()
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }
}