const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
  onDocumentUpdated,
  onDocumentWritten,
} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");

initializeApp();

const db = getFirestore();

async function enviarNotificacao(
  destinatarios,
  vendaId,
  data,
  tipo,
  mostrarNaCentral = false,
) {
  const documentos = await Promise.all(
    destinatarios.map((uid) => db.collection("usuarios").doc(uid).get()),
  );
  const tokens = new Set();
  const tokenParaUsuario = new Map();

  documentos.forEach((documento) => {
    const listaTokens = documento.get("fcmTokens");
    if (!Array.isArray(listaTokens)) return;
    listaTokens.filter((token) => typeof token === "string" && token.length > 0)
      .forEach((token) => {
        tokens.add(token);
        tokenParaUsuario.set(token, documento.ref);
      });
  });

  if (tokens.size === 0) {
    logger.warn("Nenhum dispositivo FCM registrado para a entrega", {
      vendaId,
      destinatarios,
      tipo,
    });
    return { sucessos: 0, falhas: 0 };
  }

  const tokensInvalidos = [];
  const listaTokens = [...tokens];
  let sucessos = 0;
  let falhas = 0;
  for (let inicio = 0; inicio < listaTokens.length; inicio += 500) {
    const lote = listaTokens.slice(inicio, inicio + 500);
    const android = {
      priority: "high",
      ttl: 60 * 60 * 1000,
    };
    const payload = {
      tokens: lote,
      data,
      android,
    };
    if (mostrarNaCentral) {
      payload.notification = {
        title: data.titulo,
        body: data.mensagem,
      };
      android.notification = {
        channelId: "entregas_proximas",
        clickAction: "com.example.marketplace.ABRIR_RASTREAMENTO",
        icon: "ic_stat_delivery",
        priority: "high",
        sound: "default",
        tag: `${tipo}:${vendaId}`,
      };
    }

    const resposta = await getMessaging().sendEachForMulticast(payload);

    sucessos += resposta.successCount;
    falhas += resposta.failureCount;
    resposta.responses.forEach((resultado, indice) => {
      if (
        !resultado.success &&
        [
          "messaging/invalid-registration-token",
          "messaging/registration-token-not-registered",
        ].includes(resultado.error?.code)
      ) {
        tokensInvalidos.push(lote[indice]);
      }
    });
  }

  await Promise.all(tokensInvalidos.map((token) => {
    const usuarioRef = tokenParaUsuario.get(token);
    return usuarioRef
      ? usuarioRef.update("fcmTokens", FieldValue.arrayRemove(token))
      : Promise.resolve();
  }));

  logger.info("Notificação de entrega enviada", {
    vendaId,
    tipo,
    dispositivos: sucessos,
    falhas,
  });
  return { sucessos, falhas };
}

function coordenadaValida(latitude, longitude) {
  return Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= -90 && latitude <= 90 &&
    longitude >= -180 && longitude <= 180 &&
    (latitude !== 0 || longitude !== 0);
}

function distanciaMetros(latitudeA, longitudeA, latitudeB, longitudeB) {
  const radianos = (graus) => graus * Math.PI / 180;
  const deltaLatitude = radianos(latitudeB - latitudeA);
  const deltaLongitude = radianos(longitudeB - longitudeA);
  const a = Math.sin(deltaLatitude / 2) ** 2 +
    Math.cos(radianos(latitudeA)) *
    Math.cos(radianos(latitudeB)) *
    Math.sin(deltaLongitude / 2) ** 2;
  const fracaoEsfera = Math.min(1, Math.max(0, a));
  return 6_371_000 * 2 *
    Math.atan2(Math.sqrt(fracaoEsfera), Math.sqrt(1 - fracaoEsfera));
}

async function buscarDestinoEntrega(vendaId, venda) {
  const rastreios = await db.collection("rastreios_entrega")
    .where("vendaId", "==", vendaId)
    .limit(1)
    .get();
  const rastreio = rastreios.docs[0];
  const destinoLat = rastreio?.get("destinoLat");
  const destinoLng = rastreio?.get("destinoLng");
  if (coordenadaValida(destinoLat, destinoLng)) {
    return { latitude: destinoLat, longitude: destinoLng };
  }

  if (typeof venda.compradorId !== "string" || !venda.compradorId) return null;
  const comprador = await db.collection("usuarios").doc(venda.compradorId).get();
  const latitude = comprador.get("latitude");
  const longitude = comprador.get("longitude");
  return coordenadaValida(latitude, longitude)
    ? { latitude, longitude }
    : null;
}

