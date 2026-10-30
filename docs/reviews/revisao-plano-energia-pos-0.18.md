# Revisão do plano energético após a 0.18

Skill: breno-review-audit:execution-plan-review. Plano: [próximas medidas](../planos/proximas-medidas-pos-review-0.18.md). Documento auditado no commit `749d44447ec5362fa247903bd317e1d08b6a6719`; código-base `dd2a0367a29790a337dab42cf4fc9bd043feef84` (phone 0.18/Wear 0.8). Worktree inicialmente limpo. Revisão técnica do plano, sem execução das funcionalidades planejadas ou medição de energia.

**Veredito final: APROVADO após os ajustes descritos abaixo.** Um achado alto e dois médios corrigidos no plano; nenhuma mudança obrigatória aberta. O aceite físico/energético continua pendente de aparelho e dados.

## Achados antes das correções — REVISAR

1. **Alto — etapa 3, snapshot e callers:** o plano propõe consolidar leituras sem explicitar a precedência do estado fornecido pelo chamador. `MediaNotificationListener.stopPlayback` envia UNKNOWN ao desconectar, mesmo quando a concessão de acesso ainda está presente. `MediaWidgetProvider.refreshAll` e `WidgetRenderer.render` hoje respeitam esse estado. Uma otimização que sempre recalcule PLAYING pode reintroduzir indicação de reprodução/bordas durante desconexão. Exigir precedência de UNKNOWN explícito, nenhuma leitura de progresso nesse caso, coerência de sessão e testes dos callers, inclusive updates individuais e por lote.
2. **Médio — etapa 1, comparação energética:** as condições físicas estão definidas, mas falta controlar variante do APK, instrumentação e regressão na mesma cadência. Comparar base em 5 s com candidato em 15 s pode ocultar custo novo do candidato. Registrar hashes/variante/coleta; comparar base 5 s com candidato 5 s e indicador desligado equivalente, depois comparar 5/15/30 dentro do mesmo candidato. Ganho de cadência não compensa uma regressão conhecida no controle equivalente.
3. **Médio — etapa 6, modo de aparência:** `load` devolve a mesma aparência para herança e override com valores iguais ao padrão. O plano não define o modo original e a intenção de salvar quando o usuário restaura e volta a editar, inclusive voltando aos valores anteriores. Comparação apenas de valores pode manter/remover override indevidamente ou ignorar escolha explícita de visual próprio. Distinguir modo original e intenção temporária de manter/herdar/customizar; guardar esse estado na recriação e definir a gravação por intenção, preservando a regra da 0.18 para simples edição de local.

São lacunas do plano para a implementação futura, não bugs novos reproduzidos na 0.18. Nenhum bloqueante identificado. As aprovações anteriores não haviam detalhado esses casos.

## Contexto conferido

- Provider `refreshAll`, `refreshWidget`, `onUpdate` e `onAppWidgetOptionsChanged`; renderer e leitor de progresso.
- Ciclo de vida de `MediaNotificationListener` e `WidgetPlaybackObserver`, incluindo fechamento/reconexão.
- Store de aparência, `has_override`, `WidgetAppearanceSection` e serialização temporária da 0.18.
- Regressões de estado desconhecido/desconexão, herança, alterações externas, rascunho e seleção de instância nos testes existentes.
- Resumer Spotify, fluxo Wear por invocação, critérios energéticos e contratos do protocolo já examinados nas revisões anteriores; nenhuma mudança nesses contratos é proposta nesta revisão.

## Correções incorporadas

- **Achado alto corrigido:** a etapa 3 especifica a precedência do UNKNOWN enviado pelo listener e o comportamento dos caminhos por lote/individuais. Define um snapshot coerente, progresso indisponível sem fallback por widget e orçamento de seleção/leitura verificável. Exige preservar a regressão de desconexão, acrescentar o caso UNKNOWN com sessão PLAYING consultável e distinguir custo do lote do custo completo do observer.
- **Primeiro achado médio corrigido:** a etapa 1 registra artefato/variante/coleta, separa ticks de eventos pelo ponto de origem e inclui controles desligado e 5 s equivalentes entre versões. Só depois compara 5/15/30 dentro do candidato. Uma regressão reproduzível no controle equivalente deve ser corrigida na entrega afetada; menos ticks ou dados inconclusivos não comprovam ganho de bateria.
- **Segundo achado médio corrigido:** a etapa 6 distingue modo original de valores e intenção temporária de manter/herdar/personalizar. Define Save por intenção, callbacks programáticos sem mudança de modo e aceite para valores iguais, edição revertida após restaurar, recriação, mudanças externas e Cancelar. A regra da 0.18 para edição simples de local permanece preservada.
- **Complemento de validação:** a etapa 2 inclui preferência inválida, persistência após reinício do processo e cancelamento de callbacks de observers antigos. O plano não torna a disponibilidade do aparelho um bloqueio para as entregas técnicas independentes.

## Validações e pendências

Revisados o plano integral, alterações propostas, contratos dos callers e regressões próximas. Conferidos links relativos, sequência das etapas, referências e `git diff --check`. Nenhum arquivo de app/shared/Wear foi alterado; a suíte de 394 testes é a base histórica da 0.18 e não foi reexecutada nesta alteração documental.

Na implementação, executar os testes específicos agora exigidos, build/lint e self-audit por entrega. Sem bloqueante/alto remanescente após as correções do plano. Não há nova alegação de economia medida, compatibilidade física ou funcionamento do menu LockStar. Permanecem a validação no S25/Watch 8, isolamento de energia e especificação do ajuste adicional de lockscreen; o plano está aprovado para execução incremental, não para anunciar essas pendências como concluídas.
