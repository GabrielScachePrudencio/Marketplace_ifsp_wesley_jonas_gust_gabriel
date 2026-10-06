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

    /** Limite para o vídeo caber em poucas partes no Firestore (ver ProdutoRepository) */
    const val TAMANHO_MAXIMO_VIDEO_BYTES = 15 * 1024 * 1024
    const val DURACAO_MAXIMA_VIDEO_SEGUNDOS = 30

    fun validarVideo(tamanhoBytes: Int) {
        require(tamanhoBytes > 0) { "Vídeo inválido" }
        require(tamanhoBytes <= TAMANHO_MAXIMO_VIDEO_BYTES) {
            "O vídeo deve ter no máximo ${TAMANHO_MAXIMO_VIDEO_BYTES / (1024 * 1024)} MB"
        }
    }
}