async function processarAtualizacaoLocalizacao(event) {
  const localizacao = event.data?.after?.data();
  if (!localizacao || localizacao.proximidadeNotificada === true) return;

  const vendaId = event.params.vendaId;
  const vendaRef = db.collection("vendas").doc(vendaId);
  const vendaSnapshot = await vendaRef.get();
  if (!vendaSnapshot.exists) {
    logger.warn("Localização recebida sem venda correspondente", { vendaId });
    return;
  }

  const venda = vendaSnapshot.data();
  const agora = Date.now();
  const timestamp = localizacao.timestamp;
  const latitude = localizacao.latitude;
  const longitude = localizacao.longitude;
  const precisao = localizacao.precisaoMetros;
  let status = "fora_do_raio";
  let distancia = null;

  if (!["A_CAMINHO", "SAIU_PARA_ENTREGA"].includes(venda.status)) {
    status = "entrega_nao_ativa";
  } else if (
    typeof localizacao.motoristaId !== "string" ||
    localizacao.motoristaId !== venda.motoristaId
  ) {
    status = "motorista_divergente";
  } else if (
    !Number.isFinite(timestamp) ||
    agora - timestamp > 15_000 ||
    agora - timestamp < -5_000
  ) {
    status = "localizacao_desatualizada";
  } else if (
    !Number.isFinite(precisao) ||
    precisao < 0 ||
    precisao > 10
  ) {
    status = "gps_impreciso";
  } else if (!coordenadaValida(latitude, longitude)) {
    status = "coordenada_invalida";
  } else {
    const destino = await buscarDestinoEntrega(vendaId, venda);
    if (!destino) {
      status = "destino_indisponivel";
    } else {
      distancia = distanciaMetros(
        latitude,
        longitude,
        destino.latitude,
        destino.longitude,
      );
      status = distancia <= 10 ? "dentro_do_raio" : "fora_do_raio";
    }
  }

  const resultado = await db.runTransaction(async (transacao) => {
    const snapshot = await transacao.get(vendaRef);
    if (!snapshot.exists) return { ignorar: true };
    const atual = snapshot.data();
    if (atual.proximidadeNotificada === true) {
      return { jaNotificada: true };
    }

    let statusAtual = status;
    if (!["A_CAMINHO", "SAIU_PARA_ENTREGA"].includes(atual.status)) {
      statusAtual = "entrega_nao_ativa";
    } else if (atual.motoristaId !== localizacao.motoristaId) {
      statusAtual = "motorista_divergente";
    }

    const timestampAnterior = Number(atual.proximidadeUltimoFixTimestamp) || 0;
    if (
      statusAtual === "dentro_do_raio" &&
      Number.isFinite(timestamp) &&
      timestamp <= timestampAnterior
    ) {
      return { duplicada: true };
    }

    const leiturasAnteriores = Number(atual.proximidadeLeiturasValidas) || 0;
    const leiturasValidas = statusAtual === "dentro_do_raio"
      ? leiturasAnteriores + 1
      : 0;
    const notificadaAgora = statusAtual === "dentro_do_raio" && leiturasValidas >= 2;
    if (notificadaAgora) statusAtual = "proximidade_detectada";

    const ultimaAtualizacaoDiagnostico =
      Number(atual.proximidadeDiagnosticoEm) || 0;
    const deveAtualizarDiagnostico =
      statusAtual !== atual.proximidadeDiagnostico ||
      agora - ultimaAtualizacaoDiagnostico >= 10_000 ||
      notificadaAgora;
    const deveAtualizarLeituras =
      leiturasValidas !== leiturasAnteriores ||
      (statusAtual !== "dentro_do_raio" &&
        Number(atual.proximidadeLeiturasValidas) > 0);

    if (!deveAtualizarDiagnostico && !deveAtualizarLeituras) {
      return { atualizado: false };
    }

    const atualizacao = {
      proximidadeLeiturasValidas: leiturasValidas,
    };
    if (deveAtualizarDiagnostico) {
      atualizacao.proximidadeDiagnostico = statusAtual;
      atualizacao.proximidadeDistanciaMetros = distancia;
      atualizacao.proximidadePrecisaoMetros =
        Number.isFinite(precisao) ? precisao : null;
      atualizacao.proximidadeDiagnosticoEm = agora;
    }
    if (statusAtual === "dentro_do_raio" || notificadaAgora) {
      atualizacao.proximidadeUltimoFixTimestamp = timestamp;
    }
    if (notificadaAgora) {
      atualizacao.proximidadeNotificada = true;
      atualizacao.proximidadeNotificadaEm = agora;
    }
    transacao.update(vendaRef, atualizacao);
    return { notificadaAgora };
  });

  if (resultado.jaNotificada || resultado.notificadaAgora) {
    await event.data.after.ref.set(
      { proximidadeNotificada: true },
      { merge: true },
    );
  }
}

