# Self-audit — leituras por lote, 0.20

Skill breno-review-audit:self-audit-loop; base `9fc2115415d66ceb0a27a6a55e2c074cb345bf21`; etapa 3 do plano `docs/planos/proximas-medidas-pos-review-0.18.md`. Sem agente independente. Escopo: snapshot efêmero no provider/renderer, progresso da sessão capturada, orçamento de consultas e precedência de UNKNOWN.

## Rodada 1 — achados antes das correções

- **Médio — WidgetRenderer.render:** a primeira implementação preserva um argumento de estado separado que permite cair em leitura de progresso sem snapshot. Não há caller que precise dessa compatibilidade. Remover esse caminho: render deve receber um snapshot ou capturá-lo uma vez, sempre tratando progresso nulo como final.

Nenhum bloqueante/alto. A simplificação evita uma segunda representação do mesmo contrato. Comandos continuam consultando sessões novas e não usam o snapshot de renderização. O helper compartilhado é aditivo, sem mudança no protocolo Wear.

## Rodada 2 — revisão do contrato completo

Caminho alternativo removido. Todos os callers de render agora capturam uma vez ou passam o snapshot do lote. Nenhum bloqueante/alto/médio aberto na leitura completa de provider, renderer, observer, listener, gateway e callers de comandos. UNKNOWN explícito retorna antes de qualquer acesso; snapshot nulo e progresso explicitamente nulo são situações distintas. Helper de posição consulta somente o controller selecionado e seu playback capturado; não escolhe outro player. A interface de comandos, destinos e protocolo v1 foi preservada.

Risco residual: o estado real pode mudar imediatamente após qualquer snapshot do sistema; callbacks futuros continuam sendo a fonte de atualização. O snapshot dura somente o lote. A comparação não mede consumo físico.

## Rodada 3 — APROVADO

Nenhum bloqueante/alto/médio/baixo pendente. Simplificação corrigida e validada: **413 testes**, build dos dois APKs e lint dos três módulos passaram. Orçamento do lote e do ciclo completo verificados em API 33/36; seis widgets em 5 s passaram de 96/84 para 36/24 leituras de sessão/progresso por minuto, mantendo 12 ticks e 72 atualizações. Nenhuma alteração visual nesta entrega; seletor da 0.19 já mostrado permanece igual. `git diff --check` passou.

Bug sweep sem novo defeito confirmado no escopo. Teste físico de energia/CPU, comportamento do host Samsung e integração Watch/WBR continuam pendentes. Sem alegação de economia de bateria. Revisão não é avaliação independente.
