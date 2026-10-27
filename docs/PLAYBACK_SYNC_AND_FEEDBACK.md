# Play/Pause sincronizado e feedback de toque

Implementado na prévia `0.7-preview` e incluído na versão `0.8-experimental`. As imagens foram apresentadas antes da publicação; validação física Samsung permanece pendente.

## Estado confirmado

O botão central mostra Pausa quando a sessão selecionada informa reprodução e Play quando informa pausa/parada. Durante transição mostra símbolo neutro com menor opacidade. Sem autorização, sem player elegível, com ambiguidade ou falha de consulta, mostra símbolo neutro sem assumir um estado. O clique não altera o ícone antecipadamente. Se o player ignorar o comando, a imagem continua representando o estado reportado.

A leitura usa MediaSessionSelector, a mesma seleção dos comandos: token de mídia do sistema, pacote único e prioridades já existentes. O estado não é persistido em disco. Todas as famílias têm arte de Play/Pause e mantêm tamanho, espaçamento, cor e sombra.

## Atualização por eventos

MediaNotificationListener cria um WidgetPlaybackObserver ao conectar. Ele observa a lista de sessões ativas, mudanças de PlaybackState e mudança do destinatário de teclas de mídia do sistema. Não captura teclas físicas. Eventos são agrupados na fila principal e atualizações do mesmo estado são suprimidas. Após falha de entrega ao widget, um evento posterior do mesmo estado pode tentar novamente.

Desconexão/destruição removem callbacks e estados pendentes; reconexão consulta o estado novamente. Se o serviço desconectar enquanto a autorização continua concedida, solicita requestRebind uma vez por evento de desconexão. Não solicita retomada quando o usuário revogou a autorização. Usamos o serviço já vinculado pelo Android e o acesso de notificações já existente. Sem polling, alarmes, serviço em primeiro plano ou novas permissões comuns. Não se lê conteúdo de notificações, título, artista ou capa. O listener continua com filtro vazio de categorias.

RemoteViews recebe atualização completa para substituir imagens sem acumular bitmaps em atualizações parciais. O clique do widget continua sendo broadcast explícito e imutável; não abre Activity.

## Feedback de toque

A opção Touch animation está ligada por padrão e pode ser salva por widget ou nos padrões do app. O fundo permanece transparente em repouso. Um ripple nativo e um realce oval de baixa opacidade com entrada/saída suaves respondem ao pressionamento. Há recursos para ícones brancos e escuros. O realce usa transições XML de 60/160 ms; não há timer nem animação comandada pelo serviço.

A prévia da configuração aceita cliques somente para experimentar esse feedback; nunca envia comandos de mídia. Ela mostra o estilo no estado pausado, identificado no texto.

## Evidência e limites

180 testes locais e Android lint aprovados; APIs 33 e 36 para contratos de mídia e serviço; Skia/API 33 para imagens. O GIF é composto de frames do realce/fade XML nativo, com papel de parede de amostra para visualização. Ele não captura a ondulação desenhada pelo host Samsung nem é uma captura do S25. O quadro de comparação mostra estados fornecidos aos RemoteViews.

A reação real de Spotify/fones, o tempo de atualização do host, a animação completa e a interação bloqueada ainda exigem teste no S25/LockStar. A presença do menu externo de configuração também permanece pendente de verificação física. Não há mudança nos mapeamentos de tecla lateral.

Fontes Android: [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager), [MediaController.Callback](https://developer.android.com/reference/android/media/session/MediaController.Callback), [RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews), [RippleDrawable](https://developer.android.com/reference/android/graphics/drawable/RippleDrawable).
