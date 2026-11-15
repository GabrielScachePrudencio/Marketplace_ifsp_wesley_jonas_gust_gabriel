# Notificações de entrega

O app envia notificações FCM ao comprador e ao negociador quando a entrega começa,
com uma previsão de chegada calculada a partir da foto/localização de retirada e do
destino. O payload visual também permite que o Android mostre o aviso na central
quando o app está em segundo plano. Ao tocar nele, o app abre o rastreamento do pedido.

Também envia um aviso quando o motorista autenticado entra no raio de 10 m do
destino. Cada posição nova é validada no servidor pelo motorista atribuído,
precisão GPS de até 10 m, horário recente e duas leituras distintas dentro do
raio. O status, a distância, a precisão e a confirmação de envio FCM ficam
visíveis no cartão “Diagnóstico da entrega” do rastreamento.

Quando o pedido passa a `A_CAMINHO` ou `SAIU_PARA_ENTREGA`, o comprador e o
negociador recebem o aviso de início na central do Android. O diagnóstico mostra
quantos tokens o FCM aceitou; isso confirma o envio ao serviço, não a exibição
efetiva pelo Android. Ao devolver/cancelar a coleta, o comprovante de retirada
é removido do Firestore.

## Preparar e publicar

1. Confirme que o projeto Firebase é `marketplace-ifsp-7bcf9` e que o plano permite
   Cloud Functions (a publicação normalmente exige o plano Blaze e faturamento ativo).
2. No terminal, acesse a pasta `app` do repositório, que contém `firebase.json`.
3. Execute `npm --prefix functions install`.
4. Execute `firebase deploy --only functions`.

Depois da publicação, instale a versão atualizada do app em todos os aparelhos
para que a localização inclua precisão GPS e o identificador do motorista.
Comprador e negociador precisam abrir o app e conceder a permissão de notificações
para registrar o token FCM. No Android 13 ou superior, negar essa permissão impede
a exibição dos avisos. O token é preservado quando o perfil do usuário é
atualizado ou sincronizado.
