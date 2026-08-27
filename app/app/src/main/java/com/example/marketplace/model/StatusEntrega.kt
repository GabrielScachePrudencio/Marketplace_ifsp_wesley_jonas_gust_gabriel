package com.example.marketplace.model

enum class StatusEntrega(val descricao: String) {
    PENDENTE("Pendente"),
    PRONTO_PARA_ENTREGA("Pronto para entrega"),
    A_CAMINHO("A caminho"),
    ENTREGUE("Entregue"),
    CANCELADA("Cancelada");

    companion object {
        fun deString(valor: String?): StatusEntrega {
            if (valor == null) return PENDENTE
            return when (valor.uppercase()) {
                "PENDENTE" -> PENDENTE
                "PRONTO_PARA_ENTREGA" -> PRONTO_PARA_ENTREGA
                "A_CAMINHO", "EM_TRANSPORTE" -> A_CAMINHO
                "ENTREGUE" -> ENTREGUE
                "CANCELADA" -> CANCELADA
                else -> PENDENTE
            }
        }
    }
}
