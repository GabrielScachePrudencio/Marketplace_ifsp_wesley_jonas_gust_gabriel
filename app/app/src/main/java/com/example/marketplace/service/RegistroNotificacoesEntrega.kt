package com.example.marketplace.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Mantém a escuta em tempo real no Firestore (no nível do ApplicationContext)
 * para avisar tanto o COMPRADOR quanto o NEGOCIADOR quando o motorista entrar
 * no raio de 50 m do endereço de entrega, mesmo que o app esteja minimizado.
 */
object MonitorNotificacoesEntrega {
    private const val TAG = "MonitorNotificacoes"
    private const val JANELA_NOTIFICACAO_RECENTE_MS = 60 * 60 * 1000L // 1 hora

    private var uidMonitorado: String? = null
    private var listenerComprador: ListenerRegistration? = null
    private var listenerVendedor: ListenerRegistration? = null
    private val estadoAnteriorProximidade = ConcurrentHashMap<String, Boolean>()

    @Synchronized
    fun iniciar(context: Context, uid: String) {
        if (uid.isBlank()) return
        val appContext = context.applicationContext
        EntregaMessagingService.criarCanal(appContext)

        if (uidMonitorado == uid && listenerComprador != null && listenerVendedor != null) {
            return
        }

        parar()
        uidMonitorado = uid

        val firestore = FirebaseFirestore.getInstance()

        // 1) Escuta pedidos em que o usuário logado é o COMPRADOR
        listenerComprador = firestore.collection("vendas")
            .whereEqualTo("compradorId", uid)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) {
                    Log.e(TAG, "Erro ao escutar vendas do comprador: ${erro.message}")
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    processarDocumentoVenda(
                        context = appContext,
                        uid = uid,
                        doc = doc,
                        ehComprador = true
                    )
                }
            }

        // 2) Escuta pedidos em que o usuário logado é o NEGOCIADOR (vendedor)
        listenerVendedor = firestore.collection("vendas")
            .whereEqualTo("vendedorId", uid)
            .addSnapshotListener { snapshot, erro ->
                if (erro != null) {
                    Log.e(TAG, "Erro ao escutar vendas do negociador: ${erro.message}")
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    processarDocumentoVenda(
                        context = appContext,
                        uid = uid,
                        doc = doc,
                        ehComprador = false
                    )
                }
            }
    }

    @Synchronized
    fun parar() {
        listenerComprador?.remove()
        listenerComprador = null
        listenerVendedor?.remove()
        listenerVendedor = null
        uidMonitorado = null
        estadoAnteriorProximidade.clear()
    }

    private fun processarDocumentoVenda(
        context: Context,
        uid: String,
        doc: DocumentSnapshot,
        ehComprador: Boolean
    ) {
        val vendaId = doc.id
        if (vendaId.isBlank()) return

        val chaveEstado = "${uid}_${if (ehComprador) "C" else "V"}_$vendaId"
        val proximidadeNotificada = doc.getBoolean("proximidadeNotificada") == true
        val status = doc.getString("status").orEmpty()
        val entregaAtiva = status == "A_CAMINHO" || status == "SAIU_PARA_ENTREGA"

        if (!proximidadeNotificada) {
            estadoAnteriorProximidade[chaveEstado] = false
            EntregaMessagingService.limparNotificacaoVenda(context, uid, vendaId)
            return
        }

        val anterior = estadoAnteriorProximidade.put(chaveEstado, true)
        if (!entregaAtiva) {
            // Pedido já foi finalizado ou cancelado; marca como visto para não notificar retroativamente
            EntregaMessagingService.marcarComoNotificada(context, uid, vendaId)
            return
        }

        val notificadaEm = doc.getLong("proximidadeNotificadaEm") ?: 0L
        val eventoRecente = notificadaEm == 0L ||
                (System.currentTimeMillis() - notificadaEm) <= JANELA_NOTIFICACAO_RECENTE_MS
        val mudouAgora = anterior == false

        if ((mudouAgora || eventoRecente) && !EntregaMessagingService.jaFoiNotificada(context, uid, vendaId)) {
            val produtoTitulo = doc.getString("produtoTitulo").orEmpty().ifBlank {
                "Pedido #${vendaId.take(8)}"
            }
            val titulo = if (ehComprador) {
                "Seu entregador está chegando"
            } else {
                "Entregador próximo ao cliente"
            }
            val mensagem = if (ehComprador) {
                "O entregador do pedido \"$produtoTitulo\" está a menos de 50 metros do seu endereço. Toque para acompanhar no mapa."
            } else {
                "O entregador do pedido \"$produtoTitulo\" está a menos de 50 metros do endereço do comprador. Toque para acompanhar no mapa."
            }

            EntregaMessagingService.mostrarNotificacaoProximidade(
                context = context,
                vendaId = vendaId,
                titulo = titulo,
                mensagem = mensagem,
                uid = uid
            )
        }
    }
}

@Composable
fun RegistroNotificacoesEntrega(uid: String) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (!concedida) {
            Toast.makeText(
                context,
                "Notificações desativadas. Ative-as nas configurações para receber avisos de entrega.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(uid) {
        EntregaMessagingService.criarCanal(context)
        MonitorNotificacoesEntrega.iniciar(context, uid)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        try {
            val token = FirebaseMessaging.getInstance().token.await()
            EntregaMessagingService.registrarToken(uid, token)
        } catch (e: Exception) {
            Log.e("EntregaNotifications", "Não foi possível registrar o dispositivo para notificações.", e)
        }
    }
}
