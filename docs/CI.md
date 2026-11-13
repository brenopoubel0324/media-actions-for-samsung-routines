# Validação contínua proporcional

O workflow `.github/workflows/widget-save-ci.yml` mantém o nome **Widget save regression checks** e o job/check **android**. A estratégia é escolhida dentro desse check; não há filtro de paths que deixe um check exigido pendente. Um resultado documental não equivale a build aprovado do app.

| Situação | Validação |
| --- | --- |
| PR com código/recursos comuns do celular | `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` |
| PR comum do relógio | Os mesmos alvos Debug/lint de `:wear` |
| Mudança em `shared` | Debug/testes/lint dos três módulos; shared não gera APK |
| Mais de um módulo | União dos escopos, em uma chamada Gradle |
| Build/dependências/toolchain, CI, manifest/XML de configuração, instrumentação, nativo, contratos/serviços/sessões/permissões/persistência/backup | Completo |
| Push de produto na `main` | Completo, inclusive para mudanças ordinárias que fizeram apenas Debug no PR |
| README/relatório/créditos/contribuição na raiz e `.md/.png/.jpg/.jpeg/.svg` em `docs/`, sem outros paths; exceto contratos operacionais abaixo | Só contratos do CI e formatação; sem Java/SDK/Gradle |
| Paths desconhecidos, evidências e `downloads/`, inclusive APKs | Completo; não presumir binário já validado pelo nome da pasta |
| Diff indisponível ou inválido | Plano conservador completo, mas formatação/runner falham explicitamente antes de gastar no Android. Nunca docs-only ou sucesso silencioso |

`docs/CI.md` e `docs/STABLE_RELEASE.md` são contratos operacionais e exigem completo, mesmo sendo Markdown.

A lista de risco é conservadora e fica em `scripts/ci/plan.py`. Arquivos Markdown/JSON em assets/recursos do aplicativo são produto. Não se removem testes Android por serem lentos: todo `testDebugUnitTest` do módulo selecionado continua rodando, incluindo APIs 33/36 e renderização nativa quando presentes. Autorização, ações, persistência e recuperação permanecem cobertas; paths sensíveis promovem ao completo.

## Completo e entrega

O completo faz `:app:assembleDebug :app:assemblePreview :wear:assembleDebug :app:assembleRelease :app:testReleaseUnitTest testDebugUnitTest :app:lintRelease lintDebug :app:testPreviewUnitTest --tests '*VariantIsolationTest*'`, em uma chamada Gradle. O filtro pertence apenas ao último task, de Preview; Debug/Release não são filtrados. Não usa `clean`. Assim valida também produção, ausente no workflow anterior. `assembleRelease` conserva a configuração de otimização do projeto; não liga R8 por conta própria.

Depois, `aapt` inspeciona candidatos Release/Preview/Wear: package, versão, flags de debug/testOnly e conjunto de permissões. Phone Release deve ser o original não depurável; Preview usa `.preview`; Wear usa o mesmo applicationId do original conforme Data Layer. Esses APKs não são a distribuição oficial. CI não recebe a chave histórica, não assina o Release original e não publica releases.

Antes de entregar, exigir um completo aprovado para a fonte relevante na `main`, ou executar manualmente o completo na ref necessária. Um completo manual em branch testa o head dessa branch, não o merge sintético do PR. A distribuição oficial continua sujeita ao [gate de assinatura e integridade](STABLE_RELEASE.md), incluindo vínculo explícito do binário com sua fonte/evidências, certificado histórico, package/versão e hashes. **O completo de CI não atesta por si só um APK colocado em downloads.** Não há atalho para distribuição já validada sem prova dessa associação. Não tratar um run cancelado ou bloqueado como passe.

## Eventos e comparação