function formatarDuracao(segundos) {
  const minutos = Math.max(1, Math.round(segundos / 60));
  if (minutos < 60) return `${minutos} min`;
  const horas = Math.floor(minutos / 60);
  const minutosRestantes = minutos % 60;
  return minutosRestantes === 0
    ? `${horas} h`
    : `${horas} h ${minutosRestantes} min`;
}

async function buscarPrevisaoEntrega(vendaId) {
  const [rastreios, comprovante, localizacao] = await Promise.all([
    db.collection("rastreios_entrega")
      .where("vendaId", "==", vendaId)
      .limit(1)
      .get(),
    db.collection("comprovantes_retirada").doc(vendaId).get(),
    db.collection("localizacoes_tempo_real").doc(vendaId).get(),
  ]);
  const rastreio = rastreios.docs[0];
  if (!rastreio) return null;

  const destinoLat = rastreio.get("destinoLat");
  const destinoLng = rastreio.get("destinoLng");
  const retiradaLat = comprovante.get("latitude");
  const retiradaLng = comprovante.get("longitude");
  const localLat = localizacao.get("latitude");
  const localLng = localizacao.get("longitude");
  const origem = coordenadaValida(retiradaLat, retiradaLng)
    ? [retiradaLat, retiradaLng]
    : [localLat, localLng];

  if (!coordenadaValida(...origem) || !coordenadaValida(destinoLat, destinoLng)) {
    return null;
  }

  const url = new URL(
    `https://router.project-osrm.org/route/v1/driving/` +
      `${origem[1]},${origem[0]};${destinoLng},${destinoLat}`,
  );
  url.searchParams.set("overview", "false");
  url.searchParams.set("steps", "false");
  const resposta = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (!resposta.ok) return null;

  const dados = await resposta.json();
  const rota = dados.code === "Ok" ? dados.routes?.[0] : null;
  return rota && Number.isFinite(rota.duration)
    ? {
      duracao: formatarDuracao(rota.duration),
      distancia: Number.isFinite(rota.distance)
        ? `${(rota.distance / 1000).toFixed(1)} km`
        : null,
    }
    : null;
}

exports.avisarEntregadorProximo = onDocumentUpdated(
  {
    document: "vendas/{vendaId}",
    retry: true,
  },
  async (event) => {
    const anterior = event.data.before.data();
    const atual = event.data.after.data();

    if (
      anterior.proximidadeNotificada === true ||
      atual.proximidadeNotificada !== true ||
      !["A_CAMINHO", "SAIU_PARA_ENTREGA"].includes(atual.status)
    ) {
      return;
    }

    const destinatarios = [...new Set([atual.compradorId, atual.vendedorId])]
      .filter((uid) => typeof uid === "string" && uid.length > 0);

    const resposta = await enviarNotificacao(
      destinatarios,
      event.params.vendaId,
      {
        tipo: "ENTREGADOR_PROXIMO",
        vendaId: event.params.vendaId,
        titulo: "Seu entregador está chegando",
        mensagem: "O entregador está a menos de 10 metros do endereço de entrega.",
      },
      "ENTREGADOR_PROXIMO",
    );
    await event.data.after.ref.update({
      proximidadeNotificacaoStatus: resposta.sucessos > 0
        ? "enviada"
        : "sem_dispositivo",
      proximidadeNotificacaoSucessos: resposta.sucessos,
      proximidadeNotificacaoFalhas: resposta.falhas,
      proximidadeNotificacaoEm: Date.now(),
    });
    if (resposta.sucessos === 0) {
      throw new Error(
        `Nenhuma notificação de proximidade foi enviada para a venda ${event.params.vendaId}.`,
      );
    }
  },
);

