# Leituras por lote — 0.20

Comparação com a [linha de base 0.18 e intervalo 0.19](energia-progresso-0.19.md), mesmo harness `EnergyActivityTest`, API 33/36, sessão única PLAYING, duração 300000 ms, posição 1000 ms, tela interativa, widgets com indicador sem sombras e sem tamanhos responsivos. Sessenta segundos de relógio simulado após estabilização; callbacks de preparação excluídos. O hook opcional classifica a origem da atualização, sem coletor de produção. Dados da execução em [API 33](energia/snapshot-0.20-sdk-33.json) e [API 36](energia/snapshot-0.20-sdk-36.json).

## Resultado equivalente em 5 s

| Widgets com indicador | Sessões 0.18/0.19 → 0.20 | Progresso 0.18/0.19 → 0.20 | Ticks | Atualizações / bitmap central |
| --- | --- | --- | --- | --- |
| 1 | 36 → 36 | 24 → 24 | 12 | 12 |
| 3 | 60 → 36 | 48 → 24 | 12 | 36 |
| 6 | 96 → 36 | 84 → 24 | 12 | 72 |

Em 15 s: 4 ticks, 12 consultas de sessão, 8 de progresso, independentemente de 1/3/6 widgets. Em 30 s: 2 ticks, 6 consultas de sessão, 4 de progresso. Indicador desligado: zero trabalho periódico em todos os tamanhos do grupo. Mesmo mecanismo de contagem da base, sem substituir o build histórico por um APK supostamente medido.

Cada lote do provider faz no máximo uma seleção e uma leitura de progresso. O ciclo inteiro ainda inclui uma seleção do observer para estado e outra para elegibilidade do próximo tick, contabilizadas acima. Um widget mantém o mesmo custo; o ganho aparece com múltiplas instâncias. O renderer não repete a consulta quando o progresso capturado é nulo. Widgets sem indicador não recebem atualizações periódicas; testes de lote misto verificam o orçamento constante.

O bitmap observado é a imagem central atribuída ao RemoteViews no fixture sem outras imagens bitmap. Não é contagem geral de alocações no host. As imagens continuam próprias de cada estilo e reutilizadas entre tamanhos responsivos da mesma instância. Não houve cache persistente nem alteração do caminho de comandos ou protocolo Wear; o APK Wear 0.8 continua sendo a distribuição desta entrega.

## Validação e limites

UNKNOWN explícito do listener tem precedência e causa zero consultas; revogação de acesso, lote vazio, ausência de indicadores, progresso indisponível, resize, onUpdate e troca de sessão durante a coleta estão cobertos. A posição é lida do wrapper selecionado e usa o estado capturado desse mesmo controller.

**Atividade simulada: MEDIDO. CPU, alocações físicas, bateria e energia no Galaxy/Watch: NÃO MEDIDO.** Não houve aparelho conectado. Menos consultas não comprova ganho de autonomia. Usar o protocolo físico do plano, incluindo outro app aberto, pausado, tela apagada/AOD e três repetições alternadas, antes de recomendar um perfil energético.

Rastreabilidade: alteração baseada no commit `9fc2115415d66ceb0a27a6a55e2c074cb345bf21`; APK debug phone 0.20/code 20, hash e certificado no `downloads/v0.20-experimental/VALIDATION.json`; patch/código no commit da entrega. Instrumentação de contagem e sombras somente em fontes de teste; observeWork não coleta/persiste/loga quando não usado.
