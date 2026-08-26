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

class VeiculoRepository(
    private val veiculoDao: VeiculoDao
) {

    private val colecao =
        FirebaseService.firestore.collection("veiculos")


    // =========================================================
    // CADASTRAR
    // =========================================================

    suspend fun cadastrarVeiculo(
        motoristaId: String,
        tipo: String,
        marca: String,
        modelo: String,
        ano: Int,
        placa: String,
        cor: String
    ): Veiculo {

        VeiculoRegras.validar(
            motoristaId = motoristaId,
            placa = placa,
            ano = ano
        )

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

        // Primeiro salva no Firebase
        salvarNoFirestore(veiculo)

        // Depois salva no banco local
        veiculoDao.insert(veiculo)

        return veiculo
    }


    // =========================================================
    // ATUALIZAR
    // =========================================================

    suspend fun atualizarVeiculo(
        veiculo: Veiculo
    ) {

        VeiculoRegras.validar(
            motoristaId = veiculo.motoristaId,
            placa = veiculo.placa,
            ano = veiculo.ano
        )

        // Firebase
        salvarNoFirestore(veiculo)

        // Local
        veiculoDao.update(veiculo)
    }


    // =========================================================
    // EXCLUIR
    // =========================================================

    suspend fun excluirVeiculo(
        veiculo: Veiculo
    ) {

        // Firebase
        colecao
            .document(veiculo.id)
            .delete()
            .await()

        // Local
        veiculoDao.deletar(veiculo)
    }


    // =========================================================
    // BUSCAR POR ID
    // =========================================================

    suspend fun buscarVeiculoPorId(
        id: String
    ): Veiculo? {

        // Primeiro procura no Room
        val local =
            veiculoDao.buscarPorId(id)

        if (local != null) {
            return local
        }

        // Se não encontrou localmente,
        // procura no Firebase
        val doc =
            colecao
                .document(id)
                .get()
                .await()

        if (!doc.exists()) {
            return null
        }

        val veiculo =
            veiculoDeDocumento(doc)

        // Salva o resultado no Room
        veiculoDao.insert(veiculo)

        return veiculo
    }


    // =========================================================
    // TODOS OS VEÍCULOS LOCAIS
    // =========================================================

    fun buscarVeiculos(): Flow<List<Veiculo>> {
        return veiculoDao.listarTodos()
    }


    // =========================================================
    // VEÍCULOS DE UM MOTORISTA
    // =========================================================

    fun buscarVeiculosDoMotorista(
        motoristaId: String
    ): Flow<List<Veiculo>> {

        return veiculoDao.listarPorMotorista(
            motoristaId
        )
    }


    // =========================================================
    // SINCRONIZAR TODOS
    // FIREBASE -> ROOM
    // =========================================================

    suspend fun sincronizarVeiculos() {

        val snapshot =
            colecao
                .get()
                .await()

        for (doc in snapshot.documents) {

            if (doc.exists()) {

                val veiculo =
                    veiculoDeDocumento(doc)

                veiculoDao.insert(veiculo)
            }
        }
    }


    // =========================================================
    // SINCRONIZAR MOTORISTA
    // FIREBASE -> ROOM
    // =========================================================

    suspend fun sincronizarVeiculosDoMotorista(
        motoristaId: String
    ) {

        val snapshot =
            colecao
                .whereEqualTo(
                    "motoristaId",
                    motoristaId
                )
                .get()
                .await()

        for (doc in snapshot.documents) {

            if (doc.exists()) {

                val veiculo =
                    veiculoDeDocumento(doc)

                veiculoDao.insert(veiculo)
            }
        }
    }


    // =========================================================
    // FIREBASE
    // =========================================================

    private suspend fun salvarNoFirestore(
        veiculo: Veiculo
    ) {

        val dados = mapOf(

            "motoristaId" to
                    veiculo.motoristaId,

            "tipo" to
                    veiculo.tipo,

            "marca" to
                    veiculo.marca,

            "modelo" to
                    veiculo.modelo,

            "ano" to
                    veiculo.ano,

            "placa" to
                    veiculo.placa,

            "cor" to
                    veiculo.cor,

            "dataCriacao" to
                    FirestoreDateConverter
                        .paraMillis(
                            veiculo.dataCriacao
                        )
        )

        colecao
            .document(veiculo.id)
            .set(dados)
            .await()
    }


    // =========================================================
    // FIRESTORE -> OBJETO VEICULO
    // =========================================================

    private fun veiculoDeDocumento(
        doc: DocumentSnapshot
    ): Veiculo {

        return Veiculo(

            id = doc.id,

            motoristaId =
            doc.getString(
                "motoristaId"
            ) ?: "",

            tipo =
            doc.getString(
                "tipo"
            ) ?: "",

            marca =
            doc.getString(
                "marca"
            ) ?: "",

            modelo =
            doc.getString(
                "modelo"
            ) ?: "",

            ano =
            (
                    doc.getLong("ano")
                        ?: 0L
                    ).toInt(),

            placa =
            doc.getString(
                "placa"
            ) ?: "",

            cor =
            doc.getString(
                "cor"
            ) ?: "",

            dataCriacao =
            FirestoreDateConverter
                .deMillis(
                    doc.getLong(
                        "dataCriacao"
                    )
                )
        )
    }
}
