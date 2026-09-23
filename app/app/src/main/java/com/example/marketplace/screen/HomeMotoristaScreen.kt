package com.example.marketplace.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marketplace.controller.UsuarioViewModel
import com.example.marketplace.controller.UsuarioViewModelFactory
import com.example.marketplace.controller.VeiculoViewModel
import com.example.marketplace.controller.VeiculoViewModelFactory
import com.example.marketplace.controller.VendaListViewModel
import com.example.marketplace.controller.VendaListViewModelFactory
import com.example.marketplace.domain.VeiculoRegras
import com.example.marketplace.util.AvatarUsuario
import com.example.marketplace.util.ImageUtils
import com.example.marketplace.model.enums.StatusEntrega
import com.example.marketplace.model.Usuario
import com.example.marketplace.model.Veiculo
import com.example.marketplace.model.Venda
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMotoristaScreen(
    usuario: Usuario,
    veiculos: List<Veiculo>,
    onCadastrarVeiculo: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val usuarioViewModel: UsuarioViewModel = viewModel(
        factory = UsuarioViewModelFactory(context)
    )
    val vendaViewModel: VendaListViewModel = viewModel(
        factory = VendaListViewModelFactory(context)
    )
    val veiculoViewModel: VeiculoViewModel = viewModel(
        factory = VeiculoViewModelFactory(context)
    )

    val negociantes by usuarioViewModel.negociantes.collectAsState()
    val vinculando by usuarioViewModel.vinculando.collectAsState()
    val vendas by vendaViewModel.vendas.collectAsState()

    // Conjunto reativo com os IDs de todos os parceiros vinculados (ilimitado)
    var parceirosVinculadosIds by remember(usuario) {
        mutableStateOf(usuario.todosNegociantesIds().toSet())
    }

    var fotoPerfilAtual by remember(usuario.fotoPerfil) {
        mutableStateOf(usuario.fotoPerfil)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = ImageUtils.uriParaBase64(context, uri, maxDimensao = 300, qualidade = 70)
            if (base64 != null) {
                usuarioViewModel.atualizarFotoPerfil(usuario.uid, base64) {
                    fotoPerfilAtual = base64
                }
            }
        }
    }

    // Estados para controle de modais e ações
    var veiculoEmEdicao by remember { mutableStateOf<Veiculo?>(null) }
    var veiculoParaExcluir by remember { mutableStateOf<Veiculo?>(null) }
    var vendaDetalheModal by remember { mutableStateOf<Venda?>(null) }

    // Cache local de clientes compradores para exibição de nome e endereço de entrega
    var clientesMap by remember { mutableStateOf<Map<String, Usuario>>(emptyMap()) }

    LaunchedEffect(Unit) {
        usuarioViewModel.carregarNegociantes()
    }

    // Carrega dados completos dos clientes compradores das vendas em exibição
    LaunchedEffect(vendas) {
        val compradorIds = vendas.map { it.compradorId }.filter { it.isNotBlank() }.distinct()
        compradorIds.forEach { uid ->
            if (!clientesMap.containsKey(uid)) {
                usuarioViewModel.carregarUsuario(uid) { cliente ->
                    if (cliente != null) {
                        clientesMap = clientesMap + (uid to cliente)
                    }
                }
            }
        }
    }

    // Filtra entregas relevantes para o motorista:
    // - Pedidos em PRONTO_PARA_ENTREGA pertencentes a QUALQUER negociante vinculado
    // - Pedidos assumidos por este motorista em A_CAMINHO ou ENTREGUE
    val entregasMotorista = vendas.filter { venda ->
        val status = venda.statusEntrega
        val ehDeParceiro = parceirosVinculadosIds.contains(venda.vendedorId)
        val ehDesteMotorista = venda.motoristaId == usuario.uid

        (ehDeParceiro && status == StatusEntrega.PRONTO_PARA_ENTREGA) ||
                (ehDesteMotorista && (status == StatusEntrega.A_CAMINHO || status == StatusEntrega.ENTREGUE)) ||
                (ehDeParceiro && (status == StatusEntrega.A_CAMINHO || status == StatusEntrega.ENTREGUE))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Área do Motorista") },
                actions = {
                    TextButton(onClick = onLogout) { Text("Sair") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCadastrarVeiculo) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarUsuario(
                        fotoBase64 = fotoPerfilAtual.ifEmpty { null },
                        nome = usuario.nome,
                        modifier = Modifier.size(56.dp),
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    Column {
                        Text("Olá, ${usuario.nome}!", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Toque na foto para alterá-la",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ------------------------------------------------
            // VÍNCULOS COM NEGOCIANTES (MÚLTIPLOS / ILIMITADOS)
            // ------------------------------------------------
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Negociantes Parceiros", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${parceirosVinculadosIds.size} vinculado(s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Filie-se a múltiplos negociantes para receber e entregar pedidos de várias lojas simultaneamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        // Parceiros Atualmente Vinculados
                        Text("Parceiros Vinculados:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))

                        if (parceirosVinculadosIds.isEmpty()) {
                            Text(
                                "Você não está vinculado a nenhum parceiro no momento. Vincule-se abaixo para liberar entregas.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            parceirosVinculadosIds.forEach { parceiroId ->
                                val parceiro = negociantes.find { it.uid == parceiroId }
                                val nomeParceiro = parceiro?.nome ?: "Parceiro #${parceiroId.take(6)}"

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(nomeParceiro, fontWeight = FontWeight.SemiBold)
                                            if (parceiro?.email?.isNotBlank() == true) {
                                                Text(parceiro.email, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        OutlinedButton(
                                            enabled = !vinculando,
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            onClick = {
                                                usuarioViewModel.desvincularNegociante(
                                                    motoristaUid = usuario.uid,
                                                    negocianteId = parceiroId
                                                ) {
                                                    parceirosVinculadosIds = parceirosVinculadosIds - parceiroId
                                                }
                                            }
                                        ) {
                                            Text("Desvincular")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Divider()
                        Spacer(Modifier.height(10.dp))

                        // Parceiros Disponíveis para Vínculo
                        val disponiveis = negociantes.filter { !parceirosVinculadosIds.contains(it.uid) }
                        Text("Disponíveis para Vínculo:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))

                        if (disponiveis.isEmpty()) {
                            Text(
                                if (negociantes.isEmpty()) "Carregando negociantes..."
                                else "Você já está vinculado a todos os parceiros cadastrados!",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            disponiveis.forEach { negociante ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(negociante.nome, fontWeight = FontWeight.Medium)
                                        if (negociante.cidade.isNotBlank()) {
                                            Text("${negociante.cidade} - ${negociante.estado}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Button(
                                        enabled = !vinculando,
                                        onClick = {
                                            usuarioViewModel.vincularNegociante(
                                                motoristaUid = usuario.uid,
                                                negocianteId = negociante.uid
                                            ) {
                                                parceirosVinculadosIds = parceirosVinculadosIds + negociante.uid
                                            }
                                        }
                                    ) {
                                        Text("Vincular")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------
            // MEUS VEÍCULOS (COM EDIÇÃO E EXCLUSÃO)
            // ------------------------------------------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Meus veículos", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onCadastrarVeiculo) {
                        Text("+ Novo Veículo")
                    }
                }
            }

            if (veiculos.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nenhum veículo cadastrado ainda (toque no + para cadastrar)")
                        }
                    }
                }
            } else {
                items(veiculos, key = { it.id }) { veiculo ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${veiculo.marca} ${veiculo.modelo}".trim(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text("Tipo: ${veiculo.tipo} • Placa: ${veiculo.placa}", style = MaterialTheme.typography.bodySmall)
                                    Text("Ano: ${veiculo.ano} • Cor: ${veiculo.cor}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { veiculoEmEdicao = veiculo },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Editar")
                                    }
                                    OutlinedButton(
                                        onClick = { veiculoParaExcluir = veiculo },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Excluir")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------
            // ENTREGAS / PEDIDOS
            // ------------------------------------------------
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Entregas de Pedidos", style = MaterialTheme.typography.titleMedium)
                    if (entregasMotorista.isNotEmpty()) {
                        Text(
                            "${entregasMotorista.size} pedido(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (parceirosVinculadosIds.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(16.dp)) {
                            Text("Vincule-se a pelo menos um parceiro acima para visualizar pedidos para entrega.")
                        }
                    }
                }
            } else if (entregasMotorista.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhuma entrega disponível no momento para seus parceiros vinculados.")
                        }
                    }
                }
            } else {
                items(entregasMotorista, key = { it.id }) { venda ->
                    val cliente = clientesMap[venda.compradorId]
                    val parceiroVendedor = negociantes.find { it.uid == venda.vendedorId }

                    MotoristaEntregaCard(
                        venda = venda,
                        cliente = cliente,
                        parceiroVendedor = parceiroVendedor,
                        usuario = usuario,
                        veiculos = veiculos,
                        onVerDetalhes = {
                            vendaDetalheModal = venda
                        },
                        onAtualizarStatus = { novoStatus, veiculoId ->
                            vendaViewModel.avancarStatus(
                                vendaId = venda.id,
                                novoStatus = novoStatus,
                                perfil = "motorista",
                                motoristaId = usuario.uid,
                                veiculoId = veiculoId
                            )
                        }
                    )
                }
            }
        }
    }

    // ========================================================
    // DIÁLOGO DE EDIÇÃO DE VEÍCULO
    // ========================================================
    if (veiculoEmEdicao != null) {
        val v = veiculoEmEdicao!!
        EditarVeiculoDialog(
            veiculo = v,
            onDismiss = { veiculoEmEdicao = null },
            onSalvar = { veiculoAtualizado ->
                veiculoViewModel.atualizarVeiculo(veiculoAtualizado) {
                    veiculoEmEdicao = null
                }
            }
        )
    }

    // ========================================================
    // DIÁLOGO DE CONFIRMAÇÃO DE EXCLUSÃO DE VEÍCULO
    // ========================================================
    if (veiculoParaExcluir != null) {
        val v = veiculoParaExcluir!!
        val estaEmUso = vendas.any { it.veiculoId == v.id && it.statusEntrega == StatusEntrega.A_CAMINHO }

        ConfirmarExclusaoVeiculoDialog(
            veiculo = v,
            estaEmUso = estaEmUso,
            onDismiss = { veiculoParaExcluir = null },
            onConfirmar = {
                veiculoViewModel.excluirVeiculo(v) {
                    veiculoParaExcluir = null
                }
            }
        )
    }

    // ========================================================
    // MODAL DE DETALHES COMPLETOS DA VENDA E ENTREGA
    // ========================================================
    if (vendaDetalheModal != null) {
        val v = vendaDetalheModal!!
        val cliente = clientesMap[v.compradorId]
        val parceiroVendedor = negociantes.find { it.uid == v.vendedorId }
        val veiculoAlocado = veiculos.find { it.id == v.veiculoId }

        DetalhesVendaModal(
            venda = v,
            cliente = cliente,
            parceiroVendedor = parceiroVendedor,
            veiculo = veiculoAlocado,
            onDismiss = { vendaDetalheModal = null }
        )
    }
}

// ============================================================
// COMPONENTE: CARD DA ENTREGA PARA O MOTORISTA
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotoristaEntregaCard(
    venda: Venda,
    cliente: Usuario?,
    parceiroVendedor: Usuario?,
    usuario: Usuario,
    veiculos: List<Veiculo>,
    onVerDetalhes: () -> Unit,
    onAtualizarStatus: (String, String?) -> Unit
) {
    val status = venda.statusEntrega
    var veiculoSelecionado by remember(venda.id) { mutableStateOf<Veiculo?>(null) }
    var menuVeiculoAberto by remember { mutableStateOf(false) }

    // Resolução do Nome do Cliente
    val nomeCliente = when {
        venda.compradorNome.isNotBlank() -> venda.compradorNome
        cliente != null && cliente.nome.isNotBlank() -> cliente.nome
        else -> "Cliente #${venda.compradorId.take(6)}"
    }

    // Resolução do Título do Produto
    val tituloProduto = when {
        venda.produtoTitulo.isNotBlank() -> venda.produtoTitulo
        else -> "Produto #${venda.produtoId.take(6)}"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pedido #${venda.id.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatusEntregaBadge(status = status)
            }

            Spacer(Modifier.height(8.dp))

            // Destaque claro do nome do cliente
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Cliente: $nomeCliente",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (cliente != null && cliente.cidade.isNotBlank()) {
                        Text(
                            text = "Destino: ${cliente.cidade} - ${cliente.estado}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text("Produto: $tituloProduto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("Quantidade: ${venda.quantidade}", style = MaterialTheme.typography.bodySmall)
            Text("Valor Total: R$ %.2f".format(venda.valorTotal), style = MaterialTheme.typography.bodyMedium)

            if (parceiroVendedor != null) {
                Text(
                    "Origem (Loja): ${parceiroVendedor.nome}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            // Botão para abrir o modal de detalhes
            OutlinedButton(
                onClick = onVerDetalhes,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ver Detalhes do Pedido e Endereço")
            }

            Spacer(Modifier.height(8.dp))

            when (status) {
                StatusEntrega.PENDENTE -> {
                    Text(
                        "Pedido pendente. Aguardando liberação para entrega.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                StatusEntrega.PRONTO_PARA_ENTREGA -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (veiculos.isEmpty()) {
                            Text(
                                "Cadastre um veículo para poder iniciar a entrega.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = menuVeiculoAberto,
                                onExpandedChange = { menuVeiculoAberto = it }
                            ) {
                                OutlinedTextField(
                                    value = veiculoSelecionado?.let { "${it.modelo} - ${it.placa}" } ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Veículo para a entrega") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuVeiculoAberto) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = menuVeiculoAberto,
                                    onDismissRequest = { menuVeiculoAberto = false }
                                ) {
                                    veiculos.forEach { veiculo ->
                                        DropdownMenuItem(
                                            text = { Text("${veiculo.modelo} - ${veiculo.placa}") },
                                            onClick = {
                                                veiculoSelecionado = veiculo
                                                menuVeiculoAberto = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.A_CAMINHO.name, veiculoSelecionado?.id)
                            },
                            enabled = veiculoSelecionado != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Coletar e Iniciar Entrega (A caminho)")
                        }
                    }
                }

                StatusEntrega.A_CAMINHO -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val veiculoDaEntrega = veiculos.find { it.id == venda.veiculoId }
                        if (veiculoDaEntrega != null) {
                            Text(
                                "Veículo: ${veiculoDaEntrega.modelo} - ${veiculoDaEntrega.placa}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.ENTREGUE.name, venda.veiculoId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirmar Entrega ao Cliente")
                        }

                        OutlinedButton(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.PRONTO_PARA_ENTREGA.name, venda.veiculoId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar Coleta / Devolver para Loja")
                        }
                    }
                }

                StatusEntrega.ENTREGUE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Entrega Concluída ✓",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedButton(
                            onClick = {
                                onAtualizarStatus(StatusEntrega.A_CAMINHO.name, venda.veiculoId)
                            }
                        ) {
                            Text("Desfazer Entrega")
                        }
                    }
                }

                StatusEntrega.CANCELADA -> {
                    Text(
                        "Pedido cancelado. Nenhuma ação de entrega disponível.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ============================================================
// DIÁLOGO: EDITAR VEÍCULO
// ============================================================
@Composable
private fun EditarVeiculoDialog(
    veiculo: Veiculo,
    onDismiss: () -> Unit,
    onSalvar: (Veiculo) -> Unit
) {
    var tipo by remember { mutableStateOf(veiculo.tipo) }
    var marca by remember { mutableStateOf(veiculo.marca) }
    var modelo by remember { mutableStateOf(veiculo.modelo) }
    var ano by remember { mutableStateOf(if (veiculo.ano > 0) veiculo.ano.toString() else "") }
    var placa by remember { mutableStateOf(veiculo.placa) }
    var cor by remember { mutableStateOf(veiculo.cor) }
    var erroMensagem by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Veículo") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tipo,
                    onValueChange = { tipo = it },
                    label = { Text("Tipo (Carro, Moto, etc)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = marca,
                    onValueChange = { marca = it },
                    label = { Text("Marca") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = modelo,
                    onValueChange = { modelo = it },
                    label = { Text("Modelo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ano,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 4) ano = input
                    },
                    label = { Text("Ano") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = placa,
                    onValueChange = { input ->
                        if (input.length <= 7) placa = input.uppercase()
                    },
                    label = { Text("Placa") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cor,
                    onValueChange = { cor = it },
                    label = { Text("Cor") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (erroMensagem != null) {
                    Text(
                        text = erroMensagem!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val anoInt = ano.toIntOrNull() ?: 0
                    try {
                        VeiculoRegras.validar(
                            motoristaId = veiculo.motoristaId,
                            placa = placa,
                            ano = anoInt
                        )
                        if (tipo.isBlank() || marca.isBlank() || modelo.isBlank()) {
                            erroMensagem = "Preencha todos os campos obrigatórios."
                            return@Button
                        }
                        onSalvar(
                            veiculo.copy(
                                tipo = tipo.trim(),
                                marca = marca.trim(),
                                modelo = modelo.trim(),
                                ano = anoInt,
                                placa = placa.trim().uppercase(),
                                cor = cor.trim()
                            )
                        )
                    } catch (e: Exception) {
                        erroMensagem = e.message ?: "Dados inválidos."
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// ============================================================
// DIÁLOGO: CONFIRMAR EXCLUSÃO DE VEÍCULO
// ============================================================
@Composable
private fun ConfirmarExclusaoVeiculoDialog(
    veiculo: Veiculo,
    estaEmUso: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Excluir Veículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Deseja realmente remover o veículo ${veiculo.modelo} (${veiculo.placa})?")
                if (estaEmUso) {
                    Text(
                        "Atenção: Este veículo está atualmente atribuído a uma entrega em transporte!",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Excluir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// ============================================================
// MODAL: DETALHES COMPLETOS DA VENDA E ENTREGA
// ============================================================
@Composable
private fun DetalhesVendaModal(
    venda: Venda,
    cliente: Usuario?,
    parceiroVendedor: Usuario?,
    veiculo: Veiculo?,
    onDismiss: () -> Unit
) {
    val nomeCliente = when {
        venda.compradorNome.isNotBlank() -> venda.compradorNome
        cliente != null && cliente.nome.isNotBlank() -> cliente.nome
        else -> "Cliente #${venda.compradorId.take(6)}"
    }

    val tituloProduto = when {
        venda.produtoTitulo.isNotBlank() -> venda.produtoTitulo
        else -> "Produto #${venda.produtoId.take(6)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pedido #${venda.id.take(8)}", style = MaterialTheme.typography.titleLarge)
                StatusEntregaBadge(status = venda.statusEntrega)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SEÇÃO CLIENTE E ENDEREÇO DE ENTREGA
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Dados do Cliente & Entrega", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Nome: $nomeCliente", fontWeight = FontWeight.SemiBold)
                        if (cliente != null && cliente.email.isNotBlank()) {
                            Text("Email: ${cliente.email}", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Endereço de Entrega:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (cliente != null && cliente.rua.isNotBlank()) {
                            Text("${cliente.rua}, Nº ${cliente.numero}")
                            Text("Bairro / Cidade: ${cliente.cidade} - ${cliente.estado}")
                            Text("CEP: ${cliente.cep}")
                        } else {
                            Text("Endereço detalhado não disponível no cadastro do cliente.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // SEÇÃO PRODUTO E VALORES
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Detalhes do Produto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Item: $tituloProduto", fontWeight = FontWeight.SemiBold)
                        Text("Quantidade: ${venda.quantidade}")
                        if (venda.valorUnitario > 0.0) {
                            Text("Valor Unitário: R$ %.2f".format(venda.valorUnitario))
                        }
                        Text(
                            "Valor Total do Pedido: R$ %.2f".format(venda.valorTotal),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // SEÇÃO ORIGEM (NEGOCIANTE / LOJA)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Ponto de Coleta (Parceiro)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        if (parceiroVendedor != null) {
                            Text("Loja: ${parceiroVendedor.nome}", fontWeight = FontWeight.SemiBold)
                            if (parceiroVendedor.rua.isNotBlank()) {
                                Text("Endereço: ${parceiroVendedor.rua}, Nº ${parceiroVendedor.numero} - ${parceiroVendedor.cidade}")
                            }
                            if (parceiroVendedor.email.isNotBlank()) {
                                Text("Contato: ${parceiroVendedor.email}", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text("ID do Vendedor: ${venda.vendedorId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // VEÍCULO ATRIBUÍDO (SE HOUVER)
                if (veiculo != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Veículo da Entrega", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("${veiculo.modelo} - Placa: ${veiculo.placa}")
                            Text("Tipo: ${veiculo.tipo} • Cor: ${veiculo.cor}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // DATA DO PEDIDO
                val dataFormatada = try {
                    venda.dataCriacao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                } catch (e: Exception) {
                    ""
                }
                if (dataFormatada.isNotBlank()) {
                    Text(
                        "Data do Pedido: $dataFormatada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}
