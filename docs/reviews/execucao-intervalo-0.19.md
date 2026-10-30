# Self-audit — linha de base e intervalo, 0.19

Skill breno-review-audit:self-audit-loop. Base: `196821d73fc5335cf36c2c2360def13fb6106dd1`; plano `docs/planos/proximas-medidas-pos-review-0.18.md`, etapas 1/2. Sem agente independente. Escopo: medir contagens em teste, configurar intervalo global 5/15/30, manter liga/desliga individual e proteções de tela/estado/duração. Sem medição física ou alteração do protocolo Wear.

## Rodada 1 — achados antes das correções

- **Baixo — WidgetPlaybackObserver/observeWork:** a condição do hook de contagem avalia progressWidgets mesmo sem listener de instrumentação, acrescentando consulta de preferências em eventos de mesmo estado. Verificar observeWork != null antes de calcular a condição de instrumentação. O hook não deve criar coleta/logs ou consultas quando não usado.
- **Baixo — recursos de intervalo:** lint apontou PluralsCandidate no rótulo com quantidade de segundos. Usar plurals no lugar de string com quantidade variável.

Nenhum bloqueante/alto. Conferir cadência, Save/Cancel/recriação, preferência inválida, timer substituído e pausa/fechamento. A linha de base usa código 0.18 instrumentado em teste e sombras controladas; não é uma comparação física dos APKs nem medida de energia.

## Rodada 2 — tradução e regressões

Os 405 testes passaram após as correções; lint identificou a categoria `many` exigida para plurais em português. **Baixo — strings pt-BR:** acrescentar a forma many, mantendo as opções 5/15/30 e sem suprimir lint. O hook agora evita o cálculo adicional quando não instrumentado. Nenhum bloqueante/alto.

## Rodada 3 — APROVADO

Sem bloqueante/alto ou achado aberto. Os três ajustes baixos foram corrigidos: hook sem consulta extra quando desativado e plurais completos. Build, lint dos três módulos e **405 testes** passaram. Save/Cancel/recriação, valores inválidos, 12/4/2 ticks por minuto, troca de timer e eventos imediatos foram validados em API 33/36. Imagem nativa do seletor foi mostrada antes da publicação.

Bug sweep sem novo defeito confirmado no escopo. O processo não cria coletor permanente nem timer Wear. Métricas apenas em testes/sombras; hook opcional não coleta/persiste/loga quando não usado. Tela interativa não comprova visibilidade e não houve medição de bateria/CPU física ou integração no Galaxy/Watch.
