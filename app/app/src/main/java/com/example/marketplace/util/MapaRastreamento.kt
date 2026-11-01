package com.example.marketplace.util

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.marketplace.service.Rota
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

enum class TipoMarcador { ORIGEM, DESTINO, MOTORISTA }

data class MarcadorMapa(
    val latitude: Double,
    val longitude: Double,
    val titulo: String,
    val tipo: TipoMarcador
)

/** Estado da câmera que NÃO deve causar recomposição. */
private class EstadoCamera {
    var etapaEnquadrada = 0          // 0 = nada, 1 = sem motorista, 2 = com motorista
    var usuarioMexeu = false         // true depois que o usuário tocar no mapa
    var ultimoPedidoManual = 0
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapaRastreamento(
    origem: MarcadorMapa?,
    destino: MarcadorMapa?,
    motorista: MarcadorMapa?,
    modifier: Modifier = Modifier,
    rota: Rota? = null,
    corRota: Int = 0xFF1A73E8.toInt()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val camera = remember { EstadoCamera() }
    var pedidoManual by remember { mutableStateOf(0) }

    val mapView = remember {
        configurarOsmdroid(context)
        MapView(context).apply {
            setTileSource(TilesGeoapify)
            setMultiTouchControls(true)
            maxZoomLevel = 19.0
            controller.setZoom(14.0)

            // Se o usuário mexer no mapa, nunca mais mexemos na câmera por conta própria.
            // Também impede que o scroll da tela "roube" o gesto de arrastar o mapa.
            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        camera.usuarioMexeu = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(origem, destino, motorista, rota, pedidoManual) {
        // ---------- 1) Desenha overlays (isso NÃO mexe no zoom/posição) ----------
        mapView.overlays.clear()

        val pontosRota = rota?.pontos?.map { GeoPoint(it.latitude, it.longitude) }.orEmpty()
        if (pontosRota.size >= 2) {
            mapView.overlays.add(
                Polyline().apply {
                    setPoints(pontosRota)
                    outlinePaint.color = corRota
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                }
            )
        }

        fun ponto(m: MarcadorMapa?): GeoPoint? {
            if (m == null || (m.latitude == 0.0 && m.longitude == 0.0)) return null
            return GeoPoint(m.latitude, m.longitude)
        }

        fun adicionar(marcador: MarcadorMapa?, emoji: String): GeoPoint? {
            val p = ponto(marcador) ?: return null
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = p
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "$emoji ${marcador!!.titulo}"
                }
            )
            return p
        }

        val pOrigem = adicionar(origem, "🏪")
        val pDestino = adicionar(destino, "🏁")
        val pMotorista = adicionar(motorista, "🚗")

        // ---------- 2) Câmera: só enquadra quando REALMENTE precisa ----------
        // Com motorista na rua, enquadramos só motorista + destino (a loja já ficou para trás).
        val pontosMarcadores = if (pMotorista != null) listOfNotNull(pMotorista, pDestino)
        else listOfNotNull(pOrigem, pDestino)

        val pontosEnquadrar = if (pontosRota.size >= 2) {
            (pontosMarcadores + pontosRota).distinct()
        } else {
            pontosMarcadores
        }

        val etapaAtual = when {
            pMotorista != null -> 2
            pontosMarcadores.isNotEmpty() -> 1
            else -> 0
        }

        val pediuManual = pedidoManual != camera.ultimoPedidoManual
        val deveEnquadrar = pontosEnquadrar.isNotEmpty() && (
                pediuManual || (!camera.usuarioMexeu && etapaAtual > camera.etapaEnquadrada)
                )

        if (deveEnquadrar) {
            camera.etapaEnquadrada = etapaAtual
            camera.ultimoPedidoManual = pedidoManual
            if (pediuManual) camera.usuarioMexeu = false

            mapView.post {
                val box = BoundingBox.fromGeoPoints(pontosEnquadrar)
                val distanciaMinima = box.latNorth - box.latSouth < 0.0001 && box.lonEast - box.lonWest < 0.0001
                if (pontosEnquadrar.size == 1 || distanciaMinima) {
                    mapView.controller.setZoom(16.0)
                    mapView.controller.setCenter(pontosEnquadrar.first())
                } else {
                    mapView.zoomToBoundingBox(box, true, 120)
                }
            }
        }

        mapView.invalidate()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })

        FilledTonalButton(
            onClick = { pedidoManual++ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Text("Recentralizar")
        }
    }
}