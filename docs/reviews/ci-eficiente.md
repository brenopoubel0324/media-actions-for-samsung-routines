# Revisão do CI proporcional

Base: `98fe7575e314c2e8666b0d59138a6484fd98fced`, versão do app 0.34. Pedido: aplicar breno-ci-eficiente neste repositório. Workflow anterior lido inteiro, contratos de release e plano de recuperação consultados. Nenhum AGENTS.md encontrado no worktree ou ancestrais. Sem mudanças de produto, assinatura ou protocolo Wear.

Diagnóstico remoto: cinco runs recentes lidos, último job com 3min57s e passos de 191s (build/test/lint) e 29s (Preview), artefatos Preview 1.957.984 bytes/relatórios 93.825 bytes retidos 90 dias. Erro HTTP 403 na consulta de proteção da main. Não se alterou billing/plano/orçamento, visibilidade do repo, checks exigidos, assinatura, publicação ou artefatos históricos. Mantidos arquivo/nome do workflow e job/check `android`.

## Rodada 1 — achados antes da correção

- **Médio — `plan.documentation`:** os contratos atuais `docs/CI.md` e `docs/STABLE_RELEASE.md` podiam dispensar o Android, apesar de definirem a cadeia de validação/release. Excluir esses dois caminhos da whitelist e cobrir em teste. Planos e relatórios históricos não são inputs do app e continuam documentais, evitando reconstrução a cada atualização do relatório de revisão.
- **Baixo — inspeção Release:** garantir cobertura explícita de sufixo experimental, versão zero e `testOnly` no manifest binário. A ausência de flag em badging não basta para `testOnly`. A leitura xmltree já foi adicionada durante a implementação; ampliar as fixtures de rejeição e conferir com o APK existente, sem recompilar produto.

## Rodada 2 — APROVADO localmente

O contrato documental foi restringido aos dois documentos operacionais, com fixtures de aceitação para revisões/planos históricos. Rejeições de versão zero, sufixo experimental e flags binárias debug/testOnly cobertas. Sem novos achados bloqueantes/altos/médios/baixos. Um médio e um baixo corrigidos; nenhum bloqueante/alto restante. Permissões mínimas, `android` estável, sem secrets/signing, caches readonly em PRs e nenhuma alteração em app/Wear/shared.

Passaram 23 testes Python, incluindo CLI e repositórios Git reais, propagação de erro, escopos, shallow fetch e inspeção de candidatos. YAML validado com BaseLoader, actionlint 1.7.7 (checksum oficial verificado; sem shellcheck/pyflakes externos) e git diff --check. O parser de metadados foi conferido com o APK 0.34 existente; não houve compilação, emulador ou renderização Android local. A seleção de docs não chama Gradle. Action SHAs v4 conferidos por git ls-remote nos repositórios oficiais.

Validação remota pendente de ativação: conferir um único run completo do push desta configuração na main; não disparar suíte como diagnóstico e não contar bloqueio como passe. Proteções/billing não foram alterados. O custo total não foi medido e o completo agora inclui cobertura que não existia; nenhuma promessa de redução de fatura. Gate de APK oficial permanece externo, com fonte/evidências/assinatura obrigatórias; inspeção do candidato CI não atesta binário em downloads.

Riscos residuais: não foi possível ler proteção de branch (HTTP 403), sem confirmação de checks exigidos. A aprovação local desta orquestração não é execução da suíte Android nem teste físico. A primeira execução remota precisa confirmar tasks, isolamento Preview, ferramentas do runner e parsing de candidatos.
