package com.example.marketplace.data.repository

import com.example.marketplace.data.dao.VeiculoDao
import com.example.marketplace.data.local.FirestoreDateConverter
import com.example.marketplace.domain.VeiculoRegras
import com.example.marketplace.model.Veiculo
import com.example.marketplace.service.FirebaseService
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime

class VeiculoRepository(private val veiculoDao: VeiculoDao) {

    private val colecao = FirebaseService.firestore.collection("veiculos")

    suspend fun cadastrarVeiculo(
        motoristaId: String,
        tipo: String,
        marca: String,
        modelo: String,
        ano: Int,
        placa: String,
        cor: String
    ): Veiculo {
        VeiculoRegras.validar(motoristaId, placa, ano)

        val veiculo = Veiculo(
            id = colecao.document().id,
            motoristaId = motoristaId,
            tipo = tipo,
            marca = marca,
            modelo = modelo,
            ano = ano,
            placa = placa,
            cor = cor,
            dataCriacao = LocalDateTime.now()
        )

        salvarNoFirestore(veiculo)
        veiculoDao.insert(veiculo)

        return veiculo
    }

    suspend fun atualizarVeiculo(veiculo: Veiculo) {
        VeiculoRegras.validar(veiculo.motoristaId, veiculo.placa, veiculo.ano)

        salvarNoFirestore(veiculo)
        veiculoDao.update(veiculo)
    }

    suspend fun excluirVeiculo(veiculo: Veiculo) {
        colecao.document(veiculo.id).delete().await()
        veiculoDao.deletar(veiculo)
    }

    suspend fun buscarVeiculoPorId(id: String): Veiculo? {
        veiculoDao.buscarPorId(id)?.let { return it }

        val doc = colecao.document(id).get().await()
        if (!doc.exists()) return null
        return veiculoDeDocumento(doc)
    }

    fun buscarVeiculos(): Flow<List<Veiculo>> = veiculoDao.listarTodos()

    private suspend fun salvarNoFirestore(veiculo: Veiculo) {
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
        colecao.document(veiculo.id).set(dados).await()
    }

    private fun veiculoDeDocumento(doc: DocumentSnapshot): Veiculo {
        return Veiculo(
            id = doc.id,
            motoristaId = doc.getString("motoristaId") ?: "",
            tipo = doc.getString("tipo") ?: "",
            marca = doc.getString("marca") ?: "",
            modelo = doc.getString("modelo") ?: "",
            ano = (doc.getLong("ano") ?: 0L).toInt(),
            placa = doc.getString("placa") ?: "",
            cor = doc.getString("cor") ?: "",
            dataCriacao = FirestoreDateConverter.deMillis(doc.getLong("dataCriacao"))
        )
    }
}
