# Self-audit — restaurar visual padrão, 0.22

Skill breno-review-audit:self-audit-loop; base `1e681f4349a0089c362c5cea0cab6a36ed1bdaf1`; etapa 6 do plano. Sem agente independente. Escopo: modo original por has_override e intenção temporária MANTER/HERDAR/PERSONALIZAR, edição isolada com prévia/Salvar/Cancelar.

## Rodada 1 — achados antes das correções

- **Médio — WidgetAppearanceSection, intenção após HERDAR:** observar apenas mudanças numéricas válidas não detectaria texto inválido digitado depois da restauração; HERDAR pularia validação e ignoraria a edição. Observar alterações dos campos temporários também, protegendo montagem/restauração programática. Corrigido na implementação inicial antes da rodada seguinte; teste específico recria a tela, exige correção e verifica override explícito ao salvar.

Nenhum bloqueante/alto confirmado. Restaurar reconstrói controles a partir do padrão vigente, descarta campos antigos e não persiste. Save remove somente aparência após validar o ID, preserva local e outros widgets. PERSONALIZAR é explícito mesmo com valores iguais; MANTER preserva a semântica da 0.18 para edição revertida/local e mudanças externas. Originais de valor/modo avançam após aplicar. O editor global não oferece a ação de herdar.

O novo fluxo não adiciona polling ou coleta; reconstrução de prévia acontece apenas em ações/recriação/retomada com padrão alterado. Não muda o agendador de progresso nem o APK Wear.

## Rodada 2 — UX e regressões antes das correções finais

Os testes focados de herança, configuração e regressão da 0.18 passaram em API 33/36. Nenhum bloqueante/alto/médio aberto.

- **Baixo — ajuda do modo visual:** “Usa o visual padrão” sob “Visual próprio” pode sugerir herança já ativa. Trocar por explicação condicional: ao usar o padrão, o widget acompanha o visual definido no app, após Salvar. Evita afirmar que a mudança já foi aplicada.

Corrigir a cópia antes das capturas e da validação completa. Rever modos por has_override, não apenas igualdade: override igual ao padrão continua próprio; restore→editar→reverter continua PERSONALIZAR. Rascunho sem edição efetiva MANTER não repõe modo antigo sobre alteração externa. Sem alterações de comando, protocolo, intervalo ou consulta periódica.

- **Baixo — documentação atual:** README e guias ainda descrevem intervalo fixo de 5 s, diagnóstico sem horário e Wear 0.8 como versão atual. Atualizar as instruções para 5/15/30 s, conclusão com horário/cópia e Wear 0.9, mantendo histórico das implementações anteriores. Sem alteração adicional de código.

## Rodada 3 — APROVADO

Nenhum bloqueante/alto/médio/baixo pendente no escopo técnico. Corrigidos a detecção de texto inválido após HERDAR, a ajuda condicional do modo e os guias desatualizados. **453 testes** (app 416, Wear 15, shared 22), build dos dois módulos e lint dos três passaram. `git diff --check` aprovado. Testes de API 33/36 verificam modo por has_override, override igual ao padrão, restore→editar→reverter, MANTER com mudanças externas, Cancelar, recriação, padrão vigente ao retomar/salvar, rascunho inválido, local preservado, ID removido e editor reutilizável.

Quatro imagens nativas inspecionadas e mostradas antes de publicar: visual próprio, rascunho herdado, escuro/estreito/fonte ampliada e área reduzida por insets de teclado simulados. A última não é captura de um teclado/LockStar real. Salvar/Cancelar permanecem visíveis. O editor externo continua validando a instância diretamente.

Bug sweep sem novo defeito confirmado no escopo. Não houve mudança de agendamento/protocolo nesta entrega; testes de orçamento periódico e leituras por lote continuam aprovados. Nenhuma alegação de ganho físico de bateria. `adb devices -l` não encontrou aparelhos. Spotify/LockStar/WBR e CPU/energia continuam nas pendências do roteiro físico. O menu Editar do LockStar e a restauração de gestos Samsung seguem dependentes do sistema. Revisão do próprio agente, sem avaliação independente.
