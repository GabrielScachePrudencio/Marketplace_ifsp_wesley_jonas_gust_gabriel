package com.example.marketplace.domain

object ProdutoRegras {
    fun validar(
        titulo: String,
        descricao: String,
        categoria: String,
        preco: Double,
        quantidade: Int,
        vendedorId: String
    ) {
        require(vendedorId.isNotBlank()) { "vendedorId é obrigatório" }
        require(titulo.isNotBlank()) { "Título é obrigatório" }
        require(descricao.isNotBlank()) { "Descrição é obrigatória" }
        require(categoria.isNotBlank()) { "Categoria é obrigatória" }
        require(preco > 0) { "Preço deve ser maior que zero" }
        require(quantidade >= 0) { "Quantidade não pode ser negativa" }
    }
}
