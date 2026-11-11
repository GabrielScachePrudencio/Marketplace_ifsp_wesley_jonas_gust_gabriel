const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");

initializeApp();

const db = getFirestore();

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
        vendaId: event.params.vendaId,
        destinatarios,
      });
      return;
    }

    const tokensInvalidos = [];
    const listaTokens = [...tokens];
    let sucessos = 0;
    let falhas = 0;
    for (let inicio = 0; inicio < listaTokens.length; inicio += 500) {
      const lote = listaTokens.slice(inicio, inicio + 500);
      const resposta = await getMessaging().sendEachForMulticast({
        tokens: lote,
        data: {
          tipo: "ENTREGADOR_PROXIMO",
          vendaId: event.params.vendaId,
          titulo: "Seu entregador está chegando",
          mensagem: "O entregador está a menos de 50 metros do endereço de entrega.",
        },
        android: {
          priority: "high",
          ttl: 60 * 60 * 1000,
        },
      });

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

    logger.info("Aviso de proximidade enviado", {
      vendaId: event.params.vendaId,
      dispositivos: sucessos,
      falhas,
    });
  },
);
