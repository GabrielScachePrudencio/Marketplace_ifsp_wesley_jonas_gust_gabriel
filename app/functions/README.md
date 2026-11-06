# Notificações de proximidade da entrega

O aplicativo grava `proximidadeNotificada` na venda quando o motorista autenticado
entra no raio de 187 m. Esta função envia um aviso FCM aos dispositivos registrados
do comprador e do negociador, sem enviar a localização do motorista na notificação.

## Preparar e publicar

1. Confirme que o projeto Firebase é `marketplace-ifsp-7bcf9` e que o plano permite
   Cloud Functions (a publicação normalmente exige o plano Blaze e faturamento ativo).
2. No terminal, acesse a pasta `app` do repositório, que contém `firebase.json`.
3. Execute `npm --prefix functions install`.
4. Execute `firebase deploy --only functions:avisarEntregadorProximo`.

Depois da publicação, instale a versão atualizada do app nos dispositivos. Comprador
e negociador precisam abrir o app e conceder a permissão de notificações para que o
dispositivo registre seu token FCM. No Android 13 ou superior, negar essa permissão
impede a exibição local do aviso.
