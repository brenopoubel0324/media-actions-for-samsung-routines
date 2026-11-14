# CI eficiente — revisão pós-implementação do combinado

**Consulta:** 14/11/2026. **Worktree:** `/workspace/media-actions-review`. **HEAD de base:** `b610b9a5b7ee4755b6d7493d00e22902a8cb85c0`. **Escopo observado:** diff não commitado contra HEAD, incluindo arquivos novos não rastreados. Esta revisão não é validação Android do produto.

## Veredito

**Revisão inicial: APROVADO no recorte da orquestração.** A execução completa posterior revelou o aviso Maven descrito na atualização ao final deste relatório. A correção de determinismo foi aplicada, mas sua validação completa permanece pendente. O gate completo e o check `android` permanecem; não houve mudança no workflow, seletor ou runner.

## Leitura e validações locais

Leitura integral de `.github/workflows/widget-save-ci.yml`, `scripts/ci/plan.py`, `scripts/ci/run.py`, `scripts/ci/tests/test_ci.py`, `docs/CI.md` e `docs/STABLE_RELEASE.md`, além do plano do combinado e da skill `ci-eficiente`.

- A classificação inclui união de `git diff --name-only --no-renames -z HEAD --` e `git ls-files --others --exclude-standard -z`: 41 caminhos na captura. Há mudanças em manifest, backup, listener/serviço, observador e XML de provider.
- `classify(paths, pull_request, refs/pull/1/merge)` resultou **full**, módulos `app/shared/wear`, motivo de contrato/comportamento de maior risco.
- `classify(paths, push, refs/heads/main)` resultou **full**, módulos `app/shared/wear`, motivo integração na main.
- Saída rastreável da classificação: `/workspace/scratch/combined-ci-selection.json`. É uma captura do worktree naquele momento, não um commit ou prova de APK compilado dessa fonte.
- `python3 -m unittest discover -s scripts/ci/tests -v`: **23 testes passaram**. Cobrem seleção, intervalo completo, remoção/rename, merge/clone raso, base inválida, manual e falhas de runner/inspeção.
- Parsing YAML e assertivas do nome, job, eventos, permissões, branches, defaults manuais e timeout: **passaram**.
- `git diff --check`: **passou** na captura da revisão.
- Nenhum Gradle, emulador, assinatura, upload, dispatch, rerun ou publicação foi executado por esta revisão.

## Cadeia completa preservada

```text
:app:assembleDebug :app:assemblePreview :wear:assembleDebug :app:assembleRelease :app:testReleaseUnitTest testDebugUnitTest :app:lintRelease lintDebug :app:testPreviewUnitTest --tests '*VariantIsolationTest*'
```

O filtro `--tests` fica somente no último task, Preview. Debug e Release não recebem esse filtro. `testDebugUnitTest` e `lintDebug` conservam a seleção dos três módulos; não há `clean` ou nova execução redundante introduzida. O runner propaga falhas e recusa fonte divergente/plano adulterado/diff inválido.

Após o completo, o workflow chama `run.py inspect`: `aapt` compara package, versão, flags de depuração/testOnly e permissões dos candidatos Release/Preview/Wear. Não atesta a assinatura oficial. A entrega continua sujeita ao vínculo fonte/evidências/APK e certificado histórico de `docs/STABLE_RELEASE.md`.

## Estado remoto observado

Consulta somente de três runs recentes e dos jobs dos dois mais recentes:

| Run / fonte | Resultado observado | Alcance da prova |
| --- | --- | --- |
| [43599356263](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/43599356263), `b610b9a`, 14/11/2026 | `success`, job `android` [132460574977](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/43599356263/job/132460574977); seleção/contratos/formatação executados | Mudança documental: Java/SDK/Gradle, validação Android, inspeção e candidatos ficaram `skipped` |
| [43525731311](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/43525731311), `0970d86`, 13/11/2026 | `success`, job `android` [132230878941](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/43525731311/job/132230878941) | Também documental, com a cadeia Android corretamente dispensada |
| [43496669830](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/43496669830), `962bfa8`, 13/11/2026 | `failure` na listagem; bloqueio de faturamento documentado no histórico anterior | Não é passe nem evidência de defeito do widget atual |

