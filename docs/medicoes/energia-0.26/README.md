# Segundo plano: 0.25 → 0.26

**Redução de trabalho comprovada em testes controlados. Energia, CPU física e autonomia NÃO MEDIDAS.** Não há aparelho conectado ao adb nesta execução. A comparação no mesmo Galaxy S25/Tab S10+ e a recuperação sob o sistema Samsung continuam pendentes.

## Comparação reproduzível

Base: `43dcd29a4692ef3cdd5238131c3895c2dcc2eb7a` (0.25). Candidata: tag `v0.26-experimental`. Testes Robolectric API 33 e 36, mesmas condições por versão: sessão única, duração 300000 ms, posição inicial 1000 ms, velocidade 1, tela interativa, widgets sem sombra/tamanhos responsivos. Janelas de 60 segundos simulados após estabilização, timestamp reiniciado em cada janela. Contadores e injeções de falha existem somente nos testes. Os dois códigos foram executados separadamente no mesmo ambiente/JVM; isso não equivale a medir um dispositivo Android.

| Cenário após estabilização | Consultas getActiveSessions: antes → depois | Leituras de metadados: antes → depois | updateAppWidget: antes → depois |
| --- | ---: | ---: | ---: |
| 6 widgets, progresso 5 s, 60 s | 36 → 0 | 24 → 0 | 72 → 72 |
| 6 widgets, progresso 15 s, 60 s | 12 → 0 | 8 → 0 | 24 → 24 |
| 6 widgets, progresso 30 s, 60 s | 6 → 0 | 4 → 0 | 12 → 12 |
| 6 widgets em pausa, 10 callbacks idênticos | 20 → 0 | 0 → 0 | 60 → 0 |
| Sem widgets, 10 callbacks idênticos | 10 → 0 | 0 → 0 | 0 → 0 |
| Indicador desligado, 60 s | 0 → 0 | 0 → 0 | 0 → 0 |

As atualizações 72/24/12 são necessárias porque o progresso realmente mudou em cada tick. Os resultados para 1/3/6 widgets e ambas as APIs estão nos JSON `base-0.25-sdk-*` e `candidata-0.26-sdk-*`; eventos repetidos em `base-paused-*`/`candidata-paused-*` e `*-no-widgets-*`. O campo startup_session_reads inclui a criação inicial dos widgets pelo fixture e não deve ser confundido com custo do observer isolado. Eventos de preparação ficam fora das janelas comparadas.

A autorização continua sendo verificada em cada refresh elegível, inclusive cada tick: **zero enumerações periódicas não significa zero consultas privilegiadas**. A primeira observação e o resync ao acender a tela fazem uma captura atual. Mudanças da lista de sessões usam a lista fornecida pelo callback; mudanças de playback/metadados alimentam campos mínimos do cache. Eventos são agrupados na fila principal. Comandos continuam verificando autorização, lista, alvo, estado e ações suportadas atuais, sem usar o cache visual.

O cache visual conserva somente chaves/estilo/tamanhos, e o cache de sessão somente controllers e campos necessários durante a vida do listener. Sem conteúdo de notificações, título/artista/capa nem armazenamento de sessão. O anel é quantizado em 1/1024 apenas para apresentação; a mesma fração compõe a chave e o bitmap. Uma chave só é gravada após entrega bem-sucedida. Callbacks idênticos não redesenham; atualização/configuração explícita do host pode forçar entrega para reconstruir a interface.

## Agendamento e recuperação

Um Handler com relógio de uptime, sem AlarmManager, wake lock, job ou serviço adicional. O indicador precisa estar habilitado, em PLAYING, com progresso calculável e avançando e tela interativa. Tela apagada, pausa, velocidade zero, timestamp inválido, fim da projeção, duração ausente, remoção dos consumidores e teardown suspendem o timer. Callbacks reais continuam atualizando estado mesmo com a tela apagada. Não há polling de autorização nem timer de reconexão em repouso.

`BackgroundReliabilityTest` cobre revogação detectada no tick, invalidação para UNKNOWN, ausência de trabalho periódico posterior, recuperação por evento de tela após concessão, encerramento/recriação do serviço, callbacks atrasados, sessão destruída ainda presente em lista temporariamente antiga, falha transitória de consulta, falha de entrega e comandos com estado divergente do cache. Os testes de ciclo de vida existentes cobrem desconexão, requestRebind condicionado à autorização e reconexões repetidas sem acumular inscrições. Uma falha transitória invalida dados e preserva inscrições para o próximo evento; uma revogação/SecurityException remove as inscrições. Nova conexão do listener, tela acesa ou configuração reconstitui a observação. Não há promessa de reconexão automática após **Forçar parada**, que depende de reabrir o app/sistema.

Spotify mantém seu timeout limitado por tentativa; Wear mantém o watchdog por comando. Ambos protegem operações em andamento e não são timers recorrentes de segundo plano.

## Reproduzir os testes

No código candidato: `./gradlew :app:testDebugUnitTest --tests '*EnergyActivityTest*' --tests '*BackgroundWorkComparisonTest*' --tests '*BackgroundReliabilityTest*' --console=plain`.

Para a base, criar worktree do SHA acima e copiar `progress-fixture.kt.txt` para `app/src/test/java/dev/local/mediaactions/EnergyActivityTest.kt` e `comparison-fixture.kt.txt` para `BackgroundWorkComparisonTest.kt`. Executar os dois testes de comparação. Os fixtures arquivados são os da base, incluindo as asserções antigas; os testes atuais exigem os novos orçamentos. Nenhum APK histórico foi apresentado como medido em hardware.

[Protocolo físico e planilha de resultados pendentes](protocolo-dispositivo.md). [Review e validações](../../reviews/background-0.26.md).
