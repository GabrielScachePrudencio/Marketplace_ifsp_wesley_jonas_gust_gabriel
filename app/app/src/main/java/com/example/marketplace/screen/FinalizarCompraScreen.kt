package com.example.marketplace.screen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.CompraUiState
import com.example.marketplace.controller.ProdutoDetalheViewModel
import com.example.marketplace.controller.ProdutoDetalheViewModelFactory
import com.example.marketplace.data.local.AppDatabase
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Venda
import com.example.marketplace.repository.UsuarioRepository
import com.example.marketplace.service.GeocodingService
import com.example.marketplace.util.ImagemProduto
import kotlinx.coroutines.launch

enum class MetodoPagamentoSimulado(
    val titulo: String,
    val subtitulo: String,
    val icone: ImageVector
) {
    PIX(
        titulo = "PIX",
        subtitulo = "Aprovação imediata • QR Code ou Copia e Cola",
        icone = Icons.Default.QrCode2
    ),
    CARTAO_CREDITO(
        titulo = "Cartão de Crédito",
        subtitulo = "Em até 6x sem juros • Aprovação na hora",
        icone = Icons.Default.CreditCard
    ),
    CARTAO_DEBITO(
        titulo = "Cartão de Débito",
        subtitulo = "Pagamento à vista • Débito online",
        icone = Icons.Default.AccountBalanceWallet
    ),
    BOLETO(
        titulo = "Boleto Bancário",
        subtitulo = "Compensação simulada imediata",
        icone = Icons.Default.Receipt
    )
}

