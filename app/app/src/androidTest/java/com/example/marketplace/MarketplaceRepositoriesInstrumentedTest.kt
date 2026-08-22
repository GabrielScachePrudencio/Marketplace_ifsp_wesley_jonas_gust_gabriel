package com.example.marketplace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.data.repository.AvaliacaoProdutoRepository
import com.example.marketplace.data.repository.ProdutoRepository
import com.example.marketplace.data.repository.VeiculoRepository
import com.example.marketplace.data.repository.VendaRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Testes reais: escrevem e leem do Firestore e do Room de verdade
 * (mesmo projeto Firebase/app do dispositivo/emulador). Sem mocks.
 */
@RunWith(AndroidJUnit4::class)
class MarketplaceRepositoriesInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = AppDatabase.getDatabase(context)

    private val produtoRepository = ProdutoRepository(db.produtoDao())
    private val veiculoRepository = VeiculoRepository(db.veiculoDao())
    private val vendaRepository = VendaRepository(db.vendaDao(), produtoRepository)
    private val avaliacaoRepository = AvaliacaoProdutoRepository(db.avaliacaoProdutoDao())

    @Test
    fun produto_criarBuscarAtualizarExcluir_persisteCorretamente() = runBlocking {
        val vendedorId = "teste_vendedor_${UUID.randomUUID()}"

        val produto = produtoRepository.criarProduto(
            vendedorId = vendedorId,
            titulo = "Produto Teste",
            descricao = "Descrição teste",
            categoria = "categoria-teste",
            preco = 99.9,
            quantidade = 10,
            imagens = ""
        )

        val encontrado = produtoRepository.buscarProdutoPorId(produto.id)
        assertNotNull(encontrado)
        assertEquals("Produto Teste", encontrado!!.titulo)
        assertEquals(99.9, encontrado.preco, 0.0001)
        assertEquals(10, encontrado.quantidade)
        assertEquals(vendedorId, encontrado.vendedorId)

        produtoRepository.atualizarProduto(encontrado.copy(quantidade = 5))
        val atualizado = produtoRepository.buscarProdutoPorId(produto.id)
        assertEquals(5, atualizado!!.quantidade)

        produtoRepository.excluirProduto(atualizado)
        val apagado = db.produtoDao().buscarPorId(produto.id)
        assertNull(apagado)
    }

    @Test
    fun veiculo_cadastrarBuscarExcluir_persisteCorretamente() = runBlocking {
        val motoristaId = "teste_motorista_${UUID.randomUUID()}"

        val veiculo = veiculoRepository.cadastrarVeiculo(
            motoristaId = motoristaId,
            tipo = "moto",
            marca = "Honda",
            modelo = "CG 160",
            ano = 2020,
            placa = "ABC1D23",
            cor = "preta"
        )

        val encontrado = veiculoRepository.buscarVeiculoPorId(veiculo.id)
        assertNotNull(encontrado)
        assertEquals("Honda", encontrado!!.marca)
        assertEquals(2020, encontrado.ano)

        veiculoRepository.excluirVeiculo(encontrado)
        assertNull(db.veiculoDao().buscarPorId(veiculo.id))
    }

    @Test
    fun venda_criarComEstoqueEAtualizarStatus_respeitaRegrasDeNegocio() = runBlocking {
        val vendedorId = "teste_vendedor_${UUID.randomUUID()}"
        val compradorId = "teste_comprador_${UUID.randomUUID()}"
        val motoristaId = "teste_motorista_${UUID.randomUUID()}"

        val produto = produtoRepository.criarProduto(
            vendedorId = vendedorId,
            titulo = "Produto p/ Venda",
            descricao = "Descrição",
            categoria = "categoria-teste",
            preco = 20.0,
            quantidade = 3,
            imagens = ""
        )

        val venda = vendaRepository.criarVenda(
            compradorId = compradorId,
            vendedorId = vendedorId,
            motoristaId = motoristaId,
            produtoId = produto.id,
            quantidade = 2
        )

        assertEquals(40.0, venda.valorTotal, 0.0001)
        assertEquals("PENDENTE", venda.status)

        val produtoAposVenda = produtoRepository.buscarProdutoPorId(produto.id)
        assertEquals(1, produtoAposVenda!!.quantidade)

        val emTransporte = vendaRepository.atualizarStatusVenda(venda.id, "EM_TRANSPORTE")
        assertEquals("EM_TRANSPORTE", emTransporte.status)

        try {
            vendaRepository.atualizarStatusVenda(venda.id, "PENDENTE")
            fail("Deveria rejeitar transição EM_TRANSPORTE -> PENDENTE")
        } catch (e: IllegalArgumentException) {
            // esperado
        }

        try {
            vendaRepository.criarVenda(compradorId, vendedorId, motoristaId, produto.id, 999)
            fail("Deveria rejeitar venda sem estoque suficiente")
        } catch (e: IllegalArgumentException) {
            // esperado
        }

        produtoRepository.excluirProduto(produtoAposVenda)
        db.vendaDao().deletar(vendaRepository.buscarVendaPorId(venda.id)!!)
    }

    @Test
    fun avaliacao_bloqueiaDuplicataParaMesmoUsuarioEProduto() = runBlocking {
        val vendedorId = "teste_vendedor_${UUID.randomUUID()}"
        val usuarioId = "teste_usuario_${UUID.randomUUID()}"

        val produto = produtoRepository.criarProduto(
            vendedorId = vendedorId,
            titulo = "Produto p/ Avaliação",
            descricao = "Descrição",
            categoria = "categoria-teste",
            preco = 10.0,
            quantidade = 1,
            imagens = ""
        )

        val avaliacao = avaliacaoRepository.avaliarProduto(
            produtoId = produto.id,
            usuarioId = usuarioId,
            nota = 5,
            comentario = "Muito bom"
        )

        assertEquals(5, avaliacao.nota)

        try {
            avaliacaoRepository.avaliarProduto(produto.id, usuarioId, 3, "Outra nota")
            fail("Deveria bloquear segunda avaliação do mesmo usuário no mesmo produto")
        } catch (e: Exception) {
            assertEquals("Você já avaliou este produto", e.message)
        }

        avaliacaoRepository.excluirAvaliacao(avaliacao)
        produtoRepository.excluirProduto(produto)
    }
}
