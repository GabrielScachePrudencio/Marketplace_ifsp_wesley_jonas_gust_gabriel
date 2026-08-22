# Backend do Marketplace — o que foi feito

Documento pra galera saber o que mudou nessa leva de commits antes de mexer em cima. Cobre: fix de um crash real no login, a camada de persistência + regras de negócio dos modelos do marketplace (Produto, Veiculo, Venda, AvaliacaoProduto), telas novas de compra/venda/avaliação, e persistência de sessão de login.

Commits, em ordem (mesma ordem que devem ser lidos/revisados):

1. `fix(auth): fix login crash on Firestore LocalDateTime deserialization`
2. `feat(marketplace): add persistence and business rules for Produto, Veiculo, Venda, AvaliacaoProduto`
3. `test(marketplace): add instrumented test for real Firestore and Room persistence`
4. `feat(ui): add product browsing, purchase, review and sales screens`
5. `fix(auth): persist login session across app restarts`

---

## 1. Bug corrigido: crash no login

**Sintoma:** depois de criar conta e logar, a tela travava com o erro:
`Could not deserialize object. Class j$.time.LocalDateTime does not define a no-argument constructor... (found in field 'dataCriacao')`

**Causa:** o Firestore tenta reconstruir o objeto `Usuario` via reflection (`doc.toObject(Usuario::class.java)`), mas `LocalDateTime` não tem construtor vazio — então ele quebra na hora de ler o campo `dataCriacao`.

**Fix:** em `UsuarioRepository`, `dataCriacao` agora é salvo no Firestore como `Long` (millis, UTC) — igual ao que o `Converters.kt` do Room já fazia — e a leitura do documento é feita campo a campo (`doc.getString`, `doc.getLong`...) em vez de `toObject()`. Contas antigas com o formato quebrado caem no fallback `LocalDateTime.now()` em vez de travar.

Se algum dia alguém decidir usar `toObject()` de novo em qualquer model com `LocalDateTime`, vai voltar a quebrar — o padrão certo é sempre mapear manualmente (ver `FirestoreDateConverter` no ponto 2).

---

## 2. Persistência e regras de negócio dos modelos do marketplace

### Arquitetura (mesmo padrão do `UsuarioRepository`, sem framework de DI)

```
View (Compose) → ViewModel → Repository → Firestore (fonte de verdade) + Room (cache local)
```

Regras de negócio ficam em `domain/*Regras.kt` — objetos Kotlin puros, sem import de Android/Firebase, chamados pelo Repository antes de persistir.

### Coleções no Firestore

| Modelo | Coleção Firestore | Tabela Room |
|---|---|---|
| Produto | `produtos` | `produtos` |
| Veiculo | `veiculos` | `veiculos` |
| Venda | `vendas` | `vendas` |
| AvaliacaoProduto | `avaliacoesProdutos` | `avalicaoProdutos` (typo já existia no schema, mantido pra não quebrar migração) |

Todas nascem sozinhas no primeiro `.set()` — nenhuma foi criada vazia manualmente. Mapeamento sempre manual (sem `toObject()`), `dataCriacao` sempre `Long` millis via `data/local/FirestoreDateConverter.kt` (utilitário novo, compartilhado pelos 4 repositories novos — `UsuarioRepository` não foi tocado nesse ponto, mantém sua própria conversão inline).

### Regras de negócio (`domain/`)

- **ProdutoRegras**: título/descrição/categoria obrigatórios, `preco > 0`, `quantidade >= 0`, `vendedorId` obrigatório.
- **VeiculoRegras**: `motoristaId`/`placa` obrigatórios, `ano` entre 1950 e ano atual+1.
- **VendaRegras**:
  - `vendedorId` da venda tem que bater com o `vendedorId` do produto.
  - Estoque suficiente é exigido (`produto.quantidade >= quantidade pedida`).
  - `valorTotal` é **sempre calculado** pelo repository (`valorUnitario * quantidade`) — nunca aceito pronto de fora.
  - Status nasce `PENDENTE`. Transições permitidas: `PENDENTE → EM_TRANSPORTE → ENTREGUE` ou `PENDENTE → CANCELADA`. Qualquer outra transição é rejeitada.
- **AvaliacaoProdutoRegras**: nota entre 1 e 5. Uma avaliação por par (usuário, produto) — id determinístico `"${produtoId}_${usuarioId}"`; segunda tentativa é bloqueada com `Exception("Você já avaliou este produto")`, não vira update silencioso.

### Métodos expostos (prontos pro ViewModel/Front chamar)

