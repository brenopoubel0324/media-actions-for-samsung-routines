# Permission Manager — widget Reprodução e volume

Data: 14/11/2026. Repositório: `/workspace/media-actions-review`. Escopo: widget combinado, comandos, apresentação, autorização e recuperação. Skill aplicada: [permission-manager](skill://plugins_6af77ed31d40819198777111f6aacc98/permission-manager/SKILL.md), com matriz de requisitos, fluxos de recuperação, eficiência e checklist.

## Conclusão

**APROVADO na revisão estática após duas leituras.** Nenhum achado alto, bloqueante ou médio permanece aberto nesta fatia. A execução dos testes/builds é coordenada pelo agente principal e não foi realizada por esta auditoria. Este resultado não certifica compatibilidade física, assinatura ou entrega do APK.

## Achado e correção conferida

- **Médio, corrigido:** o clique central sem autorização de notificações falhava sem retorno visível. Em `PlaybackVolumeWidgetProvider.kt:53–64`, o resultado `MissingNotificationAccess` agora mostra uma instrução EN/PT-BR para abrir o app e conceder o acesso pertinente, deixando explícita a independência do volume. Não abre Activity, não solicita acesso automaticamente e não interfere nos comandos laterais.
- A guarda `AtomicBoolean.compareAndSet` acontece antes do Toast e do refresh. Assim, conclusão duplicada não repete aviso/refresh; `pending.finish()` fica em `finally`.
- `PlaybackVolumeWidgetTest.kt:169–188` passou a verificar o texto via `ShadowToast`, ausência de comando de mídia, funcionamento do próximo gesto de volume e ausência de Activity aberta. O teste está escrito; seu resultado deve ser citado a partir da execução do agente principal.

## Inventário por função

| Função | Requisito | Consulta/estado verificável | Gesto de correção | Recuperação |
| --- | --- | --- | --- | --- |
| Volume −/+ | Permissão normal `MODIFY_AUDIO_SETTINGS`, já existente; política de áudio pode impedir ajuste | `VolumeActions.kt:11–19`: consulta volume fixo, tenta uma vez ajustar somente `STREAM_MUSIC`, informa erro/fixo | Nenhum pedido de acesso a notificações. Política negada tem feedback; não tenta conceder acessos de áudio em lote | Próximo gesto faz nova verificação/tentativa; não há replay do ajuste anterior |
| Play/Pause | Acesso especial ao listener e sessão elegível com capacidade anunciada | `MediaActionDispatcher.kt:11–25,44–56`: consulta fresca do grant, captura/seleção atual e comando suportado; falha não vira acesso concedido | Após o gesto sem acesso, Toast contextual. Em `MainActivity.kt:159–165`, botão abre ajustes públicos específicos, com fallback tratado | Retorno da Activity revalida em `MainActivity.kt:113–135`; novo gesto executa nova seleção, sem reproduzir o comando incerto anterior |
| Apresentação do estado | Grant atual e dados de uma sessão; observador pertence ao ciclo do listener | `WidgetPlaybackObserver.kt:156–217`: revogação/desconhecimento limpa apresentação; capacidade concedida não equivale a serviço conectado | Renderização não apresenta diálogos nem abre ajustes | `MediaNotificationListener.kt:15–35`: desconexão encerra observador e força UNKNOWN; rebind apenas quando grant continua válido; conexão cria um observador novo |
| Progresso opcional | Consumidor com tamanho utilizável, opção ligada, reprodução avançando e tela interativa | `WidgetPlaybackObserver.kt:92–114`: filtra alvos e mantém o pulso existente apenas sob essas condições | Ativação/desativação pelo editor; nenhum acesso adicional | Tela apagada/close remove callbacks; retomada e resize reavaliam por eventos, sem alarmes ou wake locks |

## Guardas verificadas

- Novo provider não exportado; identidade estrita de componente, ID, provider e URI antes da execução (`PlaybackVolumeWidgetProvider.kt:35–45,79–92`). PendingIntents imutáveis e individualizados por ação/instância.
- Volume não chama dispatcher de mídia, Spotify, Activity ou refresh de reprodução; o executor compartilhado não mantém nível em polling (`VolumeActions.kt:7–21`).
- Renderer mantém lados e suas bordas independentes de UNKNOWN (`WidgetRenderer.kt:153–162`); apenas o centro depende da apresentação de reprodução. Área insuficiente usa layout sem comandos.
- Desconexão força UNKNOWN antes de reconexão, mesmo com grant ainda existente; `WidgetUpdateSnapshot.capture` retorna UNKNOWN sem consultar sessões quando esse estado é forçado.
- Caches servem somente à apresentação. Ações revalidam acesso e estado no dispatcher. Callbacks de sessão usam geração/identidade, e `close()` remove callbacks, pulso, subscriptions e cache (`WidgetPlaybackObserver.kt:220–308,369–380`).
- Manifest fonte e manifest mesclado Debug inspecionados: única permissão solicitada continua `MODIFY_AUDIO_SETTINGS`; receiver combinado não exportado. MinSdk 33, compile/target 36. O manifest Release gerado ainda correspondia ao build anterior no momento da leitura e permanece sujeito à cadeia completa do agente principal.
- Não há novo pedido de acessibilidade, isenção de bateria, alarme, FGS ou provedor privilegiado. Android Privilege não se aplica a esta fatia.
- `git diff --check` passou na segunda leitura. Não foram executados Gradle, testes ou instalação por esta auditoria.

## Fontes e limites

APIs confrontadas com documentação oficial: [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) e [AudioManager](https://developer.android.com/reference/android/media/AudioManager), consultadas em 14/11/2026. A primeira documenta conexão/desconexão e `requestRebind`; a segunda documenta volume fixo e possibilidade de rejeição pela política de áudio. Não se adicionou novo acesso para contornar esses limites.

Permanecem externas à aprovação estática: comportamento OEM/LockStar real, concessão/retirada no aparelho, morte normal versus Forçar parada, recuperação física do listener, inspeção do Release final e assinatura. Contadores de consultas/RemoteViews demonstram trabalho somente quando seus testes passam; não demonstram economia de bateria medida.