exports.avisarMudancaLocalizacaoMotorista = onDocumentWritten(
  {
    document: "localizacoes_tempo_real/{vendaId}",
    retry: true,
  },
  processarAtualizacaoLocalizacao,
);

exports.avisarEntregaIniciada = onDocumentUpdated(
  {
    document: "vendas/{vendaId}",
    retry: true,
  },
  async (event) => {
    const anterior = event.data.before.data();
    const atual = event.data.after.data();
    const statusAnteriorAtivo = ["A_CAMINHO", "SAIU_PARA_ENTREGA"]
      .includes(anterior.status);
    const statusAtualAtivo = ["A_CAMINHO", "SAIU_PARA_ENTREGA"]
      .includes(atual.status);
    if (statusAnteriorAtivo || !statusAtualAtivo) {
      return;
    }

    const preparada = await db.runTransaction(async (transacao) => {
      const vendaAtualSnapshot = await transacao.get(event.data.after.ref);
      if (!vendaAtualSnapshot.exists ||
        !["A_CAMINHO", "SAIU_PARA_ENTREGA"].includes(
          vendaAtualSnapshot.get("status"),
        )) {
        return false;
      }

      const atualizacao = {
        notificacaoInicioStatus: "enviando",
        notificacaoInicioSucessos: 0,
        notificacaoInicioFalhas: 0,
        notificacaoInicioEm: Date.now(),
      };
      if (vendaAtualSnapshot.get("proximidadeNotificada") !== true) {
        atualizacao.proximidadeDiagnostico = "aguardando_localizacao";
        atualizacao.proximidadeLeiturasValidas = 0;
        atualizacao.proximidadeUltimoFixTimestamp = 0;
        atualizacao.proximidadeDistanciaMetros = null;
        atualizacao.proximidadePrecisaoMetros = null;
        atualizacao.proximidadeDiagnosticoEm = Date.now();
      }
      transacao.update(event.data.after.ref, atualizacao);
      return true;
    });
    if (!preparada) return;

    const destinatarios = [...new Set([atual.compradorId, atual.vendedorId])]
      .filter((uid) => typeof uid === "string" && uid.length > 0);
    const previsao = await buscarPrevisaoEntrega(event.params.vendaId).catch((erro) => {
      logger.warn("Não foi possível calcular a previsão de chegada", {
        vendaId: event.params.vendaId,
        erro: erro.message,
      });
      return null;
    });

    const produtoTitulo = atual.produtoTitulo || `Pedido #${event.params.vendaId.slice(0, 8)}`;
    const detalhesPrevisao = previsao
      ? `Previsão de chegada: ~${previsao.duracao}` +
        (previsao.distancia ? ` (${previsao.distancia}).` : ".")
      : "Toque para acompanhar a localização e ver a previsão de chegada no mapa.";
    const resposta = await enviarNotificacao(
      destinatarios,
      event.params.vendaId,
      {
        tipo: "ENTREGA_INICIADA",
        vendaId: event.params.vendaId,
        titulo: "Seu pedido saiu para entrega",
        mensagem: `O pedido "${produtoTitulo}" saiu para entrega. Deseja acompanhar a localização? ${detalhesPrevisao}`,
      },
      "ENTREGA_INICIADA",
      true,
    );

    await event.data.after.ref.update({
      notificacaoInicioStatus: resposta.sucessos > 0
        ? "enviada"
        : "sem_dispositivo",
      notificacaoInicioSucessos: resposta.sucessos,
      notificacaoInicioFalhas: resposta.falhas,
      notificacaoInicioEm: Date.now(),
    });
    if (resposta.sucessos === 0) {
      throw new Error(`Nenhuma notificação de início foi entregue para a venda ${event.params.vendaId}.`);
    }
  },
);

exports.limparComprovanteRetiradaAoCancelar = onDocumentUpdated(
  {
    document: "vendas/{vendaId}",
    retry: true,
  },
  async (event) => {
    const anterior = event.data.before.data();
    const atual = event.data.after.data();
    if (
      anterior.status === atual.status ||
      !["PRONTO_PARA_ENTREGA", "PENDENTE", "CANCELADA"].includes(atual.status)
    ) {
      return;
    }

    await db.collection("comprovantes_retirada")
      .doc(event.params.vendaId)
      .delete();
    logger.info("Comprovante de retirada removido após cancelamento/devolução", {
      vendaId: event.params.vendaId,
      status: atual.status,
    });
  },
);
