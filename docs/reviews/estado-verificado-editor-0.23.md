# Estado verificado e riscos residuais — Media Actions, editor 0.23

## Escopo e veredito

**Veredito (01/11/2026): CI do Preview APROVADO no SHA de código `8ad8c160b188ac82886e9c3c9ee988c93ba172f2`; validação física e release estável: NÃO APROVADAS.**

A revisão original (`docs/reviews/widget-save-diagnostics-0.23.md`) registrou as descobertas de uma etapa anterior e não contém o resultado atualizado do CI. Este documento distingue o que foi testado do que permanece pendente.

## Evidência automatizada histórica

As execuções abaixo antecedem a validação mais recente registrada acima.

- O workflow `Widget save regression checks` na execução **41263608731** (SHA `85ca1b9d333fc8cb1d53cd87c0e2b52b9122dee7`) concluiu **success**.
- Nessa execução, `:app:assembleDebug`, `:wear:assembleDebug`, `testDebugUnitTest`, `lintDebug` e `git diff --check` foram aprovados. Nenhum erro de compilação, teste ou lint no resultado final.
- Os testes de concorrência, armazenamento lógico, reedição e falha de atualização existentes no snapshot desse SHA passaram.
- **Mudanças posteriores ao SHA aprovado** acrescentam injeção de falha/retry no editor e build `preview` isolado. Elas só podem ser consideradas aprovadas quando a execução do GitHub Actions vinculada ao HEAD correspondente ficar verde. Não extrapolar o resultado da execução anterior.

## Validação verificada do HEAD de código (01/11/2026)

- PR #1, branch `fix/widget-save-diagnostics-0.23`, SHA verificado **`8ad8c160b188ac82886e9c3c9ee988c93ba172f2`**; execução [Widget save regression checks #32](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/41266780048) (**success**, concluída em 01/11/2026 02:55 UTC).
- Job `android` e todas as etapas aprovados: `git diff --check origin/main...HEAD`, `:app:assembleDebug`, `:app:assemblePreview`, `:wear:assembleDebug`, `testDebugUnitTest`, `lintDebug`, validação de package pelo `aapt` e upload de artefatos. Gradle: **BUILD SUCCESSFUL in 4m 53s**.
- `aapt dump badging` no APK produzido confirmou **`package: name='dev.local.mediaactions.preview'`**, `versionCode='22'`, `versionName='0.22-experimental-preview'`. A verificação comprova isolamento do identificador do pacote, não o comportamento do host no dispositivo.
- Artefato [`media-actions-s25-isolated-preview`](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/41266780048/artifacts/13067726997), ID **13067726997**, 1.958.004 bytes (arquivo ZIP), SHA-256 do **ZIP**: `610572ded48c6da41bd314fea345e664c58429d73f5eb584ffd538ae58219df1`. **Não confundir o digest do ZIP com o SHA-256 do APK interno**, que não foi calculado nesta verificação.
- Relatórios automatizados: [`widget-save-test-reports`](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/41266780048/artifacts/13068260894), ID **13068260894**.
- A confirmação acima é vinculada ao SHA **de código** indicado. Um commit posterior de documentação exige CI próprio antes de ser chamado de HEAD verde. Não foi feita instalação no Galaxy S25 nem teste de LockStar, migração, persistência física, assinatura original ou bateria. **PR permanece draft, `main` preservada, nenhuma release publicada.**

## Principais correções

1. Falha no refresh deixa de ser interpretada como erro de entrada. O host tem status `REQUESTED`, `FAILED` ou `MISSING_WIDGET`.
2. Editor mantém rascunho e oferece diagnóstico de falha de leitura lógica, localização e atualização.
3. Modificações concorrentes de aparência/local são detectadas em vez de sobrescritas em silêncio.
4. Diagnóstico copiável inclui ID, `hasOverride`, aparência efetiva, dimensões e último estágio do salvamento, sem conteúdo da música.
5. Teste novo injeta falha de refresh no fluxo real do editor, verifica que o rascunho permanece e que a nova tentativa pode funcionar.
6. Um build `preview` instala ao lado do app original, com application ID `dev.local.mediaactions.preview`. O CI verifica esse ID antes de disponibilizar o APK.

## Limitações bloqueantes para release

- **Persistência em disco**: `SharedPreferences.apply()` altera a memória imediatamente, mas não fornece confirmação síncrona de gravação durável. Os read-backs atuais verificam estado lógico, não I/O físico. A falha original relatada pelo usuário ainda não foi reproduzida em um telefone.
- **Não atomicidade de stores**: o estilo e a identificação do local são preferências separadas. O tratamento informa falhas parciais; não há transação atômica entre ambas.
- **Host Samsung**: `AppWidgetManager.updateAppWidget()` não confirma que o LockStar/One UI desenhou o novo widget. O comportamento específico da firmware permanece NÃO TESTADO.
- **Instalação existente**: build Preview paralelo NÃO testa upgrade do pacote original. Não trocar a assinatura ou recomendar desinstalação do app de produção.
- **Snapshot Codex anterior**: eventuais mudanças locais nunca enviadas ao GitHub não podem ser recuperadas por essa integração.
- **Desempenho/energia**: nenhuma economia de bateria pode ser atribuída a essas correções sem medições físicas.

## Protocolo para validação de hardware

Consultar `docs/testes/validacao-editor-s25-0.23.md`: instalar somente o APK Preview obtido de um workflow verde, criar dois widgets de teste, salvar/reabrir, conferir visual antes/depois, reiniciar o aparelho, testar herança e, se possível, comparar home/LockStar. O aplicativo original deve permanecer intacto.

## Política de merge

Não mesclar o PR #1 na main nem publicar release estável antes de: CI verde no HEAD, assinatura/versionCode de atualização compatíveis, e evidências de que o S25 preserva as configurações e atualiza corretamente o widget (ou de que há uma limitação documentada do host).
