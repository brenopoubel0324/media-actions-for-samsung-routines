# Self-audit do PR #1 — 05/11/2026

Base main `1f09e3a`; candidato `fdf673a`; plano `docs/planos/widget-save-recovery-0.23.md`. Skill self-audit-loop; sem subagentes. Escopo: 18 arquivos alterados e callers de armazenamento, listener, atalhos e CI.

## Rodada 1 — achados antes das correções

- **Alto — AndroidSessionGateway.listenerComponent:** o sufixo `.preview` muda applicationId, mas não o pacote das classes. O nome calculado aponta para serviço inexistente, impedindo reconhecer o acesso concedido ao listener real. Usar package de instalação e classe estável; testar ambas as variantes.
- **Alto — shortcuts.xml/Preview:** os três intents continuam com targetPackage do original. Um atalho da Preview pode invocar o app original. Sobrepor destinos e testar resolução dos três componentes.
- **Médio — applyChangesLocked/retry:** após gravar aparência e falhar refresh, o baseline de conflito permanece anterior. Alterar novamente o rascunho faz a própria escrita parecer externa. Atualizar baseline após read-back lógico sem descartar rascunho/intenção; testar recriação e conflito externo real.

Persistência física, atomicidade entre stores e pintura Samsung continuam pendentes conforme plano; sem causa presumida nem migração de armazenamento. Main e release preservadas.

Os três problemas foram reproduzidos antes da correção em API 33/36: quatro falhas de isolamento Preview e duas falhas de retry/recriação; o cenário de conflito externo permaneceu aprovado.

## Rodada 2 — correções e validação

Serviço usa a classe declarada com applicationId da instalação; XML de atalhos sobreposto somente na Preview. CI passa a executar o teste de isolamento na variante real, além da suíte Debug. Baselines de aparência/local avançam após seus read-backs lógicos, mesmo quando o estágio seguinte falha; rascunho e intenção permanecem. Recriação guarda esses baselines nas chaves existentes. Nenhuma confirmação de disco ou atomicidade é inferida.

### Resultado da rodada 2

**APROVADO para continuidade do Preview; release original permanece pendente de validação física.** Nenhum alto/bloqueante novo aberto neste diff. Os dois altos e o médio desta rodada foram corrigidos e testados.

Validação local: `:app:assembleDebug :app:assemblePreview :wear:assembleDebug testDebugUnitTest lintDebug` passou; 479 testes Debug (app 442, Wear 15, shared 22), zero falhas/erros/pulos. Em seguida, `:app:testPreviewUnitTest` com VariantIsolationTest e captura SettingsUxVisualTest passou: quatro casos de isolamento API 33/36 e uma captura nativa. A captura com nome Media Actions Preview foi mostrada antes de disponibilizar o novo candidato. `git diff --check` passou.

O APK Preview compilado foi inspecionado com aapt: package `dev.local.mediaactions.preview`, três atalhos apontando exclusivamente a esse package, classes no namespace original. SHA-256 **do APK local**: `0aca91cb259b362416c97569c1e345831718c32320e950e44aeb19998a585c50`. Não é hash do ZIP do CI nem comprovação de upgrade do original. Artefatos CI usam assinatura de seu ambiente.

Bug sweep incluiu broadcasts com setPackage, PendingIntents, listener declarado, config externa do widget, seleção de sessão e chamadas de diagnóstico. Nenhum polling, wake lock ou mudança de cadência/transporte de comandos foi introduzido. O hash de aparência do diagnóstico é auxiliar em memória; comparar campos reais no teste após reinício.

Continuam pendentes: reprodução da falha original S1/S2/S3 no S25; durabilidade real e recuperação entre stores; desenho pelo LockStar; atualização do original com certificado/versionCode compatíveis; energia física. Preview tem outro package e não valida a ponte com o Wear original. Os limites do plano não foram promovidos a testes concluídos. Revisão não independente, em duas rodadas.
