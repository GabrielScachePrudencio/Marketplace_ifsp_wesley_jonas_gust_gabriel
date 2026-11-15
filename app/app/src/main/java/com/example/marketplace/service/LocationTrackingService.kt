package com.example.marketplace.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.marketplace.MainActivity
import com.example.marketplace.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val ACTION_REMOVE_VENDA = "ACTION_REMOVE_VENDA"
        const val EXTRA_VENDA_ID = "EXTRA_VENDA_ID"
        const val EXTRA_VENDAS_IDS = "EXTRA_VENDAS_IDS"
        const val EXTRA_LIMPAR_SERVIDOR = "EXTRA_LIMPAR_SERVIDOR"

        private const val CHANNEL_ID = "rastreio_entrega_channel"
        private const val NOTIFICATION_ID = 1001
        private const val INTERVALO_ATUALIZACAO_MS = 3000L
        val RAIO_CERCA_METROS: Float
            get() = ProximidadeEntregaPolicy.raioMetros
        private const val IDADE_MAXIMA_LOCALIZACAO_MS = 15000L
        private const val TAG = "LocationTracking"

        @Volatile
        var servicoAtivo: Boolean = false
            private set

        private val destinosCache = ConcurrentHashMap<String, Pair<Double, Double>>()
        private val proximidadeNotificadaMemory = ConcurrentHashMap.newKeySet<String>()
        private val leiturasConsecutivasProximidade = ConcurrentHashMap<String, Int>()
        private val ultimaLeituraProximidadeNanos = ConcurrentHashMap<String, Long>()
        private val ultimoLogRejeicaoProximidade = ConcurrentHashMap<String, Long>()
        private val carregandoDestino = ConcurrentHashMap.newKeySet<String>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun limparCacheVenda(vendaId: String) {
            if (vendaId.isBlank()) return
            destinosCache.remove(vendaId)
            proximidadeNotificadaMemory.remove(vendaId)
            limparLeiturasProximidade(vendaId)
            carregandoDestino.remove(vendaId)
        }

        fun limparTodosCaches() {
            destinosCache.clear()
            proximidadeNotificadaMemory.clear()
            leiturasConsecutivasProximidade.clear()
            ultimaLeituraProximidadeNanos.clear()
            carregandoDestino.clear()
        }

        fun removerLocalizacaoServidor(vendaId: String) {
            if (vendaId.isBlank()) return
            FirebaseFirestore.getInstance()
                .collection("localizacoes_tempo_real")
                .document(vendaId)
                .delete()
        }

        fun verificarERegistrarProximidade(
            vendaId: String,
            localizacaoMotorista: Location
        ) {
            if (vendaId.isBlank() || proximidadeNotificadaMemory.contains(vendaId)) return
            if (!localizacaoRecente(localizacaoMotorista)) {
                limparLeiturasProximidade(vendaId)
                logRejeicaoThrottled(
                    vendaId,
                    "Venda ${vendaId.take(8)}: leitura de GPS descartada por estar desatualizada."
                )
                return
            }
            val destino = destinosCache[vendaId]
            if (destino == null) {
                logRejeicaoThrottled(
                    vendaId,
                    "Venda ${vendaId.take(8)}: destino ainda não carregado, tentando resolver..."
                )
                carregarDestinoAssincrono(vendaId) { coord ->
                    if (coord != null) {
                        checarDistanciaERegistrar(vendaId, localizacaoMotorista, coord)
                    } else {
                        logRejeicaoThrottled(
                            vendaId,
                            "Venda ${vendaId.take(8)}: NÃO foi possível obter as coordenadas do destino."
                        )
                    }
                }
                return
            }
            checarDistanciaERegistrar(vendaId, localizacaoMotorista, destino)
        }

        private fun carregarDestinoAssincrono(
            vendaId: String,
            onLoaded: (Pair<Double, Double>?) -> Unit = {}
        ) {
            destinosCache[vendaId]?.let {
                onLoaded(it)
                return
            }
            if (!carregandoDestino.add(vendaId)) return

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("rastreios_entrega")
                .whereEqualTo("vendaId", vendaId)
                .limit(1)
                .get()
                .addOnSuccessListener { resultado ->
                    val documento = resultado.documents.firstOrNull()
                    val lat = documento?.getDouble("destinoLat") ?: 0.0
                    val lng = documento?.getDouble("destinoLng") ?: 0.0
                    val enderecoTexto = documento?.getString("enderecoDestinoTexto").orEmpty()

                    if (coordenadaValida(lat, lng)) {
                        val par = lat to lng
                        destinosCache[vendaId] = par
                        carregandoDestino.remove(vendaId)
                        onLoaded(par)
                    } else {
                        buscarDestinoPeloComprador(firestore, vendaId, documento?.id, enderecoTexto) { par ->
                            carregandoDestino.remove(vendaId)
                            onLoaded(par)
                        }
                    }
                }
                .addOnFailureListener { erro ->
                    Log.e(TAG, "Não foi possível carregar o destino da venda $vendaId.", erro)
                    carregandoDestino.remove(vendaId)
                    onLoaded(null)
                }
        }

        private fun buscarDestinoPeloComprador(
            firestore: FirebaseFirestore,
            vendaId: String,
            rastreioDocId: String?,
            enderecoTexto: String,
            onResult: (Pair<Double, Double>?) -> Unit
        ) {
            firestore.collection("vendas").document(vendaId).get()
                .addOnSuccessListener { vendaDoc ->
                    val compradorId = vendaDoc.getString("compradorId").orEmpty()
                    if (compradorId.isBlank()) {
                        geocodificarFallback(firestore, vendaId, rastreioDocId, enderecoTexto, null, onResult)
                        return@addOnSuccessListener
                    }
                    firestore.collection("usuarios").document(compradorId).get()
                        .addOnSuccessListener { usuarioDoc ->
                            val uLat = usuarioDoc.getDouble("latitude") ?: 0.0
                            val uLng = usuarioDoc.getDouble("longitude") ?: 0.0
                            if (coordenadaValida(uLat, uLng)) {
                                val par = uLat to uLng
                                destinosCache[vendaId] = par
                                salvarDestinoNoRastreio(firestore, rastreioDocId, uLat, uLng)
                                onResult(par)
                            } else {
                                geocodificarFallback(firestore, vendaId, rastreioDocId, enderecoTexto, usuarioDoc, onResult)
                            }
                        }
                        .addOnFailureListener {
                            geocodificarFallback(firestore, vendaId, rastreioDocId, enderecoTexto, null, onResult)
                        }
                }
                .addOnFailureListener {
                    onResult(null)
                }
        }

        private fun geocodificarFallback(
            firestore: FirebaseFirestore,
            vendaId: String,
            rastreioDocId: String?,
            enderecoTexto: String,
            usuarioDoc: com.google.firebase.firestore.DocumentSnapshot?,
            onResult: (Pair<Double, Double>?) -> Unit
        ) {
            scope.launch {
                try {
                    val rua = usuarioDoc?.getString("rua").orEmpty()
                    val numero = usuarioDoc?.getString("numero").orEmpty()
                    val cidade = usuarioDoc?.getString("cidade").orEmpty()
                    val estado = usuarioDoc?.getString("estado").orEmpty()
                    val cep = usuarioDoc?.getString("cep").orEmpty()

                    val coord = if (rua.isNotBlank() || cidade.isNotBlank() || cep.isNotBlank()) {
                        GeocodingService.buscarCoordenadas(
                            rua = rua,
                            numero = numero,
                            cidade = cidade,
                            estado = estado,
                            cep = cep
                        )
                    } else if (enderecoTexto.isNotBlank()) {
                        GeocodingService.buscarCoordenadasPorTexto("$enderecoTexto, Brasil")
                    } else null

                    if (coord != null && coordenadaValida(coord.latitude, coord.longitude)) {
                        val par = coord.latitude to coord.longitude
                        destinosCache[vendaId] = par
                        salvarDestinoNoRastreio(firestore, rastreioDocId, coord.latitude, coord.longitude)
                        onResult(par)
                    } else {
                        onResult(null)
                    }
                } catch (e: Exception) {
                    onResult(null)
                }
            }
        }

        private fun salvarDestinoNoRastreio(
            firestore: FirebaseFirestore,
            rastreioDocId: String?,
            lat: Double,
            lng: Double
        ) {
            if (!rastreioDocId.isNullOrBlank()) {
                firestore.collection("rastreios_entrega")
                    .document(rastreioDocId)
                    .update(mapOf("destinoLat" to lat, "destinoLng" to lng))
            }
        }

        private fun coordenadaValida(lat: Double, lng: Double): Boolean {
            return lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)
        }

        private fun localizacaoRecente(localizacao: Location): Boolean {
            val instanteFix = localizacao.elapsedRealtimeNanos
            if (instanteFix <= 0L) return false
            val idadeMs = (SystemClock.elapsedRealtimeNanos() - instanteFix) / 1_000_000L
            return idadeMs in 0..IDADE_MAXIMA_LOCALIZACAO_MS
        }

        private fun limparLeiturasProximidade(vendaId: String) {
            synchronized(leiturasConsecutivasProximidade) {
                leiturasConsecutivasProximidade.remove(vendaId)
                ultimaLeituraProximidadeNanos.remove(vendaId)
            }
        }

        private fun logRejeicaoThrottled(vendaId: String, mensagem: String) {
            val agora = System.currentTimeMillis()
            val anterior = ultimoLogRejeicaoProximidade[vendaId] ?: 0L
            if (agora - anterior >= 30_000L) {
                ultimoLogRejeicaoProximidade[vendaId] = agora
                LogNotificacoesEntrega.registrar("GPS Motorista", mensagem)
            }
        }

        private fun checarDistanciaERegistrar(
            vendaId: String,
            localizacaoMotorista: Location,
            destino: Pair<Double, Double>
        ) {
            if (proximidadeNotificadaMemory.contains(vendaId)) return
            if (
                !localizacaoRecente(localizacaoMotorista) ||
                !localizacaoMotorista.hasAccuracy() ||
                !coordenadaValida(localizacaoMotorista.latitude, localizacaoMotorista.longitude)
            ) {
                limparLeiturasProximidade(vendaId)
                logRejeicaoThrottled(
                    vendaId,
                    "Venda ${vendaId.take(8)}: GPS sem precisão disponível " +
                        "(precisão=${if (localizacaoMotorista.hasAccuracy()) "${localizacaoMotorista.accuracy} m" else "indisponível"})."
                )
                return
            }

            val (destinoLat, destinoLng) = destino
            val distancia = FloatArray(1)
            Location.distanceBetween(
                localizacaoMotorista.latitude,
                localizacaoMotorista.longitude,
                destinoLat,
                destinoLng,
                distancia
            )
            LogNotificacoesEntrega.registrar(
                "GPS Motorista",
                "Venda ${vendaId.take(8)}: distância até o destino = " +
                    "${String.format(java.util.Locale.getDefault(), "%.1f", distancia[0])} m " +
                    "(precisão ±${String.format(java.util.Locale.getDefault(), "%.1f", localizacaoMotorista.accuracy)} m)."
            )
            if (!ProximidadeEntregaPolicy.amostraValida(distancia[0], localizacaoMotorista.accuracy)) {
                limparLeiturasProximidade(vendaId)
                logRejeicaoThrottled(
                    vendaId,
                    "Venda ${vendaId.take(8)}: amostra rejeitada — distância=" +
                        "${String.format(java.util.Locale.getDefault(), "%.1f", distancia[0])} m " +
                        "(raio=${ProximidadeEntregaPolicy.raioMetros.toInt()} m), " +
                        "precisão=±${String.format(java.util.Locale.getDefault(), "%.1f", localizacaoMotorista.accuracy)} m " +
                        "(máx=${ProximidadeEntregaPolicy.precisaoMaximaMetros().toInt()} m)."
                )
                return
            }

            val leiturasValidas = synchronized(leiturasConsecutivasProximidade) {
                val instanteFix = localizacaoMotorista.elapsedRealtimeNanos
                val fixAnterior = ultimaLeituraProximidadeNanos[vendaId]
                if (fixAnterior != null && instanteFix <= fixAnterior) {
                    return
                }
                ultimaLeituraProximidadeNanos[vendaId] = instanteFix
                val leituras = (leiturasConsecutivasProximidade[vendaId] ?: 0) + 1
                leiturasConsecutivasProximidade[vendaId] = leituras
                leituras
            }
            if (!ProximidadeEntregaPolicy.podeNotificar(
                    distancia[0],
                    localizacaoMotorista.accuracy,
                    leiturasValidas
                )
            ) {
                LogNotificacoesEntrega.registrar(
                    "GPS Motorista",
                    "Venda ${vendaId.take(8)}: dentro do raio, aguardando leituras " +
                        "($leiturasValidas de ${ProximidadeEntregaPolicy.LEITURAS_PARA_CONFIRMAR})."
                )
                return
            }

            LogNotificacoesEntrega.registrar(
                "GPS Motorista",
                "Venda ${vendaId.take(8)}: PROXIMIDADE CONFIRMADA no aparelho, " +
                    "gravando flag no Firestore..."
            )

            val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
            if (motoristaId.isNullOrBlank()) {
                Log.w(TAG, "Usuário motorista não autenticado; evento de proximidade não registrado.")
                return
            }

            val firestore = FirebaseFirestore.getInstance()
            val vendaRef = firestore.collection("vendas").document(vendaId)
            firestore.runTransaction { transacao ->
                val venda = transacao.get(vendaRef)
                val status = venda.getString("status")
                val motoristaAtribuido = venda.getString("motoristaId")
                val ativa = status == "A_CAMINHO" || status == "SAIU_PARA_ENTREGA"

                if (!venda.exists() || motoristaAtribuido != motoristaId || !ativa) {
                    false
                } else if (venda.getBoolean("proximidadeNotificada") == true) {
                    true
                } else {
                    transacao.update(
                        vendaRef,
                        mapOf(
                            "proximidadeNotificada" to true,
                            "proximidadeNotificadaEm" to System.currentTimeMillis()
                        )
                    )
                    true
                }
            }.addOnSuccessListener { registrada ->
                if (registrada) {
                    proximidadeNotificadaMemory.add(vendaId)
                    LogNotificacoesEntrega.registrar(
                        "GPS Motorista",
                        "Venda ${vendaId.take(8)}: flag proximidadeNotificada gravada no Firestore. " +
                            "Comprador/vendedor serão avisados pelo monitor local."
                    )
                } else {
                    LogNotificacoesEntrega.registrar(
                        "GPS Motorista",
                        "Venda ${vendaId.take(8)}: transação não gravou a flag " +
                            "(status inativo, motorista divergente ou já notificada)."
                    )
                }
            }.addOnFailureListener { erro ->
                Log.e(TAG, "Não foi possível registrar a entrada na cerca da venda $vendaId.", erro)
                LogNotificacoesEntrega.registrar(
                    "GPS Motorista",
                    "Venda ${vendaId.take(8)}: ERRO ao gravar proximidade no Firestore: ${erro.message}"
                )
            }
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val vendasIdsAtivas = ConcurrentHashMap.newKeySet<String>()

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate() {
        super.onCreate()
        ProximidadeEntregaPolicy.carregar(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        criarCanalNotificacao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                iniciarModoForeground()

                val temListaCompleta = intent.hasExtra(EXTRA_VENDAS_IDS)
                val listaExtra = intent.getStringArrayListExtra(EXTRA_VENDAS_IDS).orEmpty()
                val unicoExtra = intent.getStringExtra(EXTRA_VENDA_ID)
                val novosIds = (listaExtra + listOfNotNull(unicoExtra))
                    .filter { it.isNotBlank() }
                    .toSet()

                if (temListaCompleta) {
                    val removidos = vendasIdsAtivas - novosIds
                    removidos.forEach { id ->
                        vendasIdsAtivas.remove(id)
                        limparCacheVenda(id)
                        removerLocalizacaoServidor(id)
                    }
                }

                if (novosIds.isNotEmpty()) {
                    vendasIdsAtivas.addAll(novosIds)
                    novosIds.forEach { id -> carregarDestinoAssincrono(id) }
                    if (locationCallback == null) {
                        iniciarAtualizacoesDeLocalizacao()
                    }
                } else if (vendasIdsAtivas.isEmpty()) {
                    pararAtualizacoes(limparNoServidor = false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_REMOVE_VENDA -> {
                val vendaId = intent.getStringExtra(EXTRA_VENDA_ID).orEmpty()
                val limparServidor = intent.getBooleanExtra(EXTRA_LIMPAR_SERVIDOR, true)
                if (vendaId.isNotBlank()) {
                    vendasIdsAtivas.remove(vendaId)
                    limparCacheVenda(vendaId)
                    if (limparServidor) {
                        removerLocalizacaoServidor(vendaId)
                    }
                }
                if (vendasIdsAtivas.isEmpty()) {
                    pararAtualizacoes(limparNoServidor = false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_STOP_TRACKING -> {
                val limparServidor = intent.getBooleanExtra(EXTRA_LIMPAR_SERVIDOR, false)
                pararAtualizacoes(limparNoServidor = limparServidor)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun iniciarModoForeground() {
        val tipoServico = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), tipoServico)
    }

    private fun iniciarAtualizacoesDeLocalizacao() {
        val temFine = ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val temCoarse = ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!temFine && !temCoarse) {
            servicoAtivo = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val prioridade = if (temFine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationRequest = LocationRequest.Builder(
            prioridade,
            INTERVALO_ATUALIZACAO_MS
        )
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateAgeMillis(3000L)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    vendasIdsAtivas.forEach { vendaId ->
                        enviarLocalizacaoParaFirestore(vendaId, location)
                        verificarERegistrarProximidade(vendaId, location)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback as LocationCallback,
            Looper.getMainLooper()
        )
        servicoAtivo = true
    }

    private fun enviarLocalizacaoParaFirestore(vendaId: String, location: Location) {
        val motoristaId = FirebaseAuth.getInstance().currentUser?.uid
        if (motoristaId.isNullOrBlank()) {
            Log.e(TAG, "Localização da venda $vendaId não enviada: motorista não autenticado.")
            return
        }
        val dados = mutableMapOf<String, Any?>(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "precisaoMetros" to location.accuracy
                .takeIf { location.hasAccuracy() }
                ?.toDouble(),
            "motoristaId" to motoristaId,
            "timestamp" to (
                location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        )
        firestore.collection("localizacoes_tempo_real")
            .document(vendaId)
            .set(dados, SetOptions.merge())
    }

    private fun pararAtualizacoes(limparNoServidor: Boolean = false) {
        servicoAtivo = false
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        if (limparNoServidor) {
            vendasIdsAtivas.forEach { vendaId ->
                limparCacheVenda(vendaId)
                removerLocalizacaoServidor(vendaId)
            }
        }
        vendasIdsAtivas.clear()
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rastreio de Entrega",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação enquanto a entrega está em andamento"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Entrega em andamento")
            .setContentText("Enviando sua localização em tempo real")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        pararAtualizacoes()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
