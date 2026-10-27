# Retomar no Spotify — 0.11 experimental

O Play/Pause do widget, dos atalhos de Rotinas e dos testes no app do celular tenta primeiro controlar a sessão de mídia selecionada pelo Android, como antes. Quando não há sessão elegível, envia um gesto **Play** direcionado somente ao Spotify instalado (`com.spotify.music`). Não abre Activity, não muda a tela ativa e não envia tecla global para outros players.

O fallback consulta o receiver público de `ACTION_MEDIA_BUTTON` da versão instalada, sem fixar nomes internos. Exige exatamente um receiver exportado/habilitado e permissão acessível. Usa DOWN/UP de `KEYCODE_MEDIA_PLAY`, nunca toggle, para evitar pausar uma sessão que iniciou durante a entrega. O padrão de retomada por media button é documentado pelo [Android](https://developer.android.com/media/legacy/media-buttons); o pacote é documentado pelo [Spotify](https://developer.spotify.com/documentation/android/tutorials/content-linking).

**Limite:** o envio não confirma que começou a tocar. Spotify precisa estar instalado, ter conteúdo reproduzível e aceitar a retomada nessa versão/estado. Conta sem login, fila vazia, app forçado a parar, restrições de bateria ou receiver indisponível podem impedir a reprodução. Não há teste físico com o Spotify nesta versão. O ícone do widget só muda quando a sessão informa o novo estado.

Anterior/Próxima não iniciam Spotify. Acesso a notificações ausente, seleção ambígua, sessão transitória, comando não suportado ou falha ao enviar à sessão existente não acionam fallback. Não há repetição automática após falha, abertura da interface ou autenticação OAuth.

Esta função pertence aos controles locais do **celular**. O protocolo Wear mantém suas regras de seleção/leases e não inicia um player quando os aparelhos não oferecem sessão; o companion 0.8 continua compatível.

## Teste no aparelho

1. Atualize o APK phone, mantendo Notification access concedido.
2. Abra o Spotify, faça login e toque algo uma vez para estabelecer uma fila; pause e deixe a sessão encerrar normalmente.
3. Com nenhuma sessão elegível disponível, use Play/Pause do widget ou atalho. Deve tentar retomar o Spotify sem abrir a interface.
4. Com outro player em sessão pausada, Play/Pause deve retomar esse player. Anterior/Próxima sem sessão não devem iniciar Spotify.

Se a versão instalada não expõe um receiver elegível, o resultado continua sendo ausência de sessão; se o envio for aceito mas ignorado, o ícone não inventa estado de reprodução. Não se promete partida a frio universal.