- `ProdutoRepository`: `criarProduto`, `atualizarProduto`, `excluirProduto`, `buscarProdutoPorId`, `buscarProdutos()` (Flow)
- `VeiculoRepository`: `cadastrarVeiculo`, `atualizarVeiculo`, `excluirVeiculo`, `buscarVeiculoPorId`, `buscarVeiculos()` (Flow)
- `VendaRepository(vendaDao, produtoRepository)` — **atenção**: precisa de `ProdutoRepository` no construtor pra buscar preço/estoque do produto. Composição manual: `VendaRepository(db.vendaDao(), ProdutoRepository(db.produtoDao()))`.
  - `criarVenda(compradorId, vendedorId, motoristaId, produtoId, quantidade)`, `atualizarStatusVenda(id, novoStatus)`, `buscarVendaPorId`, `buscarVendas()` (Flow)
- `AvaliacaoProdutoRepository`: `avaliarProduto(produtoId, usuarioId, nota, comentario)`, `excluirAvaliacao`, `buscarAvaliacaoPorId`, `buscarAvaliacoesDoProduto(produtoId)` (Flow)

### O que ficou de fora (de propósito)

- **Veiculo/motorista não tem tela nem fluxo de uso ainda** — o cadastro de usuário só oferece perfil `comprador`/`negociante`, não existe conta `motorista`. O repository e as regras existem e estão testados, mas ninguém chama `criarVenda` passando um `motoristaId` real ainda — fica `""`. Quando alguém adicionar o perfil motorista, é só plugar.
- Sem tela de "editar/excluir produto" pelo negociante — só criar e listar. Os métodos (`atualizarProduto`, `excluirProduto`) já existem no repository, só falta UI.

---

## 3. Teste automatizado

`app/src/androidTest/.../MarketplaceRepositoriesInstrumentedTest.kt` — teste instrumentado real, sem mock, batendo Firestore e Room de verdade (mesmo projeto Firebase do app). Cobre: CRUD de Produto e Veiculo, criação de Venda com desconto de estoque e cálculo de total, transição de status válida e inválida, venda sem estoque suficiente, e bloqueio de avaliação duplicada.

Pra rodar (precisa de emulador/device conectado):
```
cd app
./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.marketplace.MarketplaceRepositoriesInstrumentedTest
```
Ele cria e apaga documentos reais no Firestore do projeto ao rodar — normal, é o comportamento esperado de um teste sem mock.

---

## 4. Telas novas

Adicionadas seguindo o estilo visual já existente (Material3 puro, sem lib de navegação — troca de tela por `enum` + `remember`, igual `LoginScreen`/`CreateUsuarioScreen`):

- **ProdutoListScreen** — vira a home pós-login. Lista todos os produtos; FAB "+" só aparece pra `perfil == "negociante"`.
- **CriarProdutoScreen** — form de criação de produto (só negociante acessa).
- **ProdutoDetalheScreen** — dados do produto, form de compra (comprador, exceto se for dono do produto) e form de avaliação (nota 1-5 + comentário), lista de avaliações existentes.
- **MinhasVendasScreen** — lista as vendas do usuário logado (como comprador ou vendedor); vendedor ganha botão pra avançar status.

ViewModels novos (mesmo padrão sealed `UiState` + `StateFlow` do Login/Cadastro): `ProdutoListViewModel` (lista + criar), `ProdutoDetalheViewModel` (detalhe + comprar + avaliar), `VendaListViewModel` (lista + avançar status). Cada um com sua Factory fazendo DI manual via `AppDatabase.getDatabase(context)`.

`MainActivity` ganhou um enum `TelaApp` novo pra navegar entre essas telas pós-login — o antigo `TelaBoasVindas` (tela estática de "login com sucesso") foi removido, não é mais usado.

**Testado manualmente** no emulador com duas contas reais (negociante + comprador): criar produto → comprar → estoque desconta → avaliar → segunda avaliação bloqueada → vendedor avança status até ENTREGUE. Tudo batendo Firestore/Room reais.

---

## 5. Sessão de login agora persiste

Antes, fechar e reabrir o app sempre voltava pra tela de login, mesmo com o Firebase Auth já tendo uma sessão válida — a `MainActivity`/`LoginViewModel` simplesmente ignorava isso e recomeçava do zero.

Agora `UsuarioRepository.carregarUsuarioAtual()` verifica se tem `FirebaseAuth.currentUser` e recarrega o perfil do Firestore; `LoginViewModel` faz essa checagem no `init` e já entra direto na tela de produtos se a sessão for válida.

---

## Pontos de atenção pra quem for mexer depois

- **`VendaRepository` exige `ProdutoRepository` no construtor** — não esquecer ao criar um novo ViewModel/Factory que precise dele.
- **Perfil `motorista` não existe** no cadastro — se for implementar entrega/veículo de verdade, precisa adicionar essa opção em `CreateUsuarioScreen` primeiro.
- **Nunca usar `doc.toObject()` do Firestore em model com `LocalDateTime`** — sempre mapear campo a campo e converter via `FirestoreDateConverter` (ou o padrão equivalente que já existe em `UsuarioRepository`).
- Tabela Room de avaliação tem o nome `avalicaoProdutos` (com typo) — é assim no schema já existente antes dessas mudanças, mantido de propósito pra não quebrar a versão do banco.