data class ResumoCompraConfirmada(
    val venda: Venda,
    val formaPagamentoDescricao: String,
    val enderecoEntrega: String,
    val produtoImagemBase64: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalizarCompraScreen(
    usuario: Usuario,
    produtoId: String,
    quantidadeInicial: Int = 1,
    onVoltar: () -> Unit,
    onUsuarioAtualizado: (Usuario) -> Unit = {},
    onCompraConfirmada: (ResumoCompraConfirmada) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val viewModel: ProdutoDetalheViewModel = viewModel(
        factory = ProdutoDetalheViewModelFactory(context)
    )
    val db = remember(context) { AppDatabase.getDatabase(context) }
    val usuarioRepository = remember(db) {
        UsuarioRepository(db.usuarioDao(), db.pendenteSycronizacaoDao())
    }

    LaunchedEffect(produtoId) {
        viewModel.resetarCompra()
        viewModel.carregarProduto(produtoId, usuario.uid)
    }

    val produtoAtual by viewModel.produto.collectAsState()
    val compraUiState by viewModel.compraUiState.collectAsState()

    var quantidade by remember(produtoId, quantidadeInicial) {
        mutableIntStateOf(quantidadeInicial.coerceAtLeast(1))
    }

    // Endereço de entrega (pré-preenchido com os dados do comprador)
    var rua by remember(usuario) { mutableStateOf(usuario.rua) }
    var numero by remember(usuario) { mutableStateOf(usuario.numero) }
    var complemento by remember { mutableStateOf("") }
    var cidade by remember(usuario) { mutableStateOf(usuario.cidade) }
    var estado by remember(usuario) { mutableStateOf(usuario.estado) }
    var cep by remember(usuario) { mutableStateOf(usuario.cep) }
    var editandoEndereco by remember(usuario) {
        mutableStateOf(
            usuario.rua.isBlank() || usuario.numero.isBlank() || usuario.cidade.isBlank()
        )
    }

    // Forma de pagamento e campos simulados
    var metodoSelecionado by remember { mutableStateOf(MetodoPagamentoSimulado.PIX) }
    var numeroCartao by remember { mutableStateOf("") }
    var nomeTitular by remember(usuario.nome) { mutableStateOf(usuario.nome.uppercase()) }
    var validadeCartao by remember { mutableStateOf("") }
    var cvvCartao by remember { mutableStateOf("") }
    var parcelas by remember { mutableIntStateOf(1) }
    var menuParcelasAberto by remember { mutableStateOf(false) }

    var erroValidacao by remember { mutableStateOf<String?>(null) }
    var processandoCheckout by remember { mutableStateOf(false) }

    val enderecoFormatado = remember(rua, numero, complemento, cidade, estado, cep) {
        val logradouro = listOf(rua.trim(), numero.trim().takeIf { it.isNotBlank() }?.let { "nº $it" } ?: "")
            .filter { it.isNotBlank() }
            .joinToString(", ")
        val comp = complemento.trim().takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        val localidade = listOf(cidade.trim(), estado.trim().uppercase()).filter { it.isNotBlank() }.joinToString("/")
        val cepTxt = cep.trim().takeIf { it.isNotBlank() }?.let { " • CEP $it" } ?: ""
        "$logradouro$comp - $localidade$cepTxt"
    }

    val descricaoPagamentoSelecionado = remember(metodoSelecionado, parcelas, numeroCartao) {
        when (metodoSelecionado) {
            MetodoPagamentoSimulado.PIX -> "PIX (Aprovação imediata)"
            MetodoPagamentoSimulado.CARTAO_CREDITO -> {
                val finalCartao = numeroCartao.filter { it.isDigit() }.takeLast(4).ifBlank { "4242" }
                "Cartão de Crédito •••• $finalCartao (${parcelas}x sem juros)"
            }
            MetodoPagamentoSimulado.CARTAO_DEBITO -> {
                val finalCartao = numeroCartao.filter { it.isDigit() }.takeLast(4).ifBlank { "8891" }
                "Cartão de Débito •••• $finalCartao (À vista)"
            }
            MetodoPagamentoSimulado.BOLETO -> "Boleto Bancário (À vista)"
        }
    }

    LaunchedEffect(compraUiState) {
        val estado = compraUiState
        if (estado is CompraUiState.Sucesso) {
            processandoCheckout = false
            val vendaCriada = estado.venda
            val fotoProduto = produtoAtual?.imagens?.firstOrNull()
            viewModel.resetarCompra()
            onCompraConfirmada(
                ResumoCompraConfirmada(
                    venda = vendaCriada,
                    formaPagamentoDescricao = descricaoPagamentoSelecionado,
                    enderecoEntrega = enderecoFormatado,
                    produtoImagemBase64 = fotoProduto
                )
            )
        } else if (estado is CompraUiState.Erro) {
            processandoCheckout = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Finalizar Compra", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color(0xFF2E7D32)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Checkout Seguro • MarketPlace IFSP",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVoltar, enabled = !processandoCheckout) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        val produto = produtoAtual
        if (produto == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LaunchedEffect(produto.quantidade) {
            if (produto.quantidade > 0 && quantidade > produto.quantidade) {
                quantidade = produto.quantidade
            }
        }

        val subtotal = produto.preco * quantidade
        val codigoPixSimulado = remember(produto.id, quantidade) {
            "00020126580014BR.GOV.BCB.PIX0136marketplace-ifsp-${produto.id.take(8)}5204000053039865406${"%.2f".format(java.util.Locale.US, subtotal)}5802BR"
        }
        val codigoBoletoSimulado = remember(produto.id, quantidade) {
            "23793.38128 60000.000003 00000.000400 1 954300000${(subtotal * 100).toInt()}"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== 1. ITEM E QUANTIDADE ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "1. Revisão do Item e Quantidade",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ImagemProduto(
                            fotoBase64 = produto.imagens.firstOrNull(),
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = produto.titulo,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (produto.categoria.isNotBlank()) {
                                Text(
                                    text = produto.categoria,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "R$ %.2f / un.".format(produto.preco),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${produto.quantidade} unidade(s) disponíveis em estoque",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Quantidade de itens",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = { if (quantidade > 1) quantidade-- },
                                    enabled = quantidade > 1 && !processandoCheckout,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuir")
                                }
                                Text(
                                    text = quantidade.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                FilledTonalIconButton(
                                    onClick = { if (quantidade < produto.quantidade) quantidade++ },
                                    enabled = quantidade < produto.quantidade && !processandoCheckout,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Aumentar")
                                }
                            }
                        }
                    }
                }
            }

            // ==================== 2. ENDEREÇO DE ENTREGA ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "2. Endereço de Entrega",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { editandoEndereco = !editandoEndereco }) {
                            Icon(
                                Icons.Default.EditLocationAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (editandoEndereco) "Concluir" else "Alterar")
                        }
                    }

                    HorizontalDivider()

                    if (!editandoEndereco && rua.isNotBlank() && numero.isNotBlank() && cidade.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Destinatário: ${usuario.nome}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = enderecoFormatado,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = cep,
                            onValueChange = { input ->
                                if (input.filter { it.isDigit() }.length <= 8) cep = input
                            },
                            label = { Text("CEP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = rua,
                                onValueChange = { rua = it },
                                label = { Text("Rua / Logradouro *") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = numero,
                                onValueChange = { numero = it },
                                label = { Text("Nº *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = complemento,
                            onValueChange = { complemento = it },
                            label = { Text("Complemento / Ponto de referência (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cidade,
                                onValueChange = { cidade = it },
                                label = { Text("Cidade *") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = estado,
                                onValueChange = { if (it.length <= 2) estado = it.uppercase() },
                                label = { Text("UF *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ==================== 3. FORMA DE PAGAMENTO ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "3. Forma de Pagamento",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    MetodoPagamentoSimulado.entries.forEach { metodo ->
                        val selecionado = metodoSelecionado == metodo
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selecionado) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (selecionado) 2.dp else 1.dp,
                                color = if (selecionado) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    metodoSelecionado = metodo
                                    erroValidacao = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selecionado,
                                    onClick = {
                                        metodoSelecionado = metodo
                                        erroValidacao = null
                                    }
                                )
                                Icon(
                                    imageVector = metodo.icone,
                                    contentDescription = null,
                                    tint = if (selecionado) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = metodo.titulo,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = metodo.subtitulo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Detalhes específicos do método selecionado
                    when (metodoSelecionado) {
                        MetodoPagamentoSimulado.PIX -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.QrCode2,
                                        contentDescription = "QR Code PIX",
                                        modifier = Modifier.size(88.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Chave PIX Copia e Cola (Simulação)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = codigoPixSimulado,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(codigoPixSimulado))
                                            Toast.makeText(
                                                context,
                                                "Código PIX copiado!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copiar código PIX")
                                    }
                                }
                            }
                        }

                        MetodoPagamentoSimulado.CARTAO_CREDITO,
                        MetodoPagamentoSimulado.CARTAO_DEBITO -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Dados do Cartão",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        TextButton(
                                            onClick = {
                                                numeroCartao = "4532117890124242"
                                                if (nomeTitular.isBlank()) {
                                                    nomeTitular = usuario.nome.uppercase().ifBlank { "COMPRADOR IFSP" }
                                                }
                                                validadeCartao = "12/29"
                                                cvvCartao = "789"
                                                erroValidacao = null
                                            }
                                        ) {
                                            Text("Preencher cartão de teste")
                                        }
                                    }

                                    OutlinedTextField(
                                        value = numeroCartao,
                                        onValueChange = { input ->
                                            val digitos = input.filter { it.isDigit() }.take(16)
                                            numeroCartao = digitos
                                        },
                                        label = { Text("Número do cartão (16 dígitos)") },
                                        placeholder = { Text("4532 1178 9012 4242") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = nomeTitular,
                                        onValueChange = { nomeTitular = it.uppercase() },
                                        label = { Text("Nome impresso no cartão") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = validadeCartao,
                                            onValueChange = { input ->
                                                val limpo = input.filter { it.isDigit() || it == '/' }.take(5)
                                                validadeCartao = limpo
                                            },
                                            label = { Text("Validade (MM/AA)") },
                                            placeholder = { Text("12/29") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = cvvCartao,
                                            onValueChange = { input ->
                                                cvvCartao = input.filter { it.isDigit() }.take(4)
                                            },
                                            label = { Text("CVV") },
                                            placeholder = { Text("123") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    if (metodoSelecionado == MetodoPagamentoSimulado.CARTAO_CREDITO) {
                                        ExposedDropdownMenuBox(
                                            expanded = menuParcelasAberto,
                                            onExpandedChange = { menuParcelasAberto = it }
                                        ) {
                                            val valorParcela = subtotal / parcelas
                                            OutlinedTextField(
                                                value = "${parcelas}x de R$ %.2f sem juros".format(valorParcela),
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Parcelamento") },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuParcelasAberto)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            )
                                            ExposedDropdownMenu(
                                                expanded = menuParcelasAberto,
                                                onDismissRequest = { menuParcelasAberto = false }
                                            ) {
                                                (1..6).forEach { opcao ->
                                                    val vParcela = subtotal / opcao
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text("${opcao}x de R$ %.2f sem juros".format(vParcela))
                                                        },
                                                        onClick = {
                                                            parcelas = opcao
                                                            menuParcelasAberto = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        MetodoPagamentoSimulado.BOLETO -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Linha Digitável (Simulação)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = codigoBoletoSimulado,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(codigoBoletoSimulado))
                                            Toast.makeText(
                                                context,
                                                "Linha digitável copiada!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copiar código de barras")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==================== 4. RESUMO FINANCEIRO E CONFIRMAÇÃO ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "4. Resumo do Pedido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Produtos ($quantidade item/itens)")
                        Text("R$ %.2f".format(subtotal))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frete (Entrega Parceira IFSP)")
                        Text(
                            "Grátis",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Forma de pagamento")
                        Text(
                            metodoSelecionado.titulo,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total a pagar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "R$ %.2f".format(subtotal),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val mensagemErro = erroValidacao ?: (compraUiState as? CompraUiState.Erro)?.mensagem
                    if (mensagemErro != null) {
                        Text(
                            text = mensagemErro,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            erroValidacao = null
                            if (rua.isBlank() || numero.isBlank() || cidade.isBlank() || estado.isBlank()) {
                                editandoEndereco = true
                                erroValidacao = "Por favor, preencha o endereço de entrega completo (Rua, Número, Cidade e UF)."
                                return@Button
                            }
                            if (
                                metodoSelecionado == MetodoPagamentoSimulado.CARTAO_CREDITO ||
                                metodoSelecionado == MetodoPagamentoSimulado.CARTAO_DEBITO
                            ) {
                                if (numeroCartao.length < 12 || nomeTitular.isBlank() || validadeCartao.length < 4 || cvvCartao.length < 3) {
                                    erroValidacao = "Preencha os dados do cartão ou toque em 'Preencher cartão de teste'."
                                    return@Button
                                }
                            }

                            processandoCheckout = true
                            scope.launch {
                                // Se o comprador atualizou o endereço no checkout, persiste no perfil para o rastreio
                                val enderecoMudou = rua.trim() != usuario.rua ||
                                    numero.trim() != usuario.numero ||
                                    cidade.trim() != usuario.cidade ||
                                    estado.trim() != usuario.estado ||
                                    cep.trim() != usuario.cep
                                if (enderecoMudou) {
                                    try {
                                        val coord = GeocodingService.buscarCoordenadas(
                                            rua = rua.trim(),
                                            numero = numero.trim(),
                                            cidade = cidade.trim(),
                                            estado = estado.trim(),
                                            cep = cep.trim()
                                        )
                                        val atualizado = usuarioRepository.atualizarPerfil(
                                            usuario.copy(
                                                rua = rua.trim(),
                                                numero = numero.trim(),
                                                cidade = cidade.trim(),
                                                estado = estado.trim(),
                                                cep = cep.trim(),
                                                latitude = coord?.latitude ?: usuario.latitude,
                                                longitude = coord?.longitude ?: usuario.longitude
                                            )
                                        )
                                        onUsuarioAtualizado(atualizado)
                                    } catch (_: Exception) {
                                    }
                                }
                                viewModel.comprar(
                                    compradorId = usuario.uid,
                                    quantidade = quantidade,
                                    compradorNome = usuario.nome
                                )
                            }
                        },
                        enabled = !processandoCheckout && compraUiState !is CompraUiState.Loading && produto.quantidade > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (processandoCheckout || compraUiState is CompraUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Confirmando pagamento e pedido...")
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Confirmar e Finalizar Compra",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