**Atualização do diagnóstico de infraestrutura:** a afirmação de que todo run continua bloqueado por faturamento está desatualizada. Os dois últimos jobs documentais chegaram a executar. Isso não comprova a situação financeira da conta nem a execução de um completo Android remoto: ambos permanecem fora do que esta amostra verificou. Nenhum novo completo foi disparado para diagnóstico.

O check observado chama-se `android`, como no workflow. A consulta read-only de proteção de `main` voltou **HTTP 403 — Resource not accessible by integration**; portanto os requisitos efetivamente salvos de proteção não foram confirmados. Conferência pelo titular: [Branches](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/settings/branches) e [Rules](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/settings/rules). Não alteramos proteção ou contornamos checks.

## Segurança, eficiência e entrega pendente

- Permissão do workflow `contents: read`, checkout sem credencial persistida, ausência de `pull_request_target`/assinatura/secrets de release preservadas.
- Cache Gradle permanece read-only em PRs e refs fora da main; SDK só é provisionado se ausente. Execuções obsoletas automáticas podem ser canceladas, com execução manual em grupo distinto.
- Relatórios em falha e candidatos apenas quando explicitamente solicitados continuam com retenção de sete dias. Nenhum artefato foi apagado.
- Não houve redução adicional de tarefas neste diff: a implementação atual continua **full**. Durações de etapas remotas, custo, economia de franquia e bateria não foram medidos nem inferidos.
- O agente principal está coordenando testes focados locais; ainda deverá executar e registrar o completo na fonte final e inspecionar seus APKs antes da entrega. Um passe documental remoto não substitui essa obrigação.
- Quando houver publicação de nova versão, atualizar documentação de canal/backup vigente e manifest/certificado/hashes do artefato exato. Não marcar uma fonte em edição como já validada, assinada ou publicada.

## Atualização após o primeiro completo remoto — 14/11/2026

**Achado médio, build/lint:** o [run 43602148538](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/43602148538), fonte `a5dac3dd2f2c150bb3269c310d690f8bf5b4bc79`, efetivamente executou a cadeia Android e falhou em `:app:lintRelease`. O único erro de lint informado foi `NewerVersionAvailable`: Robolectric 4.18 anunciado no Maven para o pin `testImplementation("org.robolectric:robolectric:4.17")`. O log explica que esse detector consulta o repositório central. Não é o bloqueio histórico de faturamento. Log inspecionado: `/workspace/scratch/combined-remote-failure.log`.

O completo local anterior registrou 1.467 testes aprovados e não encontrou esse aviso no ambiente/cache usado. Esse resultado não substitui a falha remota nem valida automaticamente a fonte corrigida. O achado indica catálogo de atualização variável; não demonstrou necessidade funcional de Robolectric 4.18. Robolectric é dependência de testes, e os cenários API 33/36 existentes foram executados com 4.17; a mudança do widget não requer API exclusiva de 4.18.

**Correção aplicada, ainda sem novo passe:** em `app/build.gradle.kts`, `shared/build.gradle.kts` e `wear/build.gradle.kts`, desabilitar apenas `NewerVersionAvailable`, com comentário sobre versões fixadas e consulta variável à rede. Os pins não mudaram. `GradleDependency`, `warningsAsErrors`, `checkReleaseBuilds` do celular e os demais checks de segurança/API/correção foram preservados; não foi criado baseline global ou desligado lint. Política documentada em `docs/CI.md`.

**Validação pendente:** commit da fonte corrigida pelo agente principal, repetição da cadeia completa local/remota pertinente e inspeção de APK. Este ajuste não foi compilado por esta revisão; nenhum novo run, rerun, assinatura ou publicação foi iniciado aqui. Não declarar o run 43602148538 aprovado. A etapa de entrega continua pendente até as evidências da nova fonte serem registradas.

Atualização do agente principal: completo local canônico passou novamente após a correção (1min46s, 39 tarefas executadas e 267 UP-TO-DATE); candidatos reinspecionados e payload oficial inalterado. A confirmação remota será registrada no Release.
