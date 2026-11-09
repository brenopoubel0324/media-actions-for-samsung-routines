# Volume de mídia por clique — 0.25 experimental

No mesmo APK do Media Actions, um segundo tipo de widget oferece **− e +** para o volume de mídia **do aparelho onde foi instalado**. Destinado ao uso no Galaxy Tab S10+ solicitado, sem teste físico desse tablet nesta execução. Requer Android 13 ou posterior, como o restante do app.

Após atualizar, abra o seletor de widgets da tela inicial → **Media Actions → Volume de mídia**. Cada toque solicita um passo ao sistema; não é uma porcentagem fixa e a saída atual (alto-falante/Bluetooth) pode impor limites. Não controla Spotify Connect remoto nem o volume do celular a partir do tablet. Não precisa de sessão de mídia nem de acesso às notificações. Não altera toque, alarme, chamada ou modo Não Perturbe por código próprio. O sistema pode rejeitar uma alteração conforme sua política.

A permissão normal **MODIFY_AUDIO_SETTINGS** é declarada no APK phone para ajustar áudio local; o Android a concede na instalação, sem diálogo runtime. Não há pedido de microfone, acessibilidade, DND, root ou captura global de teclas. Wear 0.9 permanece igual, com as três ações de reprodução existentes.

## Aparência e edição

Fundo transparente, tamanho redimensionável, dois alvos com mínimo de 48dp quando o host comporta a área, tamanho/cor/sombra dos símbolos, distância, padding vertical negativo e bordas dos botões/moldura independentes. Os símbolos fixos −/+ não usam as famílias de playback. Sem botão central, progresso ou porcentagem. Sem dimensões fornecidas pelo host, a geometria usa o fallback conservador do renderer existente.

**Editar widget instalado** lista volume e reprodução, com tipo, local e número/dimensões. O editor externo também abre a instância correta quando o host oferece esse menu. Salvar/Cancelar, rascunho/recriação, visual próprio/herdado e identificação independente usam as mesmas chaves existentes. O padrão global de aparência pode ser herdado por ambos; progresso e borda apenas central são ignorados no widget de volume. Salvar padrões atualiza os dois tipos; ticks da música não redesenham o volume.

A prévia do editor não ajusta volume. Não há um segundo aplicativo no telefone/tablet; atualize sobre o original. Widgets já existentes de reprodução não são convertidos para volume.

## Consumo e limites

Uma consulta de política e uma chamada `AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER/RAISE, 0)` por clique válido. Sem Activity, flags de painel de volume/som, timer, polling de nível, wake lock, sincronização de relógio ou fila/retry. Sem atualizações periódicas no provider (`updatePeriodMillis=0`). Volume fixo ou exceção retornam aviso; nenhum reenvio. Sem visibilidade do nível, não se presume confirmação sonora.

Testes com doubles/sombras conferem stream/direção/flags, comandos inválidos/removidos/outro tipo, limites do modelo, ausência de outras ações, editor/isolamento/herança, falha global de host/retry e geometria. O ShadowAudioManager 4.17 tem helper que altera todos os streams: o fixture modela explicitamente o stream alvo e registra cada chamada. Isso não é prova do AudioService Samsung. Capturas Android/Skia são nativas, sem simular fotografia de hardware.

Pendentes no Tab S10+: clique/limites reais, saída Bluetooth, volume fixo, DND, tela bloqueada/LockStar, redimensionamento e consumo físico. As chamadas seguem a política do Android; não há promessa de controlar uma saída que o sistema proíbe.

Fontes: [AudioManager](https://developer.android.com/reference/android/media/AudioManager#adjustStreamVolume(int,int,int)), [implementação Android 16](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/media/java/android/media/AudioManager.java), [AudioService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/audio/AudioService.java).

## Orientação automática — 0.25

Redimensionar o **próprio widget** muda o eixo sem recriar a instância: retângulo mais largo usa − à esquerda/+ à direita; mais alto usa + acima/− abaixo. Quadrado e host sem tamanho informado usam horizontal. A seleção é por tamanho de cada variante de RemoteViews e pelo callback de resize do host, sem sensor, polling ou nova preferência.

Distância passa a ser aplicada no eixo dos botões. Padding horizontal/vertical continua nos eixos físicos; o vertical negativo permanece vertical. O host pode impor tamanho mínimo/corte; para dois alvos de 48dp, é necessário espaço para pelo menos 96dp no eixo dos botões. A prévia do editor conserva a escala limitada de demonstração; o diagnóstico descreve o primeiro tamanho fornecido, não comprova qual variante foi pintada pelo host. Controles de reprodução de três botões não mudam de orientação.
