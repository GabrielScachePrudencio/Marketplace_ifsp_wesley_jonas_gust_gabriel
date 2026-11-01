package com.example.marketplace.domain

import com.example.marketplace.model.Produto
import com.example.marketplace.model.enums.StatusEntrega

object VendaRegras {
    val STATUS_INICIAL = StatusEntrega.PENDENTE.name

    private val transicoesPermitidas: Map<StatusEntrega, Set<StatusEntrega>> = mapOf(
        StatusEntrega.PENDENTE to setOf(StatusEntrega.PRONTO_PARA_ENTREGA, StatusEntrega.A_CAMINHO, StatusEntrega.CANCELADA),
        StatusEntrega.PRONTO_PARA_ENTREGA to setOf(StatusEntrega.A_CAMINHO, StatusEntrega.PENDENTE, StatusEntrega.CANCELADA),
        StatusEntrega.A_CAMINHO to setOf(StatusEntrega.ENTREGUE, StatusEntrega.PRONTO_PARA_ENTREGA),
        StatusEntrega.ENTREGUE to setOf(StatusEntrega.A_CAMINHO)
    )

    fun validarCriacao(produto: Produto, quantidade: Int, vendedorId: String) {
        require(quantidade > 0) { "Quantidade deve ser maior que zero" }
        require(produto.vendedorId == vendedorId) { "vendedorId não corresponde ao produto" }
        require(produto.quantidade >= quantidade) { "Estoque insuficiente" }
    }

    fun calcularValorTotal(valorUnitario: Double, quantidade: Int): Double =
        valorUnitario * quantidade

    fun validarTransicao(
        statusAtualStr: String,
        novoStatusStr: String,
        perfil: String? = null,
        veiculoId: String? = null
    ) {
        val statusAtual = StatusEntrega.deString(statusAtualStr)
        val novoStatus = StatusEntrega.deString(novoStatusStr)

        val permitido = transicoesPermitidas[statusAtual]?.contains(novoStatus) == true
        require(permitido) { "Transição de status inválida: ${statusAtual.descricao} -> ${novoStatus.descricao}" }

        if (perfil != null) {
            when (perfil) {
                "negociador" -> {
                    val statusPermitidosNegociador = setOf(
                        StatusEntrega.PENDENTE,
                        StatusEntrega.PRONTO_PARA_ENTREGA,
                        StatusEntrega.CANCELADA
                    )
                    require(novoStatus in statusPermitidosNegociador) {
                        "O negociador só pode alterar para Pendente ou Pronto para entrega."
                    }
                }
                "motorista" -> {
                    val statusPermitidosMotorista = setOf(
                        StatusEntrega.PRONTO_PARA_ENTREGA,
                        StatusEntrega.A_CAMINHO,
                        StatusEntrega.ENTREGUE
                    )
                    require(novoStatus in statusPermitidosMotorista) {
                        "O motorista só pode alterar para Pronto para entrega, A caminho ou Entregue."
                    }
                    require(statusAtual != StatusEntrega.PENDENTE) {
                        "O motorista só pode assumir entregas que estejam 'Pronto para entrega'."
                    }
                    if (novoStatus == StatusEntrega.A_CAMINHO) {
                        require(!veiculoId.isNullOrBlank()) {
                            "Selecione um veículo para iniciar a entrega."
                        }
                    }
                }
                "comprador" -> {
                    require(novoStatus == StatusEntrega.CANCELADA) {
                        "O comprador só tem permissão para cancelar o pedido."
                    }
                    val statusCancelaveisComprador = setOf(
                        StatusEntrega.PENDENTE,
                        StatusEntrega.PRONTO_PARA_ENTREGA
                    )
                    require(statusAtual in statusCancelaveisComprador) {
                        "O pedido só pode ser cancelado enquanto ainda não saiu para entrega."
                    }
                }
            }
        }
    }
}