- PRs para `main` testam o merge preparado pelo GitHub. O seletor usa a base comum dos SHAs base/head do evento para selecionar a mudança, sem confundir isso com o SHA de merge efetivamente compilado.
- Push roda somente na `main`, comparando `before` e `after` de todo o intervalo, não apenas o último commit. Branch de trabalho tem cobertura pelo PR, sem duplicar push/PR.
- Tags não disparam outro build idêntico ao da `main`. Para release fora dessa integração, usar completo manual na ref antes da publicação.
- Checkout usa profundidade 2. Caso necessário, o seletor busca apenas os SHAs validados e até 128 commits de profundidade; se a base continuar inacessível, falha explicitamente. Exclusões e os dois lados de renames são considerados, sem truncar a lista de arquivos.
- Execuções automáticas obsoletas da mesma ref/PR são canceladas. Execução manual tem propósito separado e não é cancelada por pushes ou outro manual. Não se adicionou `pull_request_target`, assinatura, secrets ou escrita no repositório.

## Cache, relatórios e candidatos

Reutiliza `gradle/actions/setup-gradle`; não acrescenta cache amplo do SDK. Instala SDK 36/build-tools 36 somente se ausentes. Falhas de licença/instalação não são convertidas em sucesso. Cache é somente leitura em PRs e refs diferentes da `main`; não há credenciais Git persistidas para o código testado. Actions v4 existentes foram fixadas nos SHAs verificados nas tags oficiais, sem mudar de major.

Relatórios/XML de testes, lint e prévias ficam disponíveis em falha, com retenção de 7 dias. Candidatos só são enviados quando solicitados em **Actions → Widget save regression checks → Run workflow → full → upload_candidates=true**, também por 7 dias. A combinação daily com upload_candidates=true é recusada antes do build. O padrão é não enviar APK; não apagar artefatos históricos. O nome inclui run/attempt para evitar colisões. Os candidatos não devem substituir a instalação original assinada.

A execução manual oferece **full** por padrão e **daily** para diagnóstico dos três módulos. Resumo de cada run informa escopo, motivo, tarefas previstas, SHA efetivamente testado e resultado do job. Falhas do seletor, testes, timeout ou inspeção não viram aprovação.

## Evidência e limites

Auditoria inicial: último run inspecionado [42024166990](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/actions/runs/42024166990), em 5/11/2026, job `android` aprovado em 3min57s. Build/testes/lint consumiram 3min11s; a verificação Preview levou 29s. Uploads: Preview 1.957.984 bytes e relatórios 93.825 bytes, com retenção de 90 dias. Outros quatro runs recentes mostraram três sucessos e um cancelamento. Essa amostra antiga não mede o desempenho do app atual.

Redução demonstrável por plano: PR só do celular passa de compilar três APKs e validar Debug/lint dos três módulos mais Preview para um APK Debug e testes/lint do celular. Documentação permitida executa zero tarefas Android. Artefatos deixam de ser enviados sempre e usam 7 dias em vez de 90. O completo acrescenta a cobertura Release e pode ser mais caro; a cobertura da `main` também é nova. Não se promete redução do custo total. Minutos/custos antes/depois ainda não foram medidos; runner curto documental também pode consumir franquia. Timeout/concorrência não são orçamento financeiro. Nenhum orçamento, plano ou cobrança foi alterado.

A integração retornou **HTTP 403** ao consultar proteção da branch. O workflow/check foi preservado, mas não foi possível confirmar requisitos salvos. O titular pode conferir em [Settings → Branches](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/settings/branches) e [Rules](https://github.com/brenopoubel0324/media-actions-for-samsung-routines/settings/rules). Não é necessário fornecer token ou senha.

## Verificação local da orquestração

`python3 -m unittest discover -s scripts/ci/tests -v`, validador YAML e `actionlint` quando disponível, além de `git diff --check`. Os testes usam fixtures Git reais para push multicommit, rename/exclusão, PR/merge, shallow fetch, manual/CLI e base inválida. Simulam falha de Gradle e inspeção de APK para garantir propagação de erro. Mudança só de CI não exige reconstruir o app localmente nem iniciar emuladores. A ativação desta configuração deve ser conferida em um único run completo do push na `main`; um bloqueio remoto não conta como sucesso.
