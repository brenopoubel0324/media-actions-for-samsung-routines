# Estado verificado e riscos residuais — Media Actions, editor 0.23

## Escopo e veredito

**Veredito: APROVADO tecnicamente apenas para a base pré-Preview; release estável: NÃO APROVADA.**

A revisão original (`docs/reviews/widget-save-diagnostics-0.23.md`) registrou as descobertas de uma etapa anterior e não contém o resultado atualizado do CI. Este documento distingue o que foi testado do que permanece pendente.

## Evidência automatizada

- O workflow `Widget save regression checks` na execução **41263608731** (SHA `85ca1b9d333fc8cb1d53cd87c0e2b52b9122dee7`) concluiu **success**.
- Nessa execução, `:app:assembleDebug`, `:wear:assembleDebug`, `testDebugUnitTest`, `lintDebug` e `git diff --check` foram aprovados. Nenhum erro de compilação, teste ou lint no resultado final.
- Os testes de concorrência, armazenamento lógico, reedição e falha de atualização existentes no snapshot desse SHA passaram.
- **Mudanças posteriores ao SHA aprovado** acrescentam injeção de falha/retry no editor e build `preview` isolado. Elas só podem ser consideradas aprovadas quando a execução do GitHub Actions vinculada ao HEAD correspondente ficar verde. Não extrapolar o resultado da execução anterior.

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
